package com.imyash.pesuwifi.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import com.imyash.pesuwifi.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetAddress

data class PortalStatus(
    val isWifiConnected: Boolean = false,
    val isPesuWifi: Boolean = false,
    val isPortalOnline: Boolean = false,
    val isLoggedIn: Boolean = false,
    val activeUsername: String? = null,
    val latencyMs: Long? = null,
    val lastCheckedTimestamp: Long = 0,
    val statusMessage: String = "Initializing..."
)

class PortalRepository(
    private val context: Context,
    private val accountRepository: AccountRepository = AccountRepository.getInstance(context),
    private val api: PortalApi = PortalApi
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _statusFlow = MutableStateFlow(PortalStatus())
    val statusFlow: StateFlow<PortalStatus> = _statusFlow.asStateFlow()

    private var consecutiveProbeFailures = 0

    @Volatile
    private var lastConfirmedCampusNetwork: Network? = null
    @Volatile
    private var lastConfirmedCampusSsid: String? = null
    @Volatile
    private var lastConfirmedCampusTime: Long = 0L

    fun getWifiLocalAddress(wifiNet: Network? = null): InetAddress? {
        val net = wifiNet ?: getWifiNetwork() ?: return null
        val lp = connectivityManager?.getLinkProperties(net) ?: return null
        return lp.linkAddresses.map { it.address }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
    }

    /**
     * Resolves the active Wi-Fi Network instance across all available network interfaces.
     * This avoids the cm.activeNetwork pitfall where Mobile Data is default when Wi-Fi is unvalidated.
     */
    fun getWifiNetwork(): Network? {
        val cm = connectivityManager ?: return null
        return cm.allNetworks.firstOrNull { network ->
            val caps = cm.getNetworkCapabilities(network) ?: return@firstOrNull false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        }
    }

    fun isWifiConnected(): Boolean {
        return getWifiNetwork() != null
    }

    fun getCurrentWifiSsid(wifiNet: Network? = null): String? {
        val cm = connectivityManager ?: return null
        val targetNet = wifiNet ?: getWifiNetwork() ?: return null
        val caps = cm.getNetworkCapabilities(targetNet)
        val wifiInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            caps?.transportInfo as? android.net.wifi.WifiInfo
        } else {
            @Suppress("DEPRECATION")
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            wm?.connectionInfo
        }
        val ssid = wifiInfo?.ssid?.takeIf { it != "<unknown ssid>" }
            ?: run {
                @Suppress("DEPRECATION")
                val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
                wm?.connectionInfo?.ssid?.takeIf { it != "<unknown ssid>" }
            }
        val clean = ssid?.replace("\"", "")?.trim()
        return clean?.takeIf { it.isNotEmpty() && it != "<unknown ssid>" }
    }

    fun getCurrentWifiBssid(wifiNet: Network? = null): String? {
        val cm = connectivityManager ?: return null
        val targetNet = wifiNet ?: getWifiNetwork() ?: return null
        val caps = cm.getNetworkCapabilities(targetNet)
        val wifiInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            caps?.transportInfo as? android.net.wifi.WifiInfo
        } else {
            @Suppress("DEPRECATION")
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            wm?.connectionInfo
        }
        val bssid = wifiInfo?.bssid?.takeIf { it != "02:00:00:00:00:00" }
            ?: run {
                @Suppress("DEPRECATION")
                val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
                wm?.connectionInfo?.bssid?.takeIf { it != "02:00:00:00:00:00" }
            }
        return bssid
    }

    fun isCampusSsid(ssid: String?): Boolean {
        if (ssid.isNullOrBlank()) return false
        val clean = ssid.replace("\"", "").trim()
        return clean.contains("PESU", ignoreCase = true)
    }

    /**
     * Determines whether the given (or active) Wi-Fi network is the PESU campus network.
     * 1. If SSID is known, it is the authoritative source of truth (must contain "PESU").
     * 2. If SSID is redacted/unknown (e.g. during AP roaming handoffs or Android 12 location policy),
     *    check if this active connection was recently verified as campus Wi-Fi.
     * 3. If SSID is redacted and no recent cache, check campus-specific network infrastructure:
     *    Cyberoam gateway (192.168.254.*) or PESU internal DNS (192.168.3.2).
     */
    fun isCampusNetwork(wifiNet: Network? = null): Boolean {
        val net = wifiNet ?: getWifiNetwork() ?: return false

        // 1. Check SSID if available: if known, it is the definitive indicator
        val ssid = getCurrentWifiSsid(net)
        if (!ssid.isNullOrBlank()) {
            val isCampus = isCampusSsid(ssid)
            if (isCampus) {
                lastConfirmedCampusNetwork = net
                lastConfirmedCampusSsid = ssid
                lastConfirmedCampusTime = SystemClock.elapsedRealtime()
                return true
            } else {
                // Definitive non-campus SSID (home, personal hotspot, etc.)
                lastConfirmedCampusNetwork = null
                lastConfirmedCampusSsid = null
                lastConfirmedCampusTime = 0L
                return false
            }
        }

        // 1b. Check BSSID against campus AP database: 0ms recognition if router MAC is known
        val now = SystemClock.elapsedRealtime()
        val bssid = getCurrentWifiBssid(net)
        if (!bssid.isNullOrBlank() && BssidDatabase.isCampusBssid(context, bssid)) {
            lastConfirmedCampusNetwork = net
            if (ssid != null) lastConfirmedCampusSsid = ssid
            lastConfirmedCampusTime = now
            AppLogger.d("PortalRepository", "isCampusNetwork: BSSID $bssid matched campus AP database. Instant campus recognition.")
            return true
        }

        // 2. If SSID is redacted/unknown, check if recently confirmed on campus.
        // During roaming between campus APs, the SSID is momentarily null, but the device
        // remains continuously connected on campus.
        if (lastConfirmedCampusTime > 0L && (now - lastConfirmedCampusTime) < 120_000L) {
            if (lastConfirmedCampusNetwork == null || lastConfirmedCampusNetwork == net) {
                AppLogger.d("PortalRepository", "isCampusNetwork: SSID is null/redacted, but connection was confirmed campus ${(now - lastConfirmedCampusTime) / 1000}s ago (roaming handoff). Preserving campus status.")
                return true
            }
        }

        // 3. Only if SSID is redacted/unknown and no recent confirmation, check campus-specific network infrastructure
        val lp = connectivityManager?.getLinkProperties(net)
        if (lp != null) {
            val gw = lp.routes.firstOrNull { it.isDefaultRoute }?.gateway?.hostAddress
            if (gw != null && gw.startsWith("192.168.254.")) {
                lastConfirmedCampusNetwork = net
                lastConfirmedCampusTime = now
                return true
            }

            val dns = lp.dnsServers.mapNotNull { it.hostAddress }
            if (dns.contains("192.168.3.2")) {
                lastConfirmedCampusNetwork = net
                lastConfirmedCampusTime = now
                return true
            }
        }

        return false
    }

    fun clearCampusCache() {
        lastConfirmedCampusNetwork = null
        lastConfirmedCampusSsid = null
        lastConfirmedCampusTime = 0L
    }

    suspend fun refreshStatus(): PortalStatus = withContext(Dispatchers.IO) {
        val wifiNet = getWifiNetwork()
        val localIp = getWifiLocalAddress(wifiNet)
        api.setWifiSocketFactory(wifiNet?.socketFactory, localIp)

        val activeUser = accountRepository.getActiveUser()

        if (wifiNet == null) {
            consecutiveProbeFailures = 0
            val status = PortalStatus(
                isWifiConnected = false,
                isPesuWifi = false,
                isPortalOnline = false,
                isLoggedIn = false,
                activeUsername = activeUser,
                lastCheckedTimestamp = System.currentTimeMillis(),
                statusMessage = "Wi-Fi disconnected"
            )
            _statusFlow.value = status
            AppLogger.d("PortalRepository", "refreshStatus: Wi-Fi is not connected")
            return@withContext status
        }

        val currentSsid = getCurrentWifiSsid(wifiNet)
        val isCampus = isCampusNetwork(wifiNet)

        val start = System.currentTimeMillis()
        val portalUp = api.isPortalOnline()
        val latency = System.currentTimeMillis() - start

        if (portalUp) {
            lastConfirmedCampusNetwork = wifiNet
            if (!currentSsid.isNullOrBlank()) {
                lastConfirmedCampusSsid = currentSsid
            }
            lastConfirmedCampusTime = SystemClock.elapsedRealtime()
        }

        if (!portalUp) {
            if (isCampus) {
                // Connected to campus AP, but gateway probe failed (roaming, packet drop, or VPN routing RFC-1918)
                val status = PortalStatus(
                    isWifiConnected = true,
                    isPesuWifi = true, // Preserve campus classification so keepalive loop does not terminate
                    isPortalOnline = false,
                    isLoggedIn = false,
                    activeUsername = activeUser,
                    latencyMs = latency,
                    lastCheckedTimestamp = System.currentTimeMillis(),
                    statusMessage = "PESU Wi-Fi: Gateway probe unreachable (roaming or VPN active)"
                )
                _statusFlow.value = status
                AppLogger.roam("PortalRepository", "refreshStatus: Campus network active (SSID='$currentSsid', IP='${localIp?.hostAddress}'), but gateway unreachable. Preserving campus mode (latency ${latency}ms)")
                return@withContext status
            } else {
                // Truly external Wi-Fi (Home, Hotspot, Office) where PESU gateway does not exist
                val status = PortalStatus(
                    isWifiConnected = true,
                    isPesuWifi = false,
                    isPortalOnline = false,
                    isLoggedIn = false,
                    activeUsername = activeUser,
                    latencyMs = latency,
                    lastCheckedTimestamp = System.currentTimeMillis(),
                    statusMessage = "External Wi-Fi (Keepalive paused)"
                )
                _statusFlow.value = status
                AppLogger.i("PortalRepository", "refreshStatus: Connected to external Wi-Fi '$currentSsid' (gateway probe timed out)")
                return@withContext status
            }
        }

        val targetUser = activeUser ?: "test"
        var loggedIn = api.checkLive(targetUser)

        if (loggedIn) {
            // Verify real internet routing to catch "Zombie Sessions" (Cyberoam says live, but AP intercepts)
            val probeResult = api.verifyInternetConnectivity()
            when (probeResult) {
                PortalApi.InternetProbeResult.CAPTIVE_PORTAL -> {
                    AppLogger.roam("PortalRepository", "ZOMBIE SESSION DETECTED: checkLive reported true, but captive portal intercepted HTTP traffic. Invalidating session state to force re-auth.")
                    loggedIn = false
                    consecutiveProbeFailures = 0
                }
                PortalApi.InternetProbeResult.ONLINE -> {
                    consecutiveProbeFailures = 0
                    try {
                        connectivityManager?.reportNetworkConnectivity(wifiNet, true)
                    } catch (e: Exception) {
                        // Ignore security or OEM restrictions
                    }
                }
                PortalApi.InternetProbeResult.FAILED -> {
                    // Do NOT invalidate loggedIn when probe times out or DNS has brief glitches!
                    // Cyberoam explicitly confirmed the session is active (ack="ack").
                    // Only an actual captive portal redirect (CAPTIVE_PORTAL) indicates a zombie session.
                    AppLogger.d("PortalRepository", "Internet probe timed out/unreachable, but Cyberoam confirmed session is live. Preserving loggedIn=true.")
                }
            }
        } else {
            consecutiveProbeFailures = 0
        }

        val message = if (loggedIn) {
            "Connected as ${activeUser ?: "active session"}"
        } else {
            "PESU Wi-Fi connected (Logged out)"
        }

        val status = PortalStatus(
            isWifiConnected = true,
            isPesuWifi = true,
            isPortalOnline = true,
            isLoggedIn = loggedIn,
            activeUsername = activeUser,
            latencyMs = latency,
            lastCheckedTimestamp = System.currentTimeMillis(),
            statusMessage = message
        )
        _statusFlow.value = status
        AppLogger.i("PortalRepository", "refreshStatus: PESU Wi-Fi active (loggedIn=$loggedIn, latency=${latency}ms)")
        status
    }

    suspend fun login(targetUsername: String? = null): Result<String> = withContext(Dispatchers.IO) {
        val wifiNet = getWifiNetwork()
        api.setWifiSocketFactory(wifiNet?.socketFactory, getWifiLocalAddress(wifiNet))

        val username = targetUsername ?: accountRepository.getActiveUser()
            ?: run {
                AppLogger.w("PortalRepository", "Login aborted: No account configured")
                return@withContext Result.failure<String>(Exception("No account configured. Add an account first."))
            }
        val password = accountRepository.getPassword(username)
            ?: run {
                AppLogger.w("PortalRepository", "Login aborted: No password saved for $username")
                return@withContext Result.failure<String>(Exception("No password saved for '$username'"))
            }

        AppLogger.i("PortalRepository", "Initiating login for $username")
        val result = api.login(username, password)
        if (result.isSuccess) {
            accountRepository.setActiveUser(username)
            wifiNet?.let { net ->
                try {
                    connectivityManager?.reportNetworkConnectivity(net, true)
                } catch (e: Exception) {
                    // Ignore security or OEM restrictions
                }
            }
            refreshStatus()
        }
        result
    }

    suspend fun logout(): Result<String> = withContext(Dispatchers.IO) {
        val wifiNet = getWifiNetwork()
        api.setWifiSocketFactory(wifiNet?.socketFactory, getWifiLocalAddress(wifiNet))

        val username = accountRepository.getActiveUser() ?: "user"
        AppLogger.i("PortalRepository", "Initiating logout for $username")
        val result = api.logout(username)
        refreshStatus()
        result
    }

    companion object {
        @Volatile
        private var instance: PortalRepository? = null

        fun getInstance(context: Context): PortalRepository {
            return instance ?: synchronized(this) {
                instance ?: PortalRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
