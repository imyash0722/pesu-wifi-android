package com.imyash.pesuwifi.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.imyash.pesuwifi.data.AccountRepository
import com.imyash.pesuwifi.data.PortalApi
import com.imyash.pesuwifi.data.PortalRepository
import com.imyash.pesuwifi.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Ultra-lightweight heartbeat scheduler for maintaining Cyberoam / Sophos captive portal sessions.
 *
 * Cyberoam firewall terminates active client sessions if a heartbeat (mode=192) is not received
 * within 180 seconds (liveReqTimeInJS=180).
 *
 * This scheduler uses a dual-layer strategy:
 * 1. In-process Coroutine ticker: Ticks every 150 seconds while app process is alive.
 * 2. AlarmManager setAndAllowWhileIdle: Wakes the device every 150 seconds even during deep sleep / Doze.
 *
 * Completely non-intrusive:
 * - No persistent foreground service notification.
 * - Instantly stops when disconnected from campus Wi-Fi (zero battery usage off-campus).
 * - Automatic re-authentication if the portal session drops.
 */
object CampusHeartbeatScheduler {
    private const val TAG = "CampusHeartbeat"
    const val ACTION_HEARTBEAT = "com.imyash.pesuwifi.ACTION_CAMPUS_HEARTBEAT"
    const val HEARTBEAT_INTERVAL_MS = 150_000L // 150 seconds (2.5 minutes, safely under 180s timeout)
    private const val REQUEST_CODE = 9984

    @Volatile
    var lastHeartbeatSuccessTime = 0L

    @Volatile
    private var coroutineHeartbeatJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun startHeartbeat(context: Context) {
        val appContext = context.applicationContext
        val portalRepo = PortalRepository.getInstance(appContext)
        if (!portalRepo.isCampusNetwork()) {
            AppLogger.d(TAG, "startHeartbeat: Not on campus network, skipping.")
            return
        }

        // 1. Arm OS AlarmManager (works across Doze mode and screen-off)
        scheduleNextHeartbeatAlarm(appContext)

        // 2. Start in-process coroutine ticker
        synchronized(this) {
            if (coroutineHeartbeatJob?.isActive != true) {
                coroutineHeartbeatJob = scope.launch {
                    AppLogger.i(TAG, "In-process coroutine heartbeat started (150s interval)")
                    while (isActive) {
                        delay(HEARTBEAT_INTERVAL_MS)
                        val shouldContinue = performHeartbeatTick(appContext)
                        if (!shouldContinue) break
                    }
                    coroutineHeartbeatJob = null
                }
            }
        }
    }

    fun stopHeartbeat(context: Context) {
        val appContext = context.applicationContext
        cancelHeartbeatAlarm(appContext)
        synchronized(this) {
            coroutineHeartbeatJob?.cancel()
            coroutineHeartbeatJob = null
        }
        AppLogger.i(TAG, "Campus heartbeat stopped.")
    }

    fun scheduleNextHeartbeatAlarm(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val triggerAt = SystemClock.elapsedRealtime() + HEARTBEAT_INTERVAL_MS
            val pendingIntent = getPendingIntent(context)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            }
            AppLogger.d(TAG, "Scheduled next heartbeat alarm in ${HEARTBEAT_INTERVAL_MS / 1000}s")
        } catch (e: Exception) {
            AppLogger.w(TAG, "Could not schedule heartbeat alarm: ${e.message}")
        }
    }

    fun cancelHeartbeatAlarm(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val pendingIntent = getPendingIntent(context)
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            AppLogger.d(TAG, "Cancelled heartbeat alarm.")
        } catch (e: Exception) {
            AppLogger.w(TAG, "Could not cancel heartbeat alarm: ${e.message}")
        }
    }

    private fun getPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, CampusHeartbeatReceiver::class.java).apply {
            action = ACTION_HEARTBEAT
            `package` = context.packageName
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }

    suspend fun performHeartbeatTick(context: Context): Boolean {
        val portalRepo = PortalRepository.getInstance(context)
        val accountRepo = AccountRepository.getInstance(context)

        // Verify still connected to campus Wi-Fi
        if (!portalRepo.isCampusNetwork()) {
            AppLogger.i(TAG, "Heartbeat tick: No longer on campus network. Stopping heartbeat.")
            stopHeartbeat(context)
            return false
        }

        val activeUser = accountRepo.getActiveUser()
        if (activeUser.isNullOrBlank()) {
            AppLogger.w(TAG, "Heartbeat tick: No active user configured.")
            stopHeartbeat(context)
            return false
        }

        val now = SystemClock.elapsedRealtime()
        // Deduplicate if a heartbeat succeeded within the last 60 seconds
        if (lastHeartbeatSuccessTime > 0L && (now - lastHeartbeatSuccessTime) < 60_000L) {
            AppLogger.d(TAG, "Heartbeat tick: Recent heartbeat succeeded ${(now - lastHeartbeatSuccessTime) / 1000}s ago. Skipping redundant probe.")
            scheduleNextHeartbeatAlarm(context)
            return true
        }

        AppLogger.d(TAG, "Sending Cyberoam keepalive heartbeat (mode=192) for $activeUser...")
        val isLive = PortalApi.checkLive(activeUser)
        if (isLive) {
            lastHeartbeatSuccessTime = SystemClock.elapsedRealtime()
            AppLogger.i(TAG, "Heartbeat ACK received from Cyberoam portal for $activeUser")
            scheduleNextHeartbeatAlarm(context)
            return true
        } else {
            AppLogger.w(TAG, "Heartbeat: Cyberoam session inactive/expired! Auto-reauthenticating for $activeUser...")
            val result = portalRepo.login(activeUser)
            if (result.isSuccess) {
                lastHeartbeatSuccessTime = SystemClock.elapsedRealtime()
                AppLogger.i(TAG, "Heartbeat auto-reauthentication SUCCESS for $activeUser!")
                scheduleNextHeartbeatAlarm(context)
                return true
            } else {
                AppLogger.w(TAG, "Heartbeat auto-reauthentication failed: ${result.exceptionOrNull()?.message}")
                scheduleNextHeartbeatAlarm(context) // Retry on next cycle
                return false
            }
        }
    }
}
