package com.imyash.pesuwifi.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.imyash.pesuwifi.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver for periodic keepalive alarms.
 * On Android 12+ (API 31+), waking background services via PendingIntent.getService()
 * throws ForegroundServiceStartNotAllowedException when the screen is off.
 * Routing exact alarms through a BroadcastReceiver with goAsync() and a short PARTIAL_WAKE_LOCK
 * guarantees the heartbeat check executes reliably while preserving Doze compatibility.
 */
class KeepaliveAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != WifiKeepaliveService.ACTION_HEARTBEAT) return

        AppLogger.d(TAG, "KeepaliveAlarmReceiver: received ACTION_HEARTBEAT alarm")

        // Acquire a short partial wake lock to guarantee CPU stays awake during network I/O
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = pm?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "PesuWifi:AlarmReceiverWakeLock"
        )?.apply {
            setReferenceCounted(false)
            acquire(15_000L) // Safety auto-release after 15s
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WifiKeepaliveService.triggerCheckFromAlarm(context)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error executing alarm keepalive check: ${e.message}", e)
            } finally {
                try {
                    wakeLock?.let {
                        if (it.isHeld) it.release()
                    }
                } catch (e: Exception) {
                    // Ignore
                }
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "KeepaliveAlarmReceiver"
    }
}
