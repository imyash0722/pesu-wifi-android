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

    fun ensureSuggestionRegistered(context: Context): Boolean {
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
            val suggestions = CAMPUS_TARGETS.mapNotNull { target ->
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
                    builder.build()
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Error building suggestion for ${target.ssid}: ${e.message}")
                    null
                }
            }

            val status = wm.addNetworkSuggestions(suggestions)
            val statusStr = when (status) {
                WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS -> "SUCCESS"
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

    fun removeSuggestions(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return false
        return try {
            val suggestions = CAMPUS_TARGETS.mapNotNull { target ->
                try {
                    val builder = WifiNetworkSuggestion.Builder().setSsid(target.ssid)
                    if (target.passphrase != null) builder.setWpa2Passphrase(target.passphrase)
                    builder.build()
                } catch (_: Exception) {
                    null
                }
            }
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
