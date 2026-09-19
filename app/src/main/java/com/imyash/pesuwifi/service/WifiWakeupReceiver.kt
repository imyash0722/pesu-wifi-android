package com.imyash.pesuwifi.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.os.Build
import android.os.PowerManager
import com.imyash.pesuwifi.data.AccountRepository
import com.imyash.pesuwifi.data.PortalRepository
import com.imyash.pesuwifi.util.AppLogger
import com.imyash.pesuwifi.util.RouterPing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * OS-level BroadcastReceiver awakened directly by Android system events:
 * 1. WifiNetworkSuggestion post-connection (Android 10+)
 * 2. ConnectivityManager persistent NetworkCallback PendingIntent
 * 3. System connectivity changes
 *
 * Verifies router reachability upon physical AP (BSSID) handovers and
 * guarantees automatic authentication and keepalive persistence.
 */
class WifiWakeupReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        AppLogger.i(TAG, "OS system call received: action=$action")

        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = pm?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "PesuWifi:WakeupReceiverLock"
        )?.apply {
            setReferenceCounted(false)
            acquire(15_000L) // 15-second safety timeout
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleSystemNetworkEvent(context, intent)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error processing OS network event: ${e.message}", e)
            } finally {
                try {
                    wakeLock?.let { if (it.isHeld) it.release() }
                } catch (_: Exception) {
                }
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleSystemNetworkEvent(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val portalRepo = PortalRepository.getInstance(appContext)
        val accountRepo = AccountRepository.getInstance(appContext)
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return

        // 1. Resolve network handle from intent or active Wi-Fi
        val network: Network? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK, Network::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK)
        } ?: cm.activeNetwork ?: portalRepo.getWifiNetwork()

        val caps: NetworkCapabilities? = if (network != null) {
            cm.getNetworkCapabilities(network)
        } else null

        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
                portalRepo.getWifiNetwork() != null

        if (!isWifi) {
            AppLogger.d(TAG, "Non-Wi-Fi network event, ignoring.")
            return
        }

        val currentSsid = portalRepo.getCurrentWifiSsid(network)
        val wifiInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            caps?.transportInfo as? WifiInfo
        } else null

        val currentBssid = wifiInfo?.bssid?.takeIf { it != "02:00:00:00:00:00" }
            ?: portalRepo.getCurrentWifiBssid(network)

        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastBssid = prefs.getString(KEY_LAST_BSSID, null)
        val bssidChanged = currentBssid != null && lastBssid != null && currentBssid != lastBssid

        if (currentBssid != null) {
            prefs.edit().putString(KEY_LAST_BSSID, currentBssid).apply()
        }

        val gateway = portalRepo.getDefaultGateway(network)

        // BSSID change: ping router to verify physical link
        if (bssidChanged) {
            AppLogger.roam(TAG, "BSSID changed: '$lastBssid' -> '$currentBssid'. Pinging router gateway ($gateway)...")
            val pingOk = RouterPing.pingGateway(gateway)
            AppLogger.roam(TAG, "Router gateway ping: success=$pingOk")
        }

        val isCampus = portalRepo.isCampusNetwork(network)
        val isCaptivePortal = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) == true

        AppLogger.i(TAG, "OS Event: SSID='$currentSsid', BSSID='$currentBssid', isCampus=$isCampus, isCaptivePortal=$isCaptivePortal")

        if (isCampus) {
            // Ping router to confirm connection
            val pingOk = RouterPing.pingGateway(gateway)
            AppLogger.d(TAG, "Router ping ($gateway): success=$pingOk")

            val status = portalRepo.refreshStatus()
            val activeUser = accountRepo.getActiveUser()

            if ((!status.isLoggedIn || isCaptivePortal) && activeUser != null) {
                AppLogger.i(TAG, "Campus network active (isLoggedIn=${status.isLoggedIn}, isCaptivePortal=$isCaptivePortal): auto-authenticating as $activeUser")
                val result = portalRepo.login(activeUser)
                if (result.isSuccess) {
                    AppLogger.i(TAG, "Auto-authentication SUCCESS for $activeUser via OS wakeup rule!")
                    try {
                        network?.let { cm.reportNetworkConnectivity(it, true) }
                    } catch (_: Exception) {
                    }
                    WifiKeepaliveService.start(appContext)
                } else {
                    AppLogger.w(TAG, "Auto-authentication failed: ${result.exceptionOrNull()?.message}")
                }
            } else if (status.isLoggedIn) {
                AppLogger.d(TAG, "Campus session confirmed active as ${status.activeUsername}")
                val keepaliveEnabled = appContext.getSharedPreferences("pesu_wifi_settings", Context.MODE_PRIVATE)
                    .getBoolean("autostart_on_boot", true)
                if (keepaliveEnabled) {
                    WifiKeepaliveService.start(appContext)
                }
            }
        } else {
            AppLogger.i(TAG, "External Wi-Fi network detected ('$currentSsid'). Entering non-interference standby.")
        }
    }

    companion object {
        private const val TAG = "WifiWakeupReceiver"
        private const val PREFS_NAME = "pesu_wifi_wakeup_prefs"
        private const val KEY_LAST_BSSID = "last_known_bssid"

        const val ACTION_NETWORK_CALLBACK = "com.imyash.pesuwifi.ACTION_NETWORK_CALLBACK"
        const val ACTION_WIFI_SUGGESTION = "android.net.wifi.action.WIFI_NETWORK_SUGGESTION_POST_CONNECTION"
    }
}
