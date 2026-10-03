package com.imyash.pesuwifi.data

import android.content.Context
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import com.imyash.pesuwifi.util.AppLogger

object WifiSuggestionManager {
    private const val TAG = "WifiSuggestionManager"

    data class SuggestionTarget(
        val ssid: String,
        val passphrase: String? = null
    )

    val CAMPUS_TARGETS = listOf(
        SuggestionTarget("PESU-EC-Campus", "PESU-EC-Campus"),
        SuggestionTarget("PESU-EC-Campus", null),
        SuggestionTarget("PESU-RR-Campus", "PESU-RR-Campus"),
        SuggestionTarget("PESU-RR-Campus", null),
        SuggestionTarget("PESU-Campus", "PESU-Campus"),
        SuggestionTarget("PESU-Campus", null),
        SuggestionTarget("PESU-WiFi", "PESU-WiFi"),
        SuggestionTarget("PESU-WiFi", null),
        SuggestionTarget("PES_WIFI", "PES_WIFI"),
        SuggestionTarget("PES_WIFI", null),
        SuggestionTarget("PESU-CIE", "PESU-CIE"),
        SuggestionTarget("PESU-CIE", null),
        SuggestionTarget("AMAATRA_HOSTEL", "AMAATRA_HOSTEL"),
        SuggestionTarget("AMAATRA_HOSTEL", null),
        SuggestionTarget("Foodcourt", "Foodcourt"),
        SuggestionTarget("Foodcourt", null),
        SuggestionTarget("pes south cafe", "pes south cafe"),
        SuggestionTarget("pes south cafe", null)
    )

    @Volatile
    private var isRegistered = false

    private fun buildSuggestions(): List<WifiNetworkSuggestion> {
        return CAMPUS_TARGETS.mapNotNull { target ->
            try {
                val builder = WifiNetworkSuggestion.Builder()
                    .setSsid(target.ssid)
                    .setIsAppInteractionRequired(false)
                    .setIsUserInteractionRequired(false)

                if (target.passphrase != null) {
                    builder.setWpa2Passphrase(target.passphrase)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    builder.setPriority(1000)
                    builder.setIsInitialAutojoinEnabled(true)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    builder.setMacRandomizationSetting(WifiNetworkSuggestion.RANDOMIZATION_PERSISTENT)
                }
                builder.build()
            } catch (e: Exception) {
                AppLogger.w(TAG, "Error building suggestion for ${target.ssid}: ${e.message}")
                null
            }
        }
    }

    fun ensureSuggestionRegistered(context: Context, forceRefresh: Boolean = false): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            AppLogger.d(TAG, "WifiNetworkSuggestion requires Android 10+ (API 29+)")
            return false
        }

        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wm == null) {
            AppLogger.w(TAG, "WifiManager service unavailable for network suggestions")
            return false
        }

        return try {
            val suggestions = buildSuggestions()
            if (suggestions.isEmpty()) return false

            if (forceRefresh) {
                try {
                    wm.removeNetworkSuggestions(suggestions)
                    AppLogger.wifi(TAG, "Force-refresh: purged prior network suggestions to reset AOSP blocklist/disabled state")
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Failed to purge old suggestions during force-refresh: ${e.message}")
                }
            }

            val status = wm.addNetworkSuggestions(suggestions)

            val statusStr = when (status) {
                WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS -> "SUCCESS (0)"
                WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_DUPLICATE -> "ALREADY_REGISTERED_DUPLICATE"
                WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_INVALID -> "ERROR_ADD_INVALID"
                WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_NOT_ALLOWED -> "ERROR_ADD_NOT_ALLOWED"
                WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_EXCEEDS_MAX_PER_APP -> "ERROR_EXCEEDS_MAX"
                else -> "STATUS ($status)"
            }

            val success = status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS ||
                    status == WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_DUPLICATE

            if (success) {
                isRegistered = true
                AppLogger.wifi(TAG, "Campus Wi-Fi suggestions registered (${suggestions.size} profiles): $statusStr")
            } else {
                AppLogger.w(TAG, "Failed to register campus Wi-Fi suggestions: $statusStr")
            }
            success
        } catch (e: Exception) {
            AppLogger.e(TAG, "Exception registering campus Wi-Fi suggestions: ${e.message}", e)
            false
        }
    }

    fun forceRefreshSuggestions(context: Context): Boolean {
        return ensureSuggestionRegistered(context, forceRefresh = true)
    }

    fun removeSuggestions(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return false
        return try {
            val suggestions = buildSuggestions()
            val status = wm.removeNetworkSuggestions(suggestions)
            isRegistered = false
            AppLogger.wifi(TAG, "Removed campus Wi-Fi suggestions (status=$status)")
            status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error removing campus Wi-Fi suggestions: ${e.message}")
            false
        }
    }

    fun isSuggestionActive(): Boolean = isRegistered
}
