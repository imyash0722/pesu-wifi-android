package com.imyash.pesuwifi.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("pesu_wifi_settings", Context.MODE_PRIVATE)
            val autostart = prefs.getBoolean("autostart_on_boot", true)
            if (autostart) {
                WifiKeepaliveService.start(context)
            }
        }
    }
}
