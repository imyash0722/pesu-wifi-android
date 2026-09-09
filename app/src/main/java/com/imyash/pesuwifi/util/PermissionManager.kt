package com.imyash.pesuwifi.util

import android.Manifest
import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

data class PermissionState(
    val isNotificationGranted: Boolean = true,
    val isBatteryOptimizationIgnored: Boolean = true,
    val canScheduleExactAlarms: Boolean = true,
    val hasAutostartSettings: Boolean = false
) {
    val allEssentialGranted: Boolean
        get() = isNotificationGranted && isBatteryOptimizationIgnored && canScheduleExactAlarms

    val missingCount: Int
        get() {
            var count = 0
            if (!isNotificationGranted) count++
            if (!isBatteryOptimizationIgnored) count++
            if (!canScheduleExactAlarms) count++
            return count
        }
}

object PermissionManager {

    fun getPermissionState(context: Context): PermissionState {
        return PermissionState(
            isNotificationGranted = isNotificationPermissionGranted(context),
            isBatteryOptimizationIgnored = isBatteryOptimizationIgnored(context),
            canScheduleExactAlarms = canScheduleExactAlarms(context),
            hasAutostartSettings = hasAutostartSettings(context)
        )
    }

    fun isNotificationPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true
        } else {
            true
        }
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            am?.canScheduleExactAlarms() ?: true
        } else {
            true
        }
    }

    fun getBatteryOptimizationIntent(packageName: String): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }
    }

    fun getExactAlarmIntent(packageName: String): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:$packageName")
            }
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }
    }

    fun getAutostartIntent(context: Context): Intent? {
        val candidates = listOf(
            // Xiaomi / Redmi / POCO (MIUI / HyperOS)
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            Intent().setComponent(ComponentName("com.miui.securityadd", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            // Huawei / Honor (EMUI / MagicOS)
            Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")),
            Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
            // Oppo / Realme (ColorOS)
            Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
            Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")),
            Intent().setComponent(ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")),
            // Vivo / iQOO (FuntouchOS / OriginOS)
            Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
            Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
            // Samsung (OneUI)
            Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")),
            Intent().setComponent(ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity")),
            // Transsion (Infinix / Tecno / itel)
            Intent().setComponent(ComponentName("com.transsion.phonemanager", "com.transsion.phonemanager.settings.AutoRunManageActivity")),
            // Asus
            Intent().setComponent(ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity")),
            // LeEco / Lenovo
            Intent().setComponent(ComponentName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity")),
            Intent().setComponent(ComponentName("com.lenovo.security", "com.lenovo.security.purebackground.PureBackgroundActivity"))
        )

        val pm = context.packageManager
        for (intent in candidates) {
            try {
                if (intent.resolveActivity(pm) != null) {
                    return intent
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
        return null
    }

    fun hasAutostartSettings(context: Context): Boolean {
        return getAutostartIntent(context) != null
    }

    fun getAppSettingsIntent(packageName: String): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
    }
}
