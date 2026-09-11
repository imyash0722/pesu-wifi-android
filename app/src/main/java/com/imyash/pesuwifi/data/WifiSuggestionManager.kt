package com.imyash.pesuwifi.data

import android.content.Context
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import com.imyash.pesuwifi.util.AppLogger

object WifiSuggestionManager {
    private const val TAG = "WifiSuggestionManager"
    const val CAMPUS_SSID = "PESU-EC-Campus"
    const val CAMPUS_PASSPHRASE = "PESU-EC-Campus"

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
            val suggestionBuilder = WifiNetworkSuggestion.Builder()
                .setSsid(CAMPUS_SSID)
                .setWpa2Passphrase(CAMPUS_PASSPHRASE)
                .setIsAppInteractionRequired(false) // Auto-connects in background without app interaction
                .setIsUserInteractionRequired(false) // Do not prompt user

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                suggestionBuilder.setPriority(1000) // Top priority on Android 11+
                suggestionBuilder.setIsInitialAutojoinEnabled(true)
            }

            val suggestion = suggestionBuilder.build()
            val status = wm.addNetworkSuggestions(listOf(suggestion))

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
                AppLogger.wifi(TAG, "Campus Wi-Fi suggestion active for '$CAMPUS_SSID': $statusStr")
            } else {
                AppLogger.w(TAG, "Failed to register campus Wi-Fi suggestion: $statusStr")
            }
            success
        } catch (e: Exception) {
            AppLogger.e(TAG, "Exception registering campus Wi-Fi suggestion: ${e.message}", e)
            false
        }
    }

    fun removeSuggestions(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return false
        return try {
            val suggestion = WifiNetworkSuggestion.Builder()
                .setSsid(CAMPUS_SSID)
                .setWpa2Passphrase(CAMPUS_PASSPHRASE)
                .build()
            val status = wm.removeNetworkSuggestions(listOf(suggestion))
            isRegistered = false
            AppLogger.wifi(TAG, "Removed campus Wi-Fi suggestion for '$CAMPUS_SSID' (status=$status)")
            status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error removing campus Wi-Fi suggestion: ${e.message}")
            false
        }
    }

    fun isSuggestionActive(): Boolean = isRegistered
}
