package com.imyash.pesuwifi.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.imyash.pesuwifi.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

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
        return ssid?.replace("\"", "")?.trim()
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
        return ssid.contains("PESU", ignoreCase = true)
    }

    suspend fun refreshStatus(): PortalStatus = withContext(Dispatchers.IO) {
        val wifiNet = getWifiNetwork()
        api.setWifiSocketFactory(wifiNet?.socketFactory)

        val activeUser = accountRepository.getActiveUser()

        if (wifiNet == null) {
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
        val isCampusNetwork = isCampusSsid(currentSsid)

        val start = System.currentTimeMillis()
        val portalUp = api.isPortalOnline()
        val latency = System.currentTimeMillis() - start

        if (!portalUp) {
            if (isCampusNetwork) {
                // Connected to campus AP, but gateway probe failed (e.g. roaming transition or weak signal)
                val status = PortalStatus(
                    isWifiConnected = true,
                    isPesuWifi = true, // Preserve campus classification so keepalive loop does not terminate
                    isPortalOnline = false,
                    isLoggedIn = false,
                    activeUsername = activeUser,
                    latencyMs = latency,
                    lastCheckedTimestamp = System.currentTimeMillis(),
                    statusMessage = "PESU Wi-Fi: Gateway probe unreachable (roaming...)"
                )
                _statusFlow.value = status
                AppLogger.roam("PortalRepository", "refreshStatus: Campus SSID '$currentSsid' active, but gateway probe unreachable. Preserving campus mode (latency ${latency}ms)")
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
            val internetOk = api.verifyInternetConnectivity()
            if (!internetOk) {
                AppLogger.roam("PortalRepository", "ZOMBIE SESSION DETECTED: checkLive reported true, but internet probe (generate_204) failed/intercepted. Invalidating session state to force re-auth.")
                loggedIn = false
            } else {
                try {
                    connectivityManager?.reportNetworkConnectivity(wifiNet, true)
                } catch (e: Exception) {
                    // Ignore security or OEM restrictions
                }
            }
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
        api.setWifiSocketFactory(wifiNet?.socketFactory)

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
        api.setWifiSocketFactory(wifiNet?.socketFactory)

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
