package com.imyash.pesuwifi.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.SignalWifiBad
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.imyash.pesuwifi.data.PortalStatus
import com.imyash.pesuwifi.ui.theme.StatusAmber
import com.imyash.pesuwifi.ui.theme.StatusGreen
import com.imyash.pesuwifi.ui.theme.StatusRed

@Composable
fun StatusCard(
    status: PortalStatus,
    isDaemonRunning: Boolean,
    onViewLogs: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val statusColor by animateColorAsState(
        targetValue = when {
            !status.isWifiConnected -> Color.Gray
            !status.isPesuWifi -> StatusAmber
            !status.isPortalOnline -> StatusRed
            status.isLoggedIn -> StatusGreen
            else -> StatusAmber
        },
        label = "status_color"
    )

    val statusTitle = when {
        !status.isWifiConnected -> "Wi-Fi Disconnected"
        !status.isPesuWifi -> "External Wi-Fi (Paused)"
        !status.isPortalOnline -> "Portal Unreachable"
        status.isLoggedIn -> "Session Active"
        else -> "Session Inactive"
    }

    val statusIcon = when {
        !status.isWifiConnected -> Icons.Default.SignalWifiOff
        !status.isPesuWifi -> Icons.Default.SignalWifi4Bar
        !status.isPortalOnline -> Icons.Default.SignalWifiBad
        else -> Icons.Default.SignalWifi4Bar
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = statusTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = status.statusMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Latency Chip
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (status.latencyMs != null) "${status.latencyMs} ms" else "–",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Daemon Status Chip
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isDaemonRunning) StatusGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isDaemonRunning) StatusGreen else Color.Gray)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isDaemonRunning) "Daemon: On" else "Daemon: Off",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isDaemonRunning) StatusGreen else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                if (onViewLogs != null) {
                    TextButton(
                        onClick = onViewLogs,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Logs", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
