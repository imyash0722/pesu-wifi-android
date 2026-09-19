package com.imyash.pesuwifi.service

import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
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
 * 3. Fallback persistent JobScheduler for wakeup on network availability
 *
 * Rules are always active to guarantee immediate zero-latency campus association.
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
     * - Fallback persistent JobScheduler on network connect
     */
    fun registerAllRules(context: Context) {
        val appContext = context.applicationContext
        WifiSuggestionManager.ensureSuggestionRegistered(appContext)
        registerSystemNetworkCallback(appContext)
        scheduleWakeupJob(appContext)
    }

    private fun getPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, WifiWakeupReceiver::class.java).apply {
            action = WifiWakeupReceiver.ACTION_NETWORK_CALLBACK
            `package` = context.packageName
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
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

            val pendingIntent = getPendingIntent(context)
            cm.registerNetworkCallback(request, pendingIntent)
            isCallbackRegistered = true
            AppLogger.i(TAG, "Registered system-persistent NetworkCallback PendingIntent with ConnectivityManager")
        } catch (e: Exception) {
            AppLogger.w(TAG, "Could not register system NetworkCallback PendingIntent: ${e.message}")
        }
    }

    /**
     * Schedules a persistent Job via JobScheduler.
     * Guaranteed OS-level wake even if app process was killed or in background.
     */
    fun scheduleWakeupJob(context: Context) {
        try {
            val js = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            val componentName = ComponentName(context, WifiWakeupJobService::class.java)
            val builder = JobInfo.Builder(WifiWakeupJobService.JOB_ID, componentName)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)

            val result = js.schedule(builder.build())
            AppLogger.d(TAG, "Scheduled persistent WifiWakeupJob (result=$result)")
        } catch (e: Exception) {
            AppLogger.w(TAG, "Could not schedule persistent WifiWakeupJob: ${e.message}")
        }
    }
}
