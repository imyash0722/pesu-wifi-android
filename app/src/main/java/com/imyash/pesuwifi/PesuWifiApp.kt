package com.imyash.pesuwifi

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

import com.imyash.pesuwifi.util.AppLogger

class PesuWifiApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_KEEPALIVE_ID,
                getString(R.string.channel_keepalive_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_keepalive_desc)
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
            }

            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_KEEPALIVE_ID = "pesu_wifi_keepalive_channel"
        const val NOTIFICATION_ID = 1001
    }
}
