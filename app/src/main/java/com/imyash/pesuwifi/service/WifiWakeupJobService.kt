package com.imyash.pesuwifi.service

import android.app.job.JobParameters
import android.app.job.JobService
import com.imyash.pesuwifi.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Fallback persistent JobService scheduled via JobScheduler with NETWORK_TYPE_ANY.
 * Acts as an OS-level guarantee that even if the app process has been terminated,
 * network connectivity events will awaken the process, re-verify system rules,
 * and auto-authenticate on campus Wi-Fi networks.
 */
class WifiWakeupJobService : JobService() {
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onStartJob(params: JobParameters?): Boolean {
        AppLogger.i(TAG, "OS JobScheduler awakened WifiWakeupJobService (jobId=${params?.jobId})")
        serviceScope.launch {
            try {
                // Ensure all system rules (suggestions, network callback) are active
                SystemRuleManager.registerAllRules(applicationContext)
                // Execute network verification & auto-login
                WifiWakeupReceiver.handleNetworkEvent(applicationContext, null)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error in WifiWakeupJobService: ${e.message}", e)
            } finally {
                // Re-arm Job for next network state transition
                SystemRuleManager.scheduleWakeupJob(applicationContext)
                jobFinished(params, false)
            }
        }
        return true // Asynchronous execution
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        AppLogger.d(TAG, "WifiWakeupJobService stopped by OS")
        serviceScope.cancel()
        return true // Reschedule if cancelled prematurely
    }

    companion object {
        private const val TAG = "WifiWakeupJobService"
        const val JOB_ID = 9982
    }
}
