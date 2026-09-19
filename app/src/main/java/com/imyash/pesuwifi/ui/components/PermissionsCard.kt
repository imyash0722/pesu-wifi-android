package com.imyash.pesuwifi.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.imyash.pesuwifi.ui.theme.StatusAmber
import com.imyash.pesuwifi.ui.theme.StatusGreen
import com.imyash.pesuwifi.util.PermissionState

@Composable
fun PermissionsCard(
    permissionState: PermissionState,
    onRequestNotification: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onRequestExactAlarm: () -> Unit,
    onRequestAutostart: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (permissionState.allEssentialGranted && !permissionState.hasAutostartSettings) {
        return
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = if (permissionState.allEssentialGranted) StatusGreen else StatusAmber,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Background Permissions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (permissionState.allEssentialGranted) {
                            "All essential permissions granted"
                        } else {
                            "${permissionState.missingCount} permission(s) needed for screen-off keepalive"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Notification Permission Item
            PermissionItemRow(
                icon = Icons.Default.Notifications,
                title = "Notifications",
                description = "Shows live connection status in notification shade",
                isGranted = permissionState.isNotificationGranted,
                buttonText = "Allow",
                onActionClick = onRequestNotification
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Battery Optimization Item
            PermissionItemRow(
                icon = Icons.Default.BatteryAlert,
                title = "Unrestricted Battery",
                description = "Allows Android to wake app immediately on AP roaming or network events",
                isGranted = permissionState.isBatteryOptimizationIgnored,
                buttonText = "Whitelist",
                onActionClick = onRequestBatteryOptimization
            )

            if (permissionState.hasAutostartSettings) {
                Spacer(modifier = Modifier.height(10.dp))
                PermissionItemRow(
                    icon = Icons.Default.RocketLaunch,
                    title = "Autostart on Boot",
                    description = "Allows auto-connect rules when device boots up",
                    isGranted = false,
                    isOptional = true,
                    buttonText = "Settings",
                    onActionClick = onRequestAutostart
                )
            }
        }
    }
}

@Composable
fun PermissionItemRow(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    isOptional: Boolean = false,
    buttonText: String,
    onActionClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Default.CheckCircle else icon,
                contentDescription = null,
                tint = if (isGranted) StatusGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (isGranted) {
            Text(
                text = "✓ OK",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = StatusGreen
            )
        } else {
            Button(
                onClick = onActionClick,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isOptional) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text(
                    text = buttonText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun PermissionsRequiredDialog(
    permissionState: PermissionState,
    onRequestNotification: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onRequestExactAlarm: () -> Unit,
    onStartAnyway: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = StatusAmber,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = "Permissions Required",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "Background Keepalive requires permissions to stay alive without being suspended when your screen is locked:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(14.dp))

                if (!permissionState.isNotificationGranted) {
                    PermissionDialogRow(
                        title = "Notifications",
                        buttonText = "Allow",
                        onClick = onRequestNotification
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (!permissionState.isBatteryOptimizationIgnored) {
                    PermissionDialogRow(
                        title = "Unrestricted Battery",
                        buttonText = "Whitelist",
                        onClick = onRequestBatteryOptimization
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (!permissionState.canScheduleExactAlarms) {
                    PermissionDialogRow(
                        title = "Exact 60s Watchdog Alarms",
                        buttonText = "Allow",
                        onClick = onRequestExactAlarm
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onStartAnyway) {
                Text("Start Anyway")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    )
}

@Composable
private fun PermissionDialogRow(
    title: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "• $title",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        OutlinedButton(
            onClick = onClick,
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            modifier = Modifier.height(30.dp)
        ) {
            Text(buttonText, fontSize = 11.sp)
        }
    }
}
