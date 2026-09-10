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
import androidx.core.app.NotificationCompat
import com.imyash.pesuwifi.MainActivity
import com.imyash.pesuwifi.PesuWifiApp
import com.imyash.pesuwifi.R
import com.imyash.pesuwifi.data.AccountRepository
import com.imyash.pesuwifi.data.PortalRepository
import com.imyash.pesuwifi.util.AppLogger
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
        AppLogger.i(TAG, "WifiKeepaliveService onCreate")
        portalRepository = PortalRepository.getInstance(this)
        accountRepository = AccountRepository.getInstance(this)
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        registerNetworkMonitor()
        _isServiceRunning.value = true
    }

    private fun acquireLocks() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (pm != null && (wakeLock == null || !wakeLock!!.isHeld)) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PesuWifi:KeepaliveWakeLock").apply {
                    setReferenceCounted(false)
                    acquire()
                }
                AppLogger.d(TAG, "PowerManager WakeLock acquired")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to acquire WakeLock: ${e.message}", e)
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
                    acquire()
                }
                AppLogger.d(TAG, "WifiLock acquired (mode: $mode)")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to acquire WifiLock: ${e.message}", e)
        }
    }

    private fun releaseLocks() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    AppLogger.d(TAG, "WakeLock released")
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error releasing WakeLock: ${e.message}")
        }
        try {
            wifiLock?.let {
                if (it.isHeld) {
                    it.release()
                    AppLogger.d(TAG, "WifiLock released")
                }
            }
            wifiLock = null
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error releasing WifiLock: ${e.message}")
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
            AppLogger.d(TAG, "Scheduled next heartbeat alarm in ${KEEPALIVE_INTERVAL_MS / 1000}s")
        } catch (e: Exception) {
            AppLogger.w(TAG, "Could not schedule exact heartbeat alarm: ${e.message}")
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
                AppLogger.d(TAG, "Heartbeat alarm cancelled")
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        AppLogger.d(TAG, "onStartCommand: action = $action")

        when (action) {
            ACTION_STOP -> {
                AppLogger.i(TAG, "ACTION_STOP received, stopping service")
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_LOGIN -> {
                serviceScope.launch {
                    val res = portalRepository.login()
                    AppLogger.i(TAG, "ACTION_LOGIN result: $res")
                    updateForegroundNotification()
                }
            }
            ACTION_LOGOUT -> {
                serviceScope.launch {
                    val res = portalRepository.logout()
                    AppLogger.i(TAG, "ACTION_LOGOUT result: $res")
                    updateForegroundNotification()
                }
            }
            ACTION_HEARTBEAT -> {
                AppLogger.d(TAG, "ACTION_HEARTBEAT received (watchdog tick)")
                serviceScope.launch {
                    performKeepaliveCheck()
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
            content = "Monitoring network status...",
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
            AppLogger.d(TAG, "Skipping check, last check was ${(now - lastCheckTimestamp) / 1000}s ago")
            return
        }
        lastCheckTimestamp = now

        try {
            val status = portalRepository.refreshStatus()
            updateForegroundNotification()

            if (status.isWifiConnected && status.isPesuWifi) {
                // Connected to campus Wi-Fi
                acquireLocks()
                scheduleNextHeartbeat()

                if (status.isLoggedIn) {
                    consecutiveFailureCount = 0
                    AppLogger.d(TAG, "Keepalive check OK: Logged in as ${status.activeUsername} (latency ${status.latencyMs}ms)")
                } else {
                    consecutiveFailureCount++
                    AppLogger.w(TAG, "Session inactive on PESU Wi-Fi (failure count $consecutiveFailureCount/2)")
                    if (consecutiveFailureCount >= 2) {
                        val activeUser = accountRepository.getActiveUser()
                        if (activeUser != null) {
                            AppLogger.i(TAG, "Initiating re-authentication for $activeUser...")
                            val result = portalRepository.login(activeUser)
                            if (result.isSuccess) {
                                AppLogger.i(TAG, "Re-authentication succeeded for $activeUser")
                                consecutiveFailureCount = 0
                            } else {
                                AppLogger.e(TAG, "Re-authentication failed: ${result.exceptionOrNull()?.message}")
                            }
                            updateForegroundNotification()
                        } else {
                            AppLogger.w(TAG, "No active user configured for auto-re-login")
                        }
                    }
                }
            } else {
                // Non-interference mode: either disconnected or connected to non-campus Wi-Fi (Home / Hotspot)
                consecutiveFailureCount = 0
                releaseLocks() // Release CPU WakeLock and WifiLock so device can sleep
                cancelHeartbeat() // Cancel 60s hardware wake alarm

                if (!status.isWifiConnected) {
                    AppLogger.d(TAG, "Wi-Fi not connected (non-interference mode, locks released)")
                } else {
                    AppLogger.i(TAG, "External Wi-Fi detected (gateway probe unreachable). Keepalive paused in non-interference mode.")
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Keepalive check failed: ${e.message}", e)
        }
    }

    private fun startKeepaliveLoop() {
        loopJob?.cancel()
        loopJob = serviceScope.launch {
            AppLogger.d(TAG, "Keepalive loop started")
            while (isActive) {
                performKeepaliveCheck()
                val isCampus = portalRepository.statusFlow.value.isPesuWifi
                val sleepInterval = if (isCampus) KEEPALIVE_INTERVAL_MS else (KEEPALIVE_INTERVAL_MS * 5)
                delay(sleepInterval)
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
                    AppLogger.i(TAG, "NetworkCallback: Wi-Fi onAvailable ($network)")
                    // Debounce rapid AP transitions
                    reconnectJob?.cancel()
                    reconnectJob = serviceScope.launch {
                        delay(1500L) // Allow DHCP and interface binding to settle
                        performKeepaliveCheck(force = true)

                        val status = portalRepository.statusFlow.value
                        if (status.isWifiConnected && status.isPesuWifi) {
                            AppLogger.i(TAG, "Campus Wi-Fi confirmed on connection")
                            if (!status.isLoggedIn) {
                                val activeUser = accountRepository.getActiveUser()
                                if (activeUser != null) {
                                    AppLogger.i(TAG, "Network available: auto-authenticating as $activeUser")
                                    portalRepository.login(activeUser)
                                    consecutiveFailureCount = 0
                                    updateForegroundNotification()
                                }
                            }
                            acquireLocks()
                            scheduleNextHeartbeat()
                            startKeepaliveLoop()
                        } else {
                            AppLogger.i(TAG, "External network confirmed: entering non-interference standby")
                            releaseLocks()
                            cancelHeartbeat()
                        }
                    }
                }

                override fun onLost(network: Network) {
                    AppLogger.i(TAG, "NetworkCallback: Wi-Fi onLost ($network)")
                    reconnectJob?.cancel()
                    loopJob?.cancel()
                    consecutiveFailureCount = 0
                    releaseLocks()
                    cancelHeartbeat()
                    serviceScope.launch {
                        portalRepository.refreshStatus()
                        updateForegroundNotification()
                    }
                }
            }

            networkCallback = callback
            connectivityManager.registerNetworkCallback(request, callback)
            AppLogger.d(TAG, "Registered NetworkCallback for TRANSPORT_WIFI")
        } catch (e: Exception) {
            AppLogger.w(TAG, "Could not register NetworkCallback: ${e.message}")
        }
    }

    private suspend fun updateForegroundNotification() {
        val status = portalRepository.statusFlow.value
        val title = when {
            !status.isWifiConnected -> "PESU WiFi: Disconnected"
            !status.isPesuWifi -> "PESU WiFi: Paused"
            status.isLoggedIn -> "PESU WiFi: Active"
            else -> "PESU WiFi: Logged Out"
        }

        val content = when {
            !status.isWifiConnected -> "Waiting for Wi-Fi connection..."
            !status.isPesuWifi -> "Connected to external Wi-Fi. Auto-resumes on campus."
            status.isLoggedIn -> "Logged in as ${status.activeUsername ?: "active"}"
            else -> "Session inactive on PESU Wi-Fi. Tap to login."
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
        AppLogger.i(TAG, "WifiKeepaliveService onDestroy")
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
