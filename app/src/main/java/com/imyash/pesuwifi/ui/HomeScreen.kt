package com.imyash.pesuwifi.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.imyash.pesuwifi.ui.components.AccountDialog
import com.imyash.pesuwifi.ui.components.FirstLaunchPermissionsDialog
import com.imyash.pesuwifi.ui.components.PermissionsCard
import com.imyash.pesuwifi.ui.components.PermissionsRequiredDialog
import com.imyash.pesuwifi.ui.components.StatusCard
import com.imyash.pesuwifi.ui.theme.StatusAmber
import com.imyash.pesuwifi.ui.theme.StatusGreen
import com.imyash.pesuwifi.util.PermissionManager
import com.imyash.pesuwifi.viewmodel.PortalViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: PortalViewModel,
    onNavigateToAccounts: () -> Unit,
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "PESU WiFi",
                        fontWeight = FontWeight.Bold
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
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Status Card
            StatusCard(
                status = state.status,
                isDaemonRunning = state.isDaemonRunning
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Active Account Bar
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Active Account",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = state.activeUser ?: "No account selected",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (state.accounts.isNotEmpty()) {
                        OutlinedButton(
                            onClick = onNavigateToAccounts,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Switch")
                        }
                    } else {
                        Button(
                            onClick = { showAddAccountDialog = true },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("+ Add")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Primary Login / Logout Action Button
            val isLoggedIn = state.status.isLoggedIn
            Button(
                onClick = {
                    if (isLoggedIn) {
                        viewModel.logout()
                    } else {
                        viewModel.login()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLoggedIn) StatusAmber else StatusGreen
                ),
                enabled = !state.isLoading && state.accounts.isNotEmpty()
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Icon(
                        imageVector = if (isLoggedIn) Icons.Default.Logout else Icons.Default.Login,
                        contentDescription = null,
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isLoggedIn) "DISCONNECT (LOGOUT)" else "CONNECT (LOGIN)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Daemon Control Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
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
                            modifier = Modifier.size(28.dp),
                            tint = if (state.isDaemonRunning) StatusGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Background Keepalive",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (state.isDaemonRunning) "Polls every 60s & auto-reconnects" else "Disabled",
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
            }

            // Permissions Card (shows when any background permission needs setup)
            if (!state.permissionState.allEssentialGranted || state.permissionState.hasAutostartSettings) {
                Spacer(modifier = Modifier.height(16.dp))
                PermissionsCard(
                    permissionState = state.permissionState,
                    onRequestNotification = onRequestNotification,
                    onRequestBatteryOptimization = onRequestBatteryOptimization,
                    onRequestExactAlarm = onRequestExactAlarm,
                    onRequestAutostart = onRequestAutostart
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
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
