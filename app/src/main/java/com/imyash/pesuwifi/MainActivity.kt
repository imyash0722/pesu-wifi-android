package com.imyash.pesuwifi

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.viewModels
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.imyash.pesuwifi.service.SystemRuleManager
import com.imyash.pesuwifi.ui.AccountsScreen
import com.imyash.pesuwifi.ui.HomeScreen
import com.imyash.pesuwifi.ui.LogsScreen
import com.imyash.pesuwifi.ui.theme.PesuWifiTheme
import com.imyash.pesuwifi.util.PermissionManager
import com.imyash.pesuwifi.viewmodel.PortalViewModel

enum class Screen {
    HOME,
    ACCOUNTS,
    LOGS
}

class MainActivity : ComponentActivity() {

    private val viewModel: PortalViewModel by viewModels()

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            viewModel.refreshPermissions()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SystemRuleManager.registerAllRules(this)

        if (PermissionManager.hasSeenFirstLaunchPrompt(this)) {
            checkNotificationPermission()
        }

        setContent {
            PesuWifiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentScreen by rememberSaveable { mutableStateOf(Screen.HOME) }

                    // Intercept back navigation when on secondary screens (ACCOUNTS, LOGS)
                    // When on HOME, this handler is disabled so the system back cleanly exits/minimizes the app
                    BackHandler(enabled = currentScreen != Screen.HOME) {
                        currentScreen = Screen.HOME
                    }

                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "screen_transition"
                    ) { screen ->
                        when (screen) {
                            Screen.HOME -> HomeScreen(
                                viewModel = viewModel,
                                onNavigateToAccounts = { currentScreen = Screen.ACCOUNTS },
                                onNavigateToLogs = { currentScreen = Screen.LOGS },
                                onRequestNotification = { requestNotificationPermission() },
                                onRequestBatteryOptimization = { requestBatteryOptimization() },
                                onRequestExactAlarm = { requestExactAlarm() },
                                onRequestAutostart = { requestAutostart() }
                            )
                            Screen.ACCOUNTS -> AccountsScreen(
                                viewModel = viewModel,
                                onNavigateBack = { currentScreen = Screen.HOME }
                            )
                            Screen.LOGS -> LogsScreen(
                                onNavigateBack = { currentScreen = Screen.HOME }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissions()
    }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun requestBatteryOptimization() {
        try {
            startActivity(PermissionManager.getBatteryOptimizationIntent(packageName))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {
                startActivity(PermissionManager.getAppSettingsIntent(packageName))
            }
        }
    }

    fun requestExactAlarm() {
        try {
            startActivity(PermissionManager.getExactAlarmIntent(packageName))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: Exception) {}
        }
    }

    fun requestAutostart() {
        try {
            val intent = PermissionManager.getAutostartIntent(this)
            if (intent != null) {
                startActivity(intent)
            } else {
                startActivity(PermissionManager.getAppSettingsIntent(packageName))
            }
        } catch (e: Exception) {
            try {
                startActivity(PermissionManager.getAppSettingsIntent(packageName))
            } catch (_: Exception) {}
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
