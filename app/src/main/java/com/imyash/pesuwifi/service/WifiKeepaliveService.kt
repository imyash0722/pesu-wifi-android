package com.imyash.pesuwifi.service

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.SupplicantState
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.imyash.pesuwifi.MainActivity
import com.imyash.pesuwifi.PesuWifiApp
import com.imyash.pesuwifi.R
import com.imyash.pesuwifi.data.AccountRepository
import com.imyash.pesuwifi.data.PortalApi
import com.imyash.pesuwifi.data.PortalRepository
import com.imyash.pesuwifi.data.WifiSuggestionManager
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

data class WifiTelemetry(
    val isConnected: Boolean = false,
    val ssid: String? = null,
    val bssid: String? = null,
    val rssi: Int? = null,
    val frequency: Int? = null,
    val channel: Int? = null,
    val ip: String? = null,
    val gateway: String? = null,
    val isCampus: Boolean = false,
    val lastUpdate: Long = 0L
)

class WifiKeepaliveService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var loopJob: Job? = null
    private var reconnectJob: Job? = null
    private var autoReconnectWatchdogJob: Job? = null

    private lateinit var portalRepository: PortalRepository
    private lateinit var accountRepository: AccountRepository
    private lateinit var connectivityManager: ConnectivityManager
    private var wifiManager: WifiManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var wifiReceiverRegistered = false

    private var activeWifiNetwork: Network? = null
    private var wasCampusNetwork = false
    private var authBackoffUntilTimestamp = 0L

    private val isAuthBackoffActive: Boolean
        get() = SystemClock.elapsedRealtime() < authBackoffUntilTimestamp

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var consecutiveFailureCount = 0
    private var lastCheckTimestamp = 0L
    private var lastBssid: String? = null
    private var lastSsid: String? = null
    private var lastRssi: Int = -127
    private var lastFrequency: Int = 0
    private var lastIpAddresses: List<String> = emptyList()
    private var lastGateway: String? = null
    private var lastFastAuthIp: String? = null
    private var lastFastAuthTime: Long = 0L

    override fun onCreate() {
        super.onCreate()
        AppLogger.i(TAG, "WifiKeepaliveService onCreate")
        activeInstance = this
        portalRepository = PortalRepository.getInstance(this)
        accountRepository = AccountRepository.getInstance(this)
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

        registerNetworkMonitor()
        registerWifiEventReceiver()
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
                @Suppress("DEPRECATION")
                val mode = WifiManager.WIFI_MODE_FULL_HIGH_PERF
                wifiLock = wm.createWifiLock(mode, "PesuWifi:KeepaliveWifiLock").apply {
                    setReferenceCounted(false)
                    acquire()
                }
                AppLogger.d(TAG, "WifiLock acquired (mode: WIFI_MODE_FULL_HIGH_PERF)")
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
            val intent = Intent(this, KeepaliveAlarmReceiver::class.java).apply {
                action = ACTION_HEARTBEAT
            }
            val pendingIntent = PendingIntent.getBroadcast(
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
            AppLogger.d(TAG, "Scheduled next heartbeat alarm via KeepaliveAlarmReceiver in ${KEEPALIVE_INTERVAL_MS / 1000}s")
        } catch (e: Exception) {
            AppLogger.w(TAG, "Could not schedule exact heartbeat alarm: ${e.message}")
        }
    }

    private fun cancelHeartbeat() {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(this, KeepaliveAlarmReceiver::class.java).apply {
                action = ACTION_HEARTBEAT
            }
            val pendingIntent = PendingIntent.getBroadcast(
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
            ACTION_SET_DEBUG_PORTAL -> {
                val url = intent?.getStringExtra("url")
                if (!url.isNullOrBlank()) {
                    PortalApi.portalBaseUrl = url
                    AppLogger.i(TAG, "Debug portalBaseUrl set to: $url")
                } else {
                    PortalApi.portalBaseUrl = PortalApi.DEFAULT_PORTAL_BASE
                    AppLogger.i(TAG, "portalBaseUrl reset to default: ${PortalApi.DEFAULT_PORTAL_BASE}")
                }
                serviceScope.launch {
                    performKeepaliveCheck(force = true)
                }
            }
            ACTION_SET_TEST_ACCOUNT -> {
                val user = intent?.getStringExtra("username") ?: "PES1UG20CS001"
                val pass = intent?.getStringExtra("password") ?: "testpassword"
                accountRepository.saveAccount(user, pass)
                accountRepository.setActiveUser(user)
                AppLogger.i(TAG, "Test account saved: $user")
                serviceScope.launch {
                    performKeepaliveCheck(force = true)
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
        val status = portalRepository.statusFlow.value
        val notification = buildNotification(
            title = "PESU WiFi Keepalive Active",
            content = "Monitoring network status...",
            isLoggedIn = status.isLoggedIn,
            isWifiConnected = status.isWifiConnected
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val hasFineLoc = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarseLoc = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val type = if (hasFineLoc || hasCoarseLoc) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            }
            startForeground(
                PesuWifiApp.NOTIFICATION_ID,
                notification,
                type
            )
        } else {
            startForeground(PesuWifiApp.NOTIFICATION_ID, notification)
        }
    }

    private suspend fun performKeepaliveCheck(force: Boolean = false, isRoamingEvent: Boolean = false) {
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
                wasCampusNetwork = true
                acquireLocks()
                scheduleNextHeartbeat()

                if (status.isLoggedIn) {
                    consecutiveFailureCount = 0
                    authBackoffUntilTimestamp = 0L
                    AppLogger.d(TAG, "Keepalive check OK: Logged in as ${status.activeUsername} (latency ${status.latencyMs}ms)")
                    reportConnectivityValidated()
                } else {
                    if (isAuthBackoffActive) {
                        val remainingSec = ((authBackoffUntilTimestamp - SystemClock.elapsedRealtime()) / 1000).coerceAtLeast(1)
                        AppLogger.w(TAG, "Auth backoff active (${remainingSec}s remaining): skipping automatic re-login to avoid rate limits.")
                        return
                    }

                    consecutiveFailureCount++
                    AppLogger.w(TAG, "Session inactive on PESU Wi-Fi (failure count $consecutiveFailureCount, isRoamingEvent=$isRoamingEvent)")
                    if (isRoamingEvent || consecutiveFailureCount >= 1) {
                        val activeUser = accountRepository.getActiveUser()
                        if (activeUser != null) {
                            AppLogger.i(TAG, "Initiating re-authentication for $activeUser (trigger: ${if (isRoamingEvent) "roaming AP change" else "keepalive check"})...")
                            val result = portalRepository.login(activeUser)
                            if (result.isSuccess) {
                                AppLogger.i(TAG, "Re-authentication succeeded for $activeUser")
                                consecutiveFailureCount = 0
                                authBackoffUntilTimestamp = 0L
                                reportConnectivityValidated()
                            } else {
                                val err = result.exceptionOrNull()?.message ?: ""
                                AppLogger.e(TAG, "Re-authentication failed: $err")
                                val cooldownMs = if (err.contains("password", ignoreCase = true) || err.contains("credential", ignoreCase = true)) {
                                    60_000L // 60s cooldown for invalid credentials
                                } else {
                                    30_000L // 30s auto-expiring cooldown for temporary server lock
                                }
                                authBackoffUntilTimestamp = SystemClock.elapsedRealtime() + cooldownMs
                                AppLogger.w(TAG, "Auth backoff active for ${cooldownMs / 1000}s. Automatic retries paused temporarily.")
                            }
                            updateForegroundNotification()
                        } else {
                            AppLogger.w(TAG, "No active user configured for auto-re-login")
                        }
                    }
                }
            } else {
                // Non-interference mode: check if connected to campus SSID before dropping locks
                val isKnownCampus = lastSsid?.contains("PESU", ignoreCase = true) == true
                if (isKnownCampus && status.isWifiConnected) {
                    // Connected to campus SSID, but gateway probe was temporarily unreachable during roaming
                    wasCampusNetwork = true
                    acquireLocks()
                    scheduleNextHeartbeat()
                    AppLogger.roam(TAG, "Campus SSID ($lastSsid) active but gateway probe unreachable. Holding locks and continuing keepalive.")
                } else {
                    consecutiveFailureCount = 0
                    releaseLocks() // Release CPU WakeLock and WifiLock so device can sleep
                    cancelHeartbeat() // Cancel 60s hardware wake alarm

                    if (!status.isWifiConnected) {
                        AppLogger.d(TAG, "Wi-Fi not connected (non-interference mode, locks released)")
                    } else {
                        wasCampusNetwork = false
                        loopJob?.cancel()
                        loopJob = null
                        AppLogger.i(TAG, "External Wi-Fi detected ($lastSsid). Keepalive entered dormant mode.")
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Keepalive check failed: ${e.message}", e)
        }
    }

    private fun reportConnectivityValidated() {
        try {
            val net = activeWifiNetwork ?: portalRepository.getWifiNetwork()
            if (net != null) {
                connectivityManager.reportNetworkConnectivity(net, true)
                AppLogger.d(TAG, "Reported network connectivity VALIDATED to ConnectivityManager")
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Could not report network connectivity: ${e.message}")
        }
    }

    private fun startKeepaliveLoop() {
        loopJob?.cancel()
        loopJob = serviceScope.launch {
            AppLogger.d(TAG, "Keepalive loop started")
            while (isActive) {
                performKeepaliveCheck()
                val status = portalRepository.statusFlow.value
                val isCampus = status.isPesuWifi || lastSsid?.contains("PESU", ignoreCase = true) == true

                if (!isCampus && status.isWifiConnected) {
                    AppLogger.i(TAG, "Keepalive loop: Non-campus Wi-Fi ($lastSsid) detected. Entering dormant standby.")
                    break
                } else if (!status.isWifiConnected) {
                    AppLogger.i(TAG, "Keepalive loop: Wi-Fi disconnected. Entering standby.")
                    break
                }

                // If on campus, but gateway probe was temporarily unreachable, retry sooner (10s)
                val sleepInterval = if (!status.isPortalOnline) 10_000L else KEEPALIVE_INTERVAL_MS
                delay(sleepInterval)
            }
        }
    }

    private fun registerWifiEventReceiver() {
        try {
            val filter = IntentFilter().apply {
                addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
                addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION)
                addAction(WifiManager.RSSI_CHANGED_ACTION)
                addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    addAction(WifiManager.ACTION_WIFI_NETWORK_SUGGESTION_POST_CONNECTION)
                }
            }
            registerReceiver(wifiEventReceiver, filter)
            wifiReceiverRegistered = true
            AppLogger.d(TAG, "Registered Wi-Fi broadcast event receiver (NetworkState, Supplicant, RSSI, ScanResults, NetworkSuggestionPostConn)")
        } catch (e: Exception) {
            AppLogger.w(TAG, "Could not register Wi-Fi event receiver: ${e.message}")
        }
    }

    private fun unregisterWifiEventReceiver() {
        if (wifiReceiverRegistered) {
            try {
                unregisterReceiver(wifiEventReceiver)
                AppLogger.d(TAG, "Unregistered Wi-Fi broadcast event receiver")
            } catch (_: Exception) {}
            wifiReceiverRegistered = false
        }
    }

    private val wifiEventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            val action = intent?.action ?: return
            when (action) {
                WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                    @Suppress("DEPRECATION")
                    val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiManager.EXTRA_NETWORK_INFO)
                    @Suppress("DEPRECATION")
                    val wifiInfo = intent.getParcelableExtra<WifiInfo>(WifiManager.EXTRA_WIFI_INFO)
                    val extraBssid = intent.getStringExtra(WifiManager.EXTRA_BSSID)
                    val effectiveBssid = extraBssid?.takeIf { it != "02:00:00:00:00:00" }
                        ?: wifiInfo?.bssid?.takeIf { it != "02:00:00:00:00:00" }
                        ?: @Suppress("DEPRECATION") wifiManager?.connectionInfo?.bssid?.takeIf { it != "02:00:00:00:00:00" }
                    val effectiveSsid = wifiInfo?.ssid?.takeIf { it != "<unknown ssid>" }
                        ?: @Suppress("DEPRECATION") wifiManager?.connectionInfo?.ssid?.takeIf { it != "<unknown ssid>" }
                    AppLogger.wifi(TAG, "[Broadcast:NETWORK_STATE_CHANGED] State=${networkInfo?.state}, Detailed=${networkInfo?.detailedState}, Reason=${networkInfo?.reason}, Extra='${networkInfo?.extraInfo}', SSID=$effectiveSsid, BSSID=$effectiveBssid")
                    if (effectiveBssid != null) lastBssid = effectiveBssid
                    if (effectiveSsid != null) lastSsid = effectiveSsid
                    updateTelemetry()
                }
                WifiManager.SUPPLICANT_STATE_CHANGED_ACTION -> {
                    @Suppress("DEPRECATION")
                    val state = intent.getParcelableExtra<SupplicantState>(WifiManager.EXTRA_NEW_STATE)
                    val error = intent.getIntExtra(WifiManager.EXTRA_SUPPLICANT_ERROR, 0)
                    AppLogger.wifi(TAG, "[Broadcast:SUPPLICANT_STATE] State=$state, SupplicantError=$error")
                }
                WifiManager.RSSI_CHANGED_ACTION -> {
                    val newRssi = intent.getIntExtra(WifiManager.EXTRA_NEW_RSSI, -127)
                    if (Math.abs(newRssi - lastRssi) >= 6) {
                        @Suppress("DEPRECATION")
                        val level = WifiManager.calculateSignalLevel(newRssi, 5)
                        AppLogger.d(TAG, "[Broadcast:RSSI_CHANGED] Signal: $lastRssi dBm -> $newRssi dBm (level $level/5)")
                        lastRssi = newRssi
                        updateTelemetry()
                    }
                }
                WifiManager.SCAN_RESULTS_AVAILABLE_ACTION -> {
                    val updated = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                    logScanResults(updated)
                }
                WifiManager.ACTION_WIFI_NETWORK_SUGGESTION_POST_CONNECTION -> {
                    AppLogger.wifi(TAG, "[Broadcast:SUGGESTION_POST_CONNECTION] Device connected via campus network suggestion! Initiating fast check...")
                    serviceScope.launch {
                        delay(150L)
                        performKeepaliveCheck(force = true)
                    }
                }
            }
        }
    }

    private fun logScanResults(updated: Boolean) {
        try {
            val wm = wifiManager ?: return
            @Suppress("DEPRECATION")
            val results = wm.scanResults ?: return
            val campusResults = results.filter { it.SSID.contains("PESU", ignoreCase = true) }
            val otherResults = results.filter { !it.SSID.contains("PESU", ignoreCase = true) }
            AppLogger.wifi(TAG, "[ScanResults] Detected ${results.size} APs in range (${campusResults.size} campus, ${otherResults.size} other, updated=$updated)")
            for (ap in campusResults) {
                val ch = frequencyToChannel(ap.frequency)
                AppLogger.wifi(TAG, "  -> Campus AP: BSSID=${ap.BSSID}, SSID=\"${ap.SSID}\", RSSI=${ap.level} dBm, Freq=${ap.frequency} MHz (Ch $ch), Caps=${ap.capabilities}")
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed reading scan results: ${e.message}")
        }
    }

    private fun frequencyToChannel(freq: Int): Int {
        return when {
            freq == 2484 -> 14
            freq in 2412..2472 -> (freq - 2412) / 5 + 1
            freq in 5170..5825 -> (freq - 5170) / 5 + 34
            freq in 5925..7125 -> (freq - 5925) / 5 + 1
            else -> 0
        }
    }

    private fun updateTelemetry() {
        _telemetryFlow.value = WifiTelemetry(
            isConnected = activeWifiNetwork != null,
            ssid = lastSsid?.replace("\"", ""),
            bssid = lastBssid,
            rssi = if (lastRssi > -120) lastRssi else null,
            frequency = if (lastFrequency > 0) lastFrequency else null,
            channel = if (lastFrequency > 0) frequencyToChannel(lastFrequency) else null,
            ip = lastIpAddresses.firstOrNull(),
            gateway = lastGateway,
            isCampus = wasCampusNetwork,
            lastUpdate = System.currentTimeMillis()
        )
    }

    private fun registerNetworkMonitor() {
        try {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()

            val callback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
                    override fun onAvailable(network: Network) {
                        handleNetworkAvailable(network)
                    }

                    override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                        handleNetworkCapabilitiesChanged(network, networkCapabilities)
                    }

                    override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                        handleNetworkLinkPropertiesChanged(network, linkProperties)
                    }

                    override fun onLost(network: Network) {
                        handleNetworkLost(network)
                    }
                }
            } else {
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        handleNetworkAvailable(network)
                    }

                    override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                        handleNetworkCapabilitiesChanged(network, networkCapabilities)
                    }

                    override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                        handleNetworkLinkPropertiesChanged(network, linkProperties)
                    }

                    override fun onLost(network: Network) {
                        handleNetworkLost(network)
                    }
                }
            }

            networkCallback = callback
            connectivityManager.registerNetworkCallback(request, callback)
            AppLogger.d(TAG, "Registered NetworkCallback for TRANSPORT_WIFI (FLAG_INCLUDE_LOCATION_INFO on Android 12+)")
        } catch (e: Exception) {
            AppLogger.w(TAG, "Could not register NetworkCallback: ${e.message}")
        }
    }

    private fun handleNetworkAvailable(network: Network) {
        activeWifiNetwork = network
        autoReconnectWatchdogJob?.cancel()
        autoReconnectWatchdogJob = null

        val caps = connectivityManager.getNetworkCapabilities(network)
        val lp = connectivityManager.getLinkProperties(network)
        val iface = lp?.interfaceName
        val ips = lp?.linkAddresses?.map { it.address.hostAddress }

        AppLogger.wifi(TAG, "[onAvailable] Wi-Fi network connected: $network (handle=${network.networkHandle}, iface=$iface, IPs=$ips)")
        AppLogger.wifi(TAG, "  -> Capabilities: $caps")

        // Debounce rapid AP transitions
        reconnectJob?.cancel()
        reconnectJob = serviceScope.launch {
            delay(300L) // Fast 300ms debounce: DHCP and interface binding settle quickly
            performKeepaliveCheck(force = true)

            val status = portalRepository.statusFlow.value
            val isKnownCampus = portalRepository.isCampusSsid(portalRepository.getCurrentWifiSsid())
            if (status.isWifiConnected && (status.isPesuWifi || isKnownCampus)) {
                wasCampusNetwork = true
                AppLogger.i(TAG, "Campus Wi-Fi confirmed on connection")
                if (!status.isLoggedIn && !isAuthBackoffActive) {
                    val activeUser = accountRepository.getActiveUser()
                    if (activeUser != null) {
                        AppLogger.i(TAG, "Network available: auto-authenticating as $activeUser")
                        val result = portalRepository.login(activeUser)
                        if (result.isSuccess) {
                            consecutiveFailureCount = 0
                            authBackoffUntilTimestamp = 0L
                            reportConnectivityValidated()
                        } else {
                            val err = result.exceptionOrNull()?.message ?: ""
                            val cooldownMs = if (err.contains("password", ignoreCase = true) || err.contains("credential", ignoreCase = true)) {
                                60_000L
                            } else {
                                30_000L
                            }
                            authBackoffUntilTimestamp = SystemClock.elapsedRealtime() + cooldownMs
                            AppLogger.w(TAG, "Auth backoff active for ${cooldownMs / 1000}s after connection auth failure")
                        }
                        updateForegroundNotification()
                    }
                }
                acquireLocks()
                scheduleNextHeartbeat()
                startKeepaliveLoop()
            } else {
                wasCampusNetwork = false
                AppLogger.i(TAG, "External network confirmed: entering non-interference standby")
                releaseLocks()
                cancelHeartbeat()
            }
        }
    }

    private fun handleNetworkCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
        activeWifiNetwork = network
        val wifiInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            networkCapabilities.transportInfo as? WifiInfo
        } else {
            @Suppress("DEPRECATION")
            wifiManager?.connectionInfo
        }
        @Suppress("DEPRECATION")
        val legacyConnInfo = wifiManager?.connectionInfo

        val currentBssid = wifiInfo?.bssid?.takeIf { it != "02:00:00:00:00:00" }
            ?: legacyConnInfo?.bssid?.takeIf { it != "02:00:00:00:00:00" }
            ?: portalRepository.getCurrentWifiBssid(network)

        val currentSsid = wifiInfo?.ssid?.takeIf { it != "<unknown ssid>" }
            ?: legacyConnInfo?.ssid?.takeIf { it != "<unknown ssid>" }
            ?: portalRepository.getCurrentWifiSsid(network)

        val currentRssi = wifiInfo?.rssi ?: legacyConnInfo?.rssi ?: networkCapabilities.signalStrength
        val currentFreq = wifiInfo?.frequency ?: legacyConnInfo?.frequency ?: 0
        val linkSpeed = wifiInfo?.linkSpeed ?: legacyConnInfo?.linkSpeed ?: 0
        val isValidated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val isCaptivePortal = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
        val isNotValidated = !isValidated

        AppLogger.wifi(TAG, "[onCapabilitiesChanged] Net: $network | BSSID: $currentBssid | SSID: $currentSsid | RSSI: ${currentRssi}dBm | Freq: ${currentFreq}MHz (Ch ${frequencyToChannel(currentFreq)}) | Speed: ${linkSpeed}Mbps | Validated: $isValidated | CaptivePortal: $isCaptivePortal | NotValidated: $isNotValidated")

        val bssidChanged = currentBssid != null && lastBssid != null && currentBssid != lastBssid
        if (bssidChanged) {
            AppLogger.roam(TAG, "AP ROAM DETECTED: Physical router changed! BSSID '$lastBssid' -> '$currentBssid' (SSID: '$currentSsid', RSSI: ${currentRssi}dBm, Ch: ${frequencyToChannel(currentFreq)})")
            lastBssid = currentBssid
            lastSsid = currentSsid
            lastRssi = currentRssi
            lastFrequency = currentFreq
            updateTelemetry()
            handleRoamingEvent(network)
        } else {
            if (currentBssid != null) lastBssid = currentBssid
            if (currentSsid != null) lastSsid = currentSsid
            lastRssi = currentRssi
            lastFrequency = currentFreq
            updateTelemetry()
            if (isCaptivePortal) {
                AppLogger.roam(TAG, "Captive portal flag active on network $network: triggering roam re-auth check")
                handleRoamingEvent(network)
            }
        }
    }

    private fun handleNetworkLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
        activeWifiNetwork = network
        val currentIps = linkProperties.linkAddresses.mapNotNull { it.address.hostAddress }.filter { it.isNotBlank() }
        val defaultGateway = linkProperties.routes.firstOrNull { it.isDefaultRoute }?.gateway?.hostAddress
        val dnsList = linkProperties.dnsServers.mapNotNull { it.hostAddress }
        val iface = linkProperties.interfaceName

        AppLogger.wifi(TAG, "[onLinkPropertiesChanged] Net: $network | Iface: $iface | IPs: $currentIps | Gateway: $defaultGateway | DNS: $dnsList | MTU: ${linkProperties.mtu}")

        val ipChanged = lastIpAddresses.isNotEmpty() && currentIps.isNotEmpty() && lastIpAddresses != currentIps
        val gatewayChanged = lastGateway != null && defaultGateway != null && lastGateway != defaultGateway

        val hasIpv4 = currentIps.any { it.contains(".") && !it.startsWith("127.") }
        val isCampusIp = currentIps.any { it.startsWith("10.") || it.startsWith("172.16.") }

        if (ipChanged || gatewayChanged) {
            AppLogger.roam(TAG, "NETWORK ROUTE CHANGED: IPs: $lastIpAddresses -> $currentIps, Gateway: $lastGateway -> $defaultGateway. Triggering roam recovery...")
            lastIpAddresses = currentIps
            lastGateway = defaultGateway
            updateTelemetry()
            handleRoamingEvent(network)
        } else {
            if (currentIps.isNotEmpty()) lastIpAddresses = currentIps
            if (defaultGateway != null) lastGateway = defaultGateway
            updateTelemetry()
        }

        // Zero-Latency Fast Re-Auth:
        // As soon as link properties receive an IPv4 address on campus Wi-Fi (10.* IP or campus SSID),
        // authenticate immediately without waiting for debounce delays so Android's captive portal probe
        // receives a valid 204 response and avoids adding the AP BSSID to WifiBlocklistMonitor.
        val isCampus = isCampusIp || wasCampusNetwork || lastSsid?.contains("PESU", ignoreCase = true) == true
        if (hasIpv4 && isCampus && !isAuthBackoffActive) {
            val validIpv4 = currentIps.firstOrNull { it.contains(".") && !it.startsWith("127.") } ?: ""
            triggerFastReauth(network, validIpv4)
        }
    }

    private fun triggerFastReauth(network: Network, ip: String) {
        val now = SystemClock.elapsedRealtime()
        if (ip == lastFastAuthIp && (now - lastFastAuthTime) < 4000L) {
            return
        }
        lastFastAuthIp = ip
        lastFastAuthTime = now

        serviceScope.launch {
            val status = portalRepository.statusFlow.value
            if (status.isLoggedIn) {
                AppLogger.d(TAG, "Fast re-auth check: already logged in as ${status.activeUsername}")
                reportConnectivityValidated()
                return@launch
            }

            val activeUser = accountRepository.getActiveUser()
            if (activeUser != null) {
                AppLogger.i(TAG, "⚡ FAST ZERO-LATENCY RE-AUTH triggered for $activeUser on IP $ip (<150ms after DHCP)")
                PortalApi.evictConnectionPool()
                val result = portalRepository.login(activeUser)
                if (result.isSuccess) {
                    AppLogger.i(TAG, "⚡ Fast re-auth SUCCESS for $activeUser! Network ready before captive portal check.")
                    consecutiveFailureCount = 0
                    authBackoffUntilTimestamp = 0L
                    wasCampusNetwork = true
                    acquireLocks()
                    scheduleNextHeartbeat()
                    reportConnectivityValidated()
                } else {
                    val err = result.exceptionOrNull()?.message ?: ""
                    AppLogger.w(TAG, "⚡ Fast re-auth attempt result: $err (will fallback to normal check)")
                }
                updateForegroundNotification()
            }
        }
    }

    private fun handleNetworkLost(network: Network) {
        AppLogger.w(TAG, "[onLost] Wi-Fi network disconnected: $network! Last BSSID: '$lastBssid', Last SSID: '$lastSsid', Last IPs: $lastIpAddresses. Was Campus: $wasCampusNetwork")
        if (activeWifiNetwork == network) {
            activeWifiNetwork = null
        }
        val hadCampus = wasCampusNetwork
        wasCampusNetwork = false
        lastBssid = null
        lastIpAddresses = emptyList()
        lastGateway = null
        updateTelemetry()
        reconnectJob?.cancel()
        loopJob?.cancel()
        loopJob = null
        consecutiveFailureCount = 0
        releaseLocks()
        cancelHeartbeat()

        val activeNet = connectivityManager.activeNetwork
        val activeCaps = connectivityManager.getNetworkCapabilities(activeNet)
        val isCellular = activeCaps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val isVpn = activeCaps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        AppLogger.wifi(TAG, "  -> Android active fallback network: $activeNet (Cellular=$isCellular, VPN=$isVpn)")

        serviceScope.launch {
            portalRepository.refreshStatus()
            updateForegroundNotification()
        }

        if (hadCampus) {
            triggerCampusAutoReconnectWatchdog()
        }
    }

    private fun triggerCampusAutoReconnectWatchdog() {
        autoReconnectWatchdogJob?.cancel()
        val wm = wifiManager ?: return

        if (!wm.isWifiEnabled) {
            AppLogger.watchdog(TAG, "Auto-reconnect watchdog cancelled: Wi-Fi radio is toggled OFF by user.")
            return
        }

        // Immediately re-assert Wi-Fi network suggestion so Android matches any campus AP
        WifiSuggestionManager.ensureSuggestionRegistered(this)

        AppLogger.watchdog(TAG, ">>> Starting Campus Auto-Reconnect Watchdog (Wi-Fi suggestion refreshed, actively searching for campus APs)...")

        autoReconnectWatchdogJob = serviceScope.launch {
            val startTime = SystemClock.elapsedRealtime()
            var attempt = 0
            var lastScanTime = 0L

            // Adaptive schedule:
            // - Burst phase (0 to 30s): check every 3s
            // - Active search (30s to 3m): check every 8s
            // - Extended standby (3m to 15m): check every 20s
            while (isActive) {
                val elapsed = SystemClock.elapsedRealtime() - startTime
                if (elapsed > 15 * 60 * 1000L) { // 15-minute maximum watchdog lifetime
                    AppLogger.watchdog(TAG, "Watchdog: 15-minute timeout reached without reconnecting. Entering quiet standby.")
                    break
                }

                val sleepDelay = when {
                    elapsed < 30_000L -> 3000L
                    elapsed < 3 * 60_000L -> 8000L
                    else -> 20000L
                }

                delay(sleepDelay)
                if (!isActive) break

                if (portalRepository.statusFlow.value.isWifiConnected) {
                    AppLogger.watchdog(TAG, "Watchdog: Wi-Fi reconnected successfully (elapsed ${SystemClock.elapsedRealtime() - startTime}ms). Stopping watchdog.")
                    break
                }

                if (!wm.isWifiEnabled) {
                    AppLogger.watchdog(TAG, "Watchdog: Wi-Fi was disabled by user. Stopping watchdog.")
                    break
                }

                attempt++
                val now = SystemClock.elapsedRealtime()

                // Trigger scan at most once every 12s to respect Android background scan throttling
                var scanTriggered = false
                if (now - lastScanTime >= 12_000L) {
                    try {
                        @Suppress("DEPRECATION")
                        scanTriggered = wm.startScan()
                        lastScanTime = now
                    } catch (e: Exception) {
                        AppLogger.w(TAG, "Watchdog startScan error: ${e.message}")
                    }
                }

                AppLogger.watchdog(TAG, "Watchdog Attempt #$attempt (elapsed ${elapsed / 1000}s): scanTriggered=$scanTriggered")

                // Periodically re-ensure network suggestion is active
                if (attempt % 5 == 0) {
                    WifiSuggestionManager.ensureSuggestionRegistered(this@WifiKeepaliveService)
                }

                try {
                    @Suppress("DEPRECATION")
                    val scanList = wm.scanResults
                    val campusScan = scanList?.filter { it.SSID != null && it.SSID.contains("PESU", ignoreCase = true) }
                    if (!campusScan.isNullOrEmpty()) {
                        AppLogger.watchdog(TAG, "  -> Visible campus APs (${campusScan.size}): ${campusScan.map { "${it.BSSID} (${it.level}dBm, ${it.frequency}MHz)" }}")
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun handleRoamingEvent(network: Network) {
        PortalApi.evictConnectionPool()
        authBackoffUntilTimestamp = 0L
        consecutiveFailureCount = 0
        reconnectJob?.cancel()
        reconnectJob = serviceScope.launch {
            delay(250L) // Fast 250ms debounce window for physical AP handover
            performKeepaliveCheck(force = true, isRoamingEvent = true)
            reportConnectivityValidated()
        }
    }

    private suspend fun updateForegroundNotification() {
        val status = portalRepository.statusFlow.value
        val isRoamingReconnecting = autoReconnectWatchdogJob?.isActive == true

        val title = when {
            isRoamingReconnecting -> "PESU WiFi: Roaming..."
            isAuthBackoffActive -> "PESU WiFi: Login Paused"
            !status.isWifiConnected -> "PESU WiFi: Disconnected"
            !status.isPesuWifi -> "PESU WiFi: Paused"
            status.isLoggedIn -> "PESU WiFi: Active"
            else -> "PESU WiFi: Logged Out"
        }

        val content = when {
            isRoamingReconnecting -> "Searching for nearby access point..."
            isAuthBackoffActive -> "Temporary login pause. Retrying shortly..."
            !status.isWifiConnected -> "Waiting for Wi-Fi connection..."
            !status.isPesuWifi -> "Connected to external Wi-Fi. Auto-resumes on campus."
            status.isLoggedIn -> "Logged in as ${status.activeUsername ?: "active"}"
            else -> "Session inactive on PESU Wi-Fi. Tap to login."
        }

        val notification = buildNotification(title, content, status.isLoggedIn, status.isWifiConnected)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(PesuWifiApp.NOTIFICATION_ID, notification)
    }

    private fun buildNotification(
        title: String,
        content: String,
        isLoggedIn: Boolean,
        isWifiConnected: Boolean = true
    ): Notification {
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

        val builder = NotificationCompat.Builder(this, PesuWifiApp.CHANNEL_KEEPALIVE_ID)
            .setSmallIcon(R.drawable.ic_wifi)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (isWifiConnected) {
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
            builder.addAction(actionIcon, actionTitle, actionPendingIntent)
        } else {
            // Direct 1-tap bottom sheet for Wi-Fi panel on Android 10+ (API 29+)
            val panelIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Intent(Settings.Panel.ACTION_WIFI).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            } else {
                Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }
            val panelPendingIntent = PendingIntent.getActivity(
                this,
                3,
                panelIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(R.drawable.ic_wifi, "Wi-Fi Settings", panelPendingIntent)
        }

        builder.addAction(R.drawable.ic_stop, "Stop", stopPendingIntent)
        return builder.build()
    }

    override fun onDestroy() {
        super.onDestroy()
        AppLogger.i(TAG, "WifiKeepaliveService onDestroy")
        if (activeInstance === this) {
            activeInstance = null
        }
        _isServiceRunning.value = false
        _telemetryFlow.value = WifiTelemetry()
        autoReconnectWatchdogJob?.cancel()
        reconnectJob?.cancel()
        loopJob?.cancel()
        cancelHeartbeat()
        releaseLocks()
        unregisterWifiEventReceiver()
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
        const val ACTION_SET_DEBUG_PORTAL = "com.imyash.pesuwifi.action.SET_DEBUG_PORTAL"
        const val ACTION_SET_TEST_ACCOUNT = "com.imyash.pesuwifi.action.SET_TEST_ACCOUNT"

        @Volatile
        private var activeInstance: WifiKeepaliveService? = null

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        private val _telemetryFlow = MutableStateFlow(WifiTelemetry())
        val telemetryFlow: StateFlow<WifiTelemetry> = _telemetryFlow.asStateFlow()

        fun triggerCheckFromAlarm(context: Context) {
            val service = activeInstance
            if (service != null && _isServiceRunning.value) {
                AppLogger.d(TAG, "Heartbeat alarm handled directly via active service instance")
                service.serviceScope.launch {
                    service.performKeepaliveCheck(force = false)
                }
            } else {
                AppLogger.w(TAG, "Alarm fired but service instance was null. Starting foreground service...")
                start(context)
            }
        }

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
