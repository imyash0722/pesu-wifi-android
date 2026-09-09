package com.imyash.pesuwifi.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.imyash.pesuwifi.MainActivity
import com.imyash.pesuwifi.PesuWifiApp
import com.imyash.pesuwifi.R
import com.imyash.pesuwifi.data.AccountRepository
import com.imyash.pesuwifi.data.PortalRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class WifiKeepaliveService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var loopJob: Job? = null
    private var reconnectJob: Job? = null

    private lateinit var portalRepository: PortalRepository
    private lateinit var accountRepository: AccountRepository
    private lateinit var connectivityManager: ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var consecutiveFailureCount = 0

    override fun onCreate() {
        super.onCreate()
        portalRepository = PortalRepository.getInstance(this)
        accountRepository = AccountRepository.getInstance(this)
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        acquireLocks()
        registerNetworkMonitor()
        _isServiceRunning.value = true
    }

    private fun acquireLocks() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (wakeLock == null && pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PesuWifi:KeepaliveWakeLock").apply {
                    setReferenceCounted(false)
                    acquire(12 * 60 * 60 * 1000L) // 12-hour max safety timeout
                }
            }
        } catch (e: Exception) {
            // Ignore power manager exceptions
        }

        try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiLock == null && wm != null) {
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    @Suppress("DEPRECATION")
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                }
                wifiLock = wm.createWifiLock(mode, "PesuWifi:KeepaliveWifiLock").apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
        } catch (e: Exception) {
            // Ignore Wi-Fi manager exceptions
        }
    }

    private fun releaseLocks() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
            wakeLock = null
        } catch (e: Exception) {
            // Ignore
        }
        try {
            wifiLock?.let { if (it.isHeld) it.release() }
            wifiLock = null
        } catch (e: Exception) {
            // Ignore
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_LOGIN -> {
                serviceScope.launch {
                    portalRepository.login()
                    updateForegroundNotification()
                }
            }
            ACTION_LOGOUT -> {
                serviceScope.launch {
                    portalRepository.logout()
                    updateForegroundNotification()
                }
            }
            ACTION_START -> {
                startInForeground()
                startKeepaliveLoop()
            }
        }

        return START_STICKY
    }

    private fun startInForeground() {
        val notification = buildNotification(
            title = "PESU WiFi Keepalive Active",
            content = "Monitoring captive portal connection...",
            isLoggedIn = false
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                PesuWifiApp.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(PesuWifiApp.NOTIFICATION_ID, notification)
        }
    }

    private fun startKeepaliveLoop() {
        loopJob?.cancel()
        loopJob = serviceScope.launch {
            while (isActive) {
                try {
                    val status = portalRepository.refreshStatus()
                    updateForegroundNotification()

                    if (status.isWifiConnected && status.isPortalOnline) {
                        if (status.isLoggedIn) {
                            consecutiveFailureCount = 0
                        } else {
                            consecutiveFailureCount++
                            // Require 2 consecutive failed checks before auto-re-login
                            // to avoid tearing down active sessions due to transient Wi-Fi packet drops
                            if (consecutiveFailureCount >= 2) {
                                val activeUser = accountRepository.getActiveUser()
                                if (activeUser != null) {
                                    val result = portalRepository.login(activeUser)
                                    if (result.isSuccess) {
                                        consecutiveFailureCount = 0
                                    }
                                    updateForegroundNotification()
                                }
                            }
                        }
                    } else {
                        consecutiveFailureCount = 0
                    }
                } catch (e: Exception) {
                    // Ignore background network blips
                }

                // Sleep for 60 seconds (standard keepalive cadence)
                delay(60_000L)
            }
        }
    }

    private fun registerNetworkMonitor() {
        try {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    // Debounce rapid AP roaming transitions across campus
                    reconnectJob?.cancel()
                    reconnectJob = serviceScope.launch {
                        delay(1500L) // Wait for DHCP assignment and Wi-Fi interface stabilization
                        val status = portalRepository.refreshStatus()
                        updateForegroundNotification()

                        if (status.isWifiConnected && status.isPortalOnline && !status.isLoggedIn) {
                            // On fresh network connection, immediately authenticate
                            val activeUser = accountRepository.getActiveUser()
                            if (activeUser != null) {
                                portalRepository.login(activeUser)
                                consecutiveFailureCount = 0
                                updateForegroundNotification()
                            }
                        }
                        startKeepaliveLoop()
                    }
                }

                override fun onLost(network: Network) {
                    reconnectJob?.cancel()
                    consecutiveFailureCount = 0
                    serviceScope.launch {
                        portalRepository.refreshStatus()
                        updateForegroundNotification()
                    }
                }
            }

            connectivityManager.registerNetworkCallback(request, networkCallback!)
        } catch (e: Exception) {
            // Fallback: timer loop continues regardless
        }
    }

    private suspend fun updateForegroundNotification() {
        val status = portalRepository.statusFlow.value
        val title = when {
            !status.isWifiConnected -> "PESU WiFi: Disconnected"
            !status.isPortalOnline -> "PESU WiFi: Portal Unreachable"
            status.isLoggedIn -> "PESU WiFi: Active"
            else -> "PESU WiFi: Logged Out"
        }

        val content = when {
            !status.isWifiConnected -> "Waiting for Wi-Fi connection..."
            !status.isPortalOnline -> "Gateway probe timed out (${status.latencyMs ?: 0}ms)"
            status.isLoggedIn -> "Logged in as ${status.activeUsername ?: "active"}"
            else -> "Session expired. Tap to login."
        }

        val notification = buildNotification(title, content, status.isLoggedIn)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(PesuWifiApp.NOTIFICATION_ID, notification)
    }

    private fun buildNotification(title: String, content: String, isLoggedIn: Boolean): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopServiceIntent = Intent(this, WifiKeepaliveService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopServiceIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val actionIntent = Intent(this, WifiKeepaliveService::class.java).apply {
            action = if (isLoggedIn) ACTION_LOGOUT else ACTION_LOGIN
        }
        val actionPendingIntent = PendingIntent.getService(
            this,
            2,
            actionIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val actionTitle = if (isLoggedIn) "Logout" else "Login"
        val actionIcon = if (isLoggedIn) R.drawable.ic_logout else R.drawable.ic_login

        return NotificationCompat.Builder(this, PesuWifiApp.CHANNEL_KEEPALIVE_ID)
            .setSmallIcon(R.drawable.ic_wifi)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(actionIcon, actionTitle, actionPendingIntent)
            .addAction(R.drawable.ic_stop, "Stop", stopPendingIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        _isServiceRunning.value = false
        reconnectJob?.cancel()
        loopJob?.cancel()
        releaseLocks()
        try {
            networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
        } catch (e: Exception) {
            // Ignore
        }
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.imyash.pesuwifi.action.START"
        const val ACTION_STOP = "com.imyash.pesuwifi.action.STOP"
        const val ACTION_LOGIN = "com.imyash.pesuwifi.action.LOGIN"
        const val ACTION_LOGOUT = "com.imyash.pesuwifi.action.LOGOUT"

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, WifiKeepaliveService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, WifiKeepaliveService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
