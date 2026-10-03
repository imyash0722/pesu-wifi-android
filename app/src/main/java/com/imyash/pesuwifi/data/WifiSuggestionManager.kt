package com.imyash.pesuwifi.data

import android.content.Context
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.imyash.pesuwifi.util.AppLogger

object WifiSuggestionManager {
    private const val TAG = "WifiSuggestionManager"

    data class SuggestionTarget(
        val ssid: String,
        val passphrase: String? = null
    )

    data class BestApTarget(
        val ssid: String,
        val bssid: String,
        val level: Int,
        val frequency: Int
    )

    val CAMPUS_TARGETS = listOf(
        SuggestionTarget("PESU-EC-Campus", "PESU-EC-Campus"),
        SuggestionTarget("PESU-EC-Campus", null),
        SuggestionTarget("PESU-RR-Campus", "PESU-RR-Campus"),
        SuggestionTarget("PESU-RR-Campus", null),
        SuggestionTarget("PESU-CIE", "PESU-CIE"),
        SuggestionTarget("PESU-CIE", null),
        SuggestionTarget("AMAATRA-HOSTEL", "SouthPe$!t"),
        SuggestionTarget("AMAATRA-HOSTEL", null),
        SuggestionTarget("Foodcourt", "PESU-EC-Campus"),
        SuggestionTarget("Foodcourt", null),
        SuggestionTarget("pes south cafe", "PESU-EC-Campus"),
        SuggestionTarget("pes south cafe", null)
    )

    @Volatile
    private var isRegistered = false

    private fun buildSuggestions(
        prioritySsid: String? = null,
        priorityBssid: String? = null
    ): List<WifiNetworkSuggestion> {
        val sortedTargets = if (!prioritySsid.isNullOrBlank()) {
            CAMPUS_TARGETS.sortedByDescending { it.ssid.equals(prioritySsid, ignoreCase = true) }
        } else {
            CAMPUS_TARGETS
        }

        return sortedTargets.mapNotNull { target ->
            try {
                val isPriority = !prioritySsid.isNullOrBlank() && target.ssid.equals(prioritySsid, ignoreCase = true)
                val builder = WifiNetworkSuggestion.Builder()
                    .setSsid(target.ssid)
                    .setIsAppInteractionRequired(false)
                    .setIsUserInteractionRequired(false)

                if (target.passphrase != null) {
                    builder.setWpa2Passphrase(target.passphrase)
                }

                if (isPriority && !priorityBssid.isNullOrBlank() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        builder.setBssid(android.net.MacAddress.fromString(priorityBssid))
                    } catch (e: Exception) {
                        AppLogger.w(TAG, "Invalid BSSID for suggestion '$priorityBssid': ${e.message}")
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    builder.setPriority(if (isPriority) 1000 else 500)
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

    fun prioritizeTarget(context: Context, ssid: String, bssid: String? = null): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return false
        return try {
            val suggestions = buildSuggestions(prioritySsid = ssid, priorityBssid = bssid)
            if (suggestions.isEmpty()) return false
            try {
                wm.removeNetworkSuggestions(buildSuggestions())
            } catch (_: Exception) {}
            val status = wm.addNetworkSuggestions(suggestions)
            val success = status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS
            if (success) {
                isRegistered = true
                AppLogger.wifi(TAG, "Prioritized Wi-Fi suggestion for '$ssid' (BSSID=$bssid): SUCCESS")
            } else {
                AppLogger.w(TAG, "Failed prioritizing Wi-Fi suggestion for '$ssid': status $status")
            }
            success
        } catch (e: Exception) {
            AppLogger.w(TAG, "Exception prioritizing target: ${e.message}")
            false
        }
    }

    fun findStrongestCampusAp(context: Context): BestApTarget? {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        return try {
            @Suppress("DEPRECATION")
            val scanList = wm.scanResults ?: return null
            scanList
                .filter { ap ->
                    val cleanSsid = ap.SSID?.replace("\"", "")?.trim() ?: ""
                    CAMPUS_TARGETS.any { it.ssid.equals(cleanSsid, ignoreCase = true) }
                }
                .maxByOrNull { it.level }
                ?.let { BestApTarget(it.SSID.replace("\"", "").trim(), it.BSSID, it.level, it.frequency) }
        } catch (e: Exception) {
            AppLogger.w(TAG, "findStrongestCampusAp failed: ${e.message}")
            null
        }
    }

    fun requestEnableWifi(context: Context): Boolean {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wm?.isWifiEnabled == true) return true

        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            try {
                wm?.setWifiEnabled(true) ?: false
            } catch (e: Exception) {
                false
            }
        } else {
            try {
                val intent = Intent(Settings.Panel.ACTION_WIFI).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                false
            } catch (e: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {}
                false
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
