package com.imyash.pesuwifi.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import com.imyash.pesuwifi.data.WifiSuggestionManager
import com.imyash.pesuwifi.util.AppLogger

/**
 * Manages the registration of OS-level rules with Android system services:
 * 1. WifiNetworkSuggestion with WifiManager for campus SSIDs
 * 2. System-persistent NetworkCallback with PendingIntent via ConnectivityManager
 */
object SystemRuleManager {
    private const val TAG = "SystemRuleManager"
    private const val REQUEST_CODE = 9981

    @Volatile
    private var isCallbackRegistered = false

    /**
     * Registers all OS-level system rules:
     * - Campus SSID suggestions to WifiManager
     * - NetworkCallback PendingIntent to ConnectivityManager
     */
    fun registerAllRules(context: Context) {
        val appContext = context.applicationContext
        WifiSuggestionManager.ensureSuggestionRegistered(appContext)
        registerSystemNetworkCallback(appContext)
    }

    /**
     * Hands a PendingIntent to ConnectivityManager.
     * The OS system_server maintains this registration and awakens WifiWakeupReceiver
     * even if the app process has been killed or device is sleeping.
     */
    fun registerSystemNetworkCallback(context: Context) {
        if (isCallbackRegistered) return
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()

            val intent = Intent(context, WifiWakeupReceiver::class.java).apply {
                action = WifiWakeupReceiver.ACTION_NETWORK_CALLBACK
                `package` = context.packageName
            }

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pendingIntent = PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
            cm.registerNetworkCallback(request, pendingIntent)
            isCallbackRegistered = true
            AppLogger.i(TAG, "Registered system-persistent NetworkCallback PendingIntent with ConnectivityManager")
        } catch (e: Exception) {
            AppLogger.w(TAG, "Could not register system NetworkCallback PendingIntent: ${e.message}")
        }
    }
}
