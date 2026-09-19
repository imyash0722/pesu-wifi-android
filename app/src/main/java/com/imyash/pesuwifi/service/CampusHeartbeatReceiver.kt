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
 * Lightweight BroadcastReceiver awakened every 150 seconds by AlarmManager while on campus Wi-Fi.
 * Holds a brief 10s partial wake lock to guarantee CPU stays awake during the ~50ms HTTP keepalive exchange.
 * Completely passive when not on campus.
 */
class CampusHeartbeatReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != CampusHeartbeatScheduler.ACTION_HEARTBEAT) return

        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = pm?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "PesuWifi:CampusHeartbeatLock"
        )?.apply {
            setReferenceCounted(false)
            acquire(10_000L) // 10-second max safety timeout
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                CampusHeartbeatScheduler.performHeartbeatTick(context)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error during heartbeat tick: ${e.message}", e)
            } finally {
                try {
                    wakeLock?.let { if (it.isHeld) it.release() }
                } catch (_: Exception) {}
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "CampusHeartbeatReceiver"
    }
}
