package com.imyash.pesuwifi.service

import android.app.AlarmManager
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
import android.os.SystemClock
import android.util.Log
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
    private var lastCheckTimestamp = 0L

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "WifiKeepaliveService onCreate")
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
            if (pm != null && (wakeLock == null || !wakeLock!!.isHeld)) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PesuWifi:KeepaliveWakeLock").apply {
                    setReferenceCounted(false)
                    acquire() // Continuous wakelock held while service runs
                }
                Log.i(TAG, "Continuous PowerManager WakeLock acquired")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WakeLock: ${e.message}", e)
        }

        try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wm != null && (wifiLock == null || !wifiLock!!.isHeld)) {
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    @Suppress("DEPRECATION")
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                }
                wifiLock = wm.createWifiLock(mode, "PesuWifi:KeepaliveWifiLock").apply {
                    setReferenceCounted(false)
                    acquire() // Continuous wifi lock held while service runs
                }
                Log.i(TAG, "Continuous WifiLock acquired (mode: $mode)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WifiLock: ${e.message}", e)
        }
    }

    private fun releaseLocks() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.i(TAG, "WakeLock released")
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing WakeLock: ${e.message}")
        }
        try {
            wifiLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.i(TAG, "WifiLock released")
                }
            }
            wifiLock = null
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing WifiLock: ${e.message}")
        }
    }

    private fun scheduleNextHeartbeat() {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(this, WifiKeepaliveService::class.java).apply {
                action = ACTION_HEARTBEAT
            }
            val pendingIntent = PendingIntent.getService(
                this,
                REQUEST_HEARTBEAT,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val triggerAtMillis = SystemClock.elapsedRealtime() + KEEPALIVE_INTERVAL_MS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
            Log.d(TAG, "Scheduled next heartbeat alarm in ${KEEPALIVE_INTERVAL_MS / 1000}s")
        } catch (e: Exception) {
            Log.w(TAG, "Could not schedule exact heartbeat alarm: ${e.message}")
        }
    }

    private fun cancelHeartbeat() {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(this, WifiKeepaliveService::class.java).apply {
                action = ACTION_HEARTBEAT
            }
            val pendingIntent = PendingIntent.getService(
                this,
                REQUEST_HEARTBEAT,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
                Log.d(TAG, "Heartbeat alarm cancelled")
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        Log.d(TAG, "onStartCommand: action = $action")

        when (action) {
            ACTION_STOP -> {
                Log.i(TAG, "ACTION_STOP received, stopping service")
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_LOGIN -> {
                serviceScope.launch {
                    val res = portalRepository.login()
                    Log.i(TAG, "ACTION_LOGIN result: $res")
                    updateForegroundNotification()
                }
            }
            ACTION_LOGOUT -> {
                serviceScope.launch {
                    val res = portalRepository.logout()
                    Log.i(TAG, "ACTION_LOGOUT result: $res")
                    updateForegroundNotification()
                }
            }
            ACTION_HEARTBEAT -> {
                Log.d(TAG, "ACTION_HEARTBEAT received (watchdog tick)")
                acquireLocks()
                scheduleNextHeartbeat()
                serviceScope.launch {
                    performKeepaliveCheck()
                }
            }
            ACTION_START -> {
                startInForeground()
                acquireLocks()
                scheduleNextHeartbeat()
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

    private suspend fun performKeepaliveCheck(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastCheckTimestamp < 35_000L) {
            Log.d(TAG, "Skipping check, last check was ${(now - lastCheckTimestamp) / 1000}s ago")
            return
        }
        lastCheckTimestamp = now

        try {
            val status = portalRepository.refreshStatus()
            updateForegroundNotification()

            if (status.isWifiConnected && status.isPortalOnline) {
                if (status.isLoggedIn) {
                    consecutiveFailureCount = 0
                    Log.i(TAG, "Keepalive check OK: Logged in as ${status.activeUsername} (latency ${status.latencyMs}ms)")
                } else {
                    consecutiveFailureCount++
                    Log.w(TAG, "Session inactive (failure count $consecutiveFailureCount/2)")
                    // Require 2 consecutive failed checks before auto-re-login
                    // to avoid tearing down active sessions due to transient Wi-Fi packet drops
                    if (consecutiveFailureCount >= 2) {
                        val activeUser = accountRepository.getActiveUser()
                        if (activeUser != null) {
                            Log.i(TAG, "Initiating re-authentication for $activeUser...")
                            val result = portalRepository.login(activeUser)
                            if (result.isSuccess) {
                                Log.i(TAG, "Re-authentication succeeded for $activeUser")
                                consecutiveFailureCount = 0
                            } else {
                                Log.e(TAG, "Re-authentication failed: ${result.exceptionOrNull()?.message}")
                            }
                            updateForegroundNotification()
                        } else {
                            Log.w(TAG, "No active user configured for auto-re-login")
                        }
                    }
                }
            } else {
                consecutiveFailureCount = 0
                if (!status.isWifiConnected) {
                    Log.d(TAG, "Wi-Fi not connected")
                } else {
                    Log.d(TAG, "Portal gateway unreachable (${status.latencyMs}ms)")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Keepalive network check failed: ${e.message}", e)
        }
    }

    private fun startKeepaliveLoop() {
        loopJob?.cancel()
        loopJob = serviceScope.launch {
            Log.d(TAG, "Keepalive coroutine loop started")
            while (isActive) {
                performKeepaliveCheck()
                scheduleNextHeartbeat()
                delay(KEEPALIVE_INTERVAL_MS)
            }
        }
    }

    private fun registerNetworkMonitor() {
        try {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d(TAG, "NetworkCallback: Wi-Fi onAvailable ($network)")
                    // Debounce rapid AP roaming transitions across campus
                    reconnectJob?.cancel()
                    reconnectJob = serviceScope.launch {
                        delay(1500L) // Wait for DHCP assignment and Wi-Fi interface stabilization
                        performKeepaliveCheck(force = true)

                        val status = portalRepository.statusFlow.value
                        if (status.isWifiConnected && status.isPortalOnline && !status.isLoggedIn) {
                            // On fresh network connection, immediately authenticate
                            val activeUser = accountRepository.getActiveUser()
                            if (activeUser != null) {
                                Log.i(TAG, "Network available: auto-authenticating as $activeUser")
                                portalRepository.login(activeUser)
                                consecutiveFailureCount = 0
                                updateForegroundNotification()
                            }
                        }
                        startKeepaliveLoop()
                    }
                }

                override fun onLost(network: Network) {
                    Log.d(TAG, "NetworkCallback: Wi-Fi onLost ($network)")
                    reconnectJob?.cancel()
                    consecutiveFailureCount = 0
                    serviceScope.launch {
                        portalRepository.refreshStatus()
                        updateForegroundNotification()
                    }
                }
            }

            networkCallback = callback
            connectivityManager.registerNetworkCallback(request, callback)
            Log.d(TAG, "Registered NetworkCallback for TRANSPORT_WIFI")
        } catch (e: Exception) {
            Log.w(TAG, "Could not register NetworkCallback: ${e.message}")
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
        Log.i(TAG, "WifiKeepaliveService onDestroy")
        _isServiceRunning.value = false
        reconnectJob?.cancel()
        loopJob?.cancel()
        cancelHeartbeat()
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
        const val TAG = "PesuWifi"
        const val KEEPALIVE_INTERVAL_MS = 60_000L
        const val REQUEST_HEARTBEAT = 100

        const val ACTION_START = "com.imyash.pesuwifi.action.START"
        const val ACTION_STOP = "com.imyash.pesuwifi.action.STOP"
        const val ACTION_LOGIN = "com.imyash.pesuwifi.action.LOGIN"
        const val ACTION_LOGOUT = "com.imyash.pesuwifi.action.LOGOUT"
        const val ACTION_HEARTBEAT = "com.imyash.pesuwifi.action.HEARTBEAT"

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
