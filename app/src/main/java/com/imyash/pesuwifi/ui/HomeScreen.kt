package com.imyash.pesuwifi.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.imyash.pesuwifi.BuildConfig
import com.imyash.pesuwifi.ui.components.AccountDialog
import com.imyash.pesuwifi.ui.components.FirstLaunchPermissionsDialog
import com.imyash.pesuwifi.ui.components.PermissionsCard
import com.imyash.pesuwifi.ui.components.PermissionsRequiredDialog
import com.imyash.pesuwifi.ui.theme.StatusAmber
import com.imyash.pesuwifi.ui.theme.StatusGreen
import com.imyash.pesuwifi.util.PermissionManager
import com.imyash.pesuwifi.viewmodel.PortalViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: PortalViewModel,
    onNavigateToAccounts: () -> Unit,
    onNavigateToLogs: () -> Unit = {},
    onRequestNotification: () -> Unit = {},
    onRequestBatteryOptimization: () -> Unit = {},
    onRequestExactAlarm: () -> Unit = {},
    onRequestAutostart: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var showAddAccountDialog by remember { mutableStateOf(false) }
    var showPermissionsDialog by remember { mutableStateOf(false) }
    var showFirstLaunchDialog by remember {
        mutableStateOf(
            !PermissionManager.hasSeenFirstLaunchPrompt(context) &&
                !state.permissionState.allEssentialGranted
        )
    }

    LaunchedEffect(Unit) {
        if (!PermissionManager.hasSeenFirstLaunchPrompt(context) && state.permissionState.allEssentialGranted) {
            PermissionManager.setSeenFirstLaunchPrompt(context, true)
        }
    }

    LaunchedEffect(state.userMessage) {
        state.userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar("Error: $it")
            viewModel.clearMessages()
        }
    }

    // Pulse animation for WiFi icon when connected
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )

    val isConnected = state.status.isWifiConnected && state.status.isPesuWifi
    val isLoggedIn = state.status.isLoggedIn

    // Status color & text
    val statusColor = when {
        isLoggedIn -> StatusGreen
        isConnected -> StatusAmber
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusTitle = when {
        !state.status.isWifiConnected -> "Wi-Fi Disconnected"
        !state.status.isPesuWifi -> "External Wi-Fi"
        !state.status.isPortalOnline -> "Portal Unreachable"
        isLoggedIn -> "Session Active"
        else -> "Session Inactive"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "PESU WiFi",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refresh() },
                        enabled = !state.isLoading
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                    IconButton(onClick = onNavigateToAccounts) {
                        Icon(imageVector = Icons.Default.ManageAccounts, contentDescription = "Manage Accounts")
                    }
                    IconButton(onClick = onNavigateToLogs) {
                        Icon(imageVector = Icons.Default.Description, contentDescription = "Diagnostics & Logs")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // ── Hero: Pulsing WiFi icon + status ──────────────────────────
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(120.dp)
            ) {
                // Outer pulse ring (only when connected)
                if (isConnected) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .graphicsLayer {
                                scaleX = pulseScale
                                scaleY = pulseScale
                                alpha = pulseAlpha
                            }
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                }
                // Inner tonal container
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(
                            if (isConnected)
                                statusColor.copy(alpha = 0.12f)
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                ) {
                    Icon(
                        imageVector = if (state.status.isWifiConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = if (isConnected) statusColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Status headline
            Text(
                text = statusTitle,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Status message sub-text
            Text(
                text = state.status.statusMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Status pill chip
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50.dp))
                    .background(statusColor.copy(alpha = 0.12f))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when {
                        isLoggedIn -> "Authenticated"
                        isConnected -> "Connected, not logged in"
                        else -> "Disconnected"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── Active account chip ────────────────────────────────────────
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f))
                    .clickable {
                        if (state.accounts.isNotEmpty()) onNavigateToAccounts()
                        else showAddAccountDialog = true
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = state.activeUser ?: "Add account",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (state.accounts.isNotEmpty()) Icons.Default.ChevronRight else Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Primary action button ─────────────────────────────────────
            Button(
                onClick = {
                    if (isLoggedIn) viewModel.logout() else viewModel.login()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLoggedIn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                ),
                enabled = !state.isLoading && state.accounts.isNotEmpty()
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Icon(
                        imageVector = if (isLoggedIn) Icons.AutoMirrored.Filled.Logout else Icons.AutoMirrored.Filled.Login,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isLoggedIn) "Disconnect" else "Connect",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Latency indicator below button
            if (state.status.latencyMs != null && isLoggedIn) {
                Text(
                    text = "Latency: ${state.status.latencyMs} ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── Daemon list tile (no card, minimal) ───────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                        tint = if (state.isDaemonRunning) StatusGreen else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Background Keepalive",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (state.isDaemonRunning) "Auto-reconnects every 60 s" else "Disabled",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = state.isDaemonRunning,
                    onCheckedChange = {
                        if (!state.isDaemonRunning && !state.permissionState.allEssentialGranted) {
                            showPermissionsDialog = true
                        } else {
                            viewModel.toggleDaemon()
                        }
                    }
                )
            }

            // Tester build only: persistent PermissionsCard for easy debug access
            if (BuildConfig.SHOW_PERMISSIONS_SECTION &&
                (!state.permissionState.allEssentialGranted || state.permissionState.hasAutostartSettings)) {
                Spacer(modifier = Modifier.height(16.dp))
                PermissionsCard(
                    permissionState = state.permissionState,
                    onRequestNotification = onRequestNotification,
                    onRequestBatteryOptimization = onRequestBatteryOptimization,
                    onRequestExactAlarm = onRequestExactAlarm,
                    onRequestAutostart = onRequestAutostart
                )
            }

            Spacer(modifier = Modifier.height(48.dp))
        }

        if (showPermissionsDialog) {
            PermissionsRequiredDialog(
                permissionState = state.permissionState,
                onRequestNotification = onRequestNotification,
                onRequestBatteryOptimization = onRequestBatteryOptimization,
                onRequestExactAlarm = onRequestExactAlarm,
                onStartAnyway = {
                    showPermissionsDialog = false
                    viewModel.toggleDaemon()
                },
                onDismiss = { showPermissionsDialog = false }
            )
        }

        if (showAddAccountDialog) {
            AccountDialog(
                onDismiss = { showAddAccountDialog = false },
                onSave = { u, p ->
                    viewModel.saveAccount(u, p)
                    showAddAccountDialog = false
                }
            )
        }

        if (showFirstLaunchDialog) {
            FirstLaunchPermissionsDialog(
                permissionState = state.permissionState,
                onRequestNotification = onRequestNotification,
                onRequestBatteryOptimization = onRequestBatteryOptimization,
                onRequestExactAlarm = onRequestExactAlarm,
                onGrantAll = {
                    PermissionManager.setSeenFirstLaunchPrompt(context, true)
                    if (state.permissionState.allEssentialGranted) {
                        showFirstLaunchDialog = false
                    } else {
                        when {
                            !state.permissionState.isBatteryOptimizationIgnored -> onRequestBatteryOptimization()
                            !state.permissionState.isNotificationGranted -> onRequestNotification()
                            !state.permissionState.canScheduleExactAlarms -> onRequestExactAlarm()
                            else -> showFirstLaunchDialog = false
                        }
                    }
                },
                onDismiss = {
                    PermissionManager.setSeenFirstLaunchPrompt(context, true)
                    showFirstLaunchDialog = false
                }
            )
        }
    }
}
