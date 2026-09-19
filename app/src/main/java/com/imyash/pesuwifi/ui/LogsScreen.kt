package com.imyash.pesuwifi.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import com.imyash.pesuwifi.data.BssidDatabase
import com.imyash.pesuwifi.data.CampusAp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.imyash.pesuwifi.data.PortalRepository
import com.imyash.pesuwifi.data.WifiTelemetry
import com.imyash.pesuwifi.ui.theme.StatusAmber
import com.imyash.pesuwifi.ui.theme.StatusGreen
import com.imyash.pesuwifi.util.AppLogger
import com.imyash.pesuwifi.util.LogEntry
import com.imyash.pesuwifi.util.LogLevel

enum class LogCategory(val label: String) {
    ALL("All"),
    ROAM("Roam"),
    WIFI("Wi-Fi"),
    PORTAL("Portal"),
    WATCHDOG("Watchdog"),
    ERRORS("Errors"),
    WARNINGS("Warnings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val allLogs by AppLogger.logsFlow.collectAsState()
    val portalRepo = remember { PortalRepository.getInstance(context) }
    val telemetry by portalRepo.telemetryFlow.collectAsState()
    val campusAps by BssidDatabase.campusApsFlow.collectAsState()
    val listState = rememberLazyListState()

    var selectedCategory by remember { mutableStateOf(LogCategory.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }

    var showTagDialog by remember { mutableStateOf(false) }
    var tagDialogBssid by remember { mutableStateOf("") }
    var tagDialogText by remember { mutableStateOf("") }
    var showApListDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        BssidDatabase.init(context)
    }

    val filteredLogs = remember(allLogs, selectedCategory, searchQuery) {
        allLogs.asReversed().filter { entry ->
            val matchesCategory = when (selectedCategory) {
                LogCategory.ALL -> true
                LogCategory.ROAM -> entry.tag.contains("Roam", ignoreCase = true) ||
                        entry.message.contains("roam", ignoreCase = true) ||
                        entry.message.contains("BSSID", ignoreCase = true)
                LogCategory.WIFI -> entry.tag.contains("Wifi", ignoreCase = true) ||
                        entry.message.contains("wifi", ignoreCase = true) ||
                        entry.message.contains("supplicant", ignoreCase = true) ||
                        entry.message.contains("scan", ignoreCase = true)
                LogCategory.PORTAL -> entry.tag.contains("Portal", ignoreCase = true) ||
                        entry.message.contains("portal", ignoreCase = true) ||
                        entry.message.contains("login", ignoreCase = true) ||
                        entry.message.contains("live", ignoreCase = true)
                LogCategory.WATCHDOG -> entry.tag.contains("Watchdog", ignoreCase = true) ||
                        entry.message.contains("watchdog", ignoreCase = true) ||
                        entry.message.contains("keepalive", ignoreCase = true)
                LogCategory.ERRORS -> entry.level == LogLevel.ERROR
                LogCategory.WARNINGS -> entry.level == LogLevel.WARN
            }
            val matchesSearch = searchQuery.isBlank() ||
                    entry.message.contains(searchQuery, ignoreCase = true) ||
                    entry.tag.contains(searchQuery, ignoreCase = true) ||
                    (entry.stackTrace?.contains(searchQuery, ignoreCase = true) == true)
            matchesCategory && matchesSearch
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Universal Telemetry",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "${filteredLogs.size} of ${allLogs.size} entries",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Toggle Search"
                        )
                    }
                    IconButton(onClick = {
                        val exported = AppLogger.exportLogsText()
                        val safeText = if (exported.length > 250_000) exported.takeLast(250_000) else exported
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("PESU WiFi Logs", safeText))
                        val msg = if (exported.length > 250_000) "Latest logs copied to clipboard (truncated)" else "Logs copied to clipboard"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy All"
                        )
                    }
                    IconButton(onClick = {
                        val logFile = AppLogger.getLogFile()
                        if (logFile != null && logFile.exists()) {
                            try {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    logFile
                                )
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share PESU WiFi Log File"))
                            } catch (e: Exception) {
                                val text = AppLogger.exportLogsText()
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, text.takeLast(100_000))
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share PESU WiFi Logs"))
                            }
                        } else {
                            val text = AppLogger.exportLogsText()
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text.takeLast(100_000))
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share PESU WiFi Logs"))
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share Logs"
                        )
                    }
                    IconButton(onClick = {
                        AppLogger.clear()
                        Toast.makeText(context, "Logs cleared", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear Logs"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Live Wi-Fi Telemetry Bar & Campus AP Hub
            LiveTelemetryCard(
                telemetry = telemetry,
                campusAps = campusAps,
                onTriggerScan = {
                    val ok = portalRepo.triggerManualScan(context)
                    val msg = if (ok) "Scanning for campus APs..." else "Scan throttled by Android (try again shortly)"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                },
                onExportDatabase = {
                    val json = BssidDatabase.exportJson(context)
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_TEXT, json)
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Export Campus AP Database"))
                },
                onOpenTagDialog = { bssid, currentTag ->
                    tagDialogBssid = bssid
                    tagDialogText = currentTag
                    showTagDialog = true
                },
                onOpenApList = {
                    showApListDialog = true
                }
            )

            AnimatedVisibility(visible = showSearch) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filter logs by keyword, BSSID, IP...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.ClearAll, contentDescription = "Clear")
                            }
                        }
                    }
                )
            }

            // Category Filter Chips (Horizontally Scrollable)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (cat in LogCategory.values()) {
                    val count = remember(allLogs, cat) {
                        when (cat) {
                            LogCategory.ALL -> allLogs.size
                            LogCategory.ROAM -> allLogs.count {
                                it.tag.contains("Roam", ignoreCase = true) ||
                                        it.message.contains("roam", ignoreCase = true) ||
                                        it.message.contains("BSSID", ignoreCase = true)
                            }
                            LogCategory.WIFI -> allLogs.count {
                                it.tag.contains("Wifi", ignoreCase = true) ||
                                        it.message.contains("wifi", ignoreCase = true) ||
                                        it.message.contains("supplicant", ignoreCase = true) ||
                                        it.message.contains("scan", ignoreCase = true)
                            }
                            LogCategory.PORTAL -> allLogs.count {
                                it.tag.contains("Portal", ignoreCase = true) ||
                                        it.message.contains("portal", ignoreCase = true) ||
                                        it.message.contains("login", ignoreCase = true) ||
                                        it.message.contains("live", ignoreCase = true)
                            }
                            LogCategory.WATCHDOG -> allLogs.count {
                                it.tag.contains("Watchdog", ignoreCase = true) ||
                                        it.message.contains("watchdog", ignoreCase = true) ||
                                        it.message.contains("keepalive", ignoreCase = true)
                            }
                            LogCategory.ERRORS -> allLogs.count { it.level == LogLevel.ERROR }
                            LogCategory.WARNINGS -> allLogs.count { it.level == LogLevel.WARN }
                        }
                    }

                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text("${cat.label} ($count)") },
                        shape = RoundedCornerShape(8.dp),
                        colors = if (cat == LogCategory.ERRORS) {
                            FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        } else {
                            FilterChipDefaults.filterChipColors()
                        }
                    )
                }
            }

            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (allLogs.isEmpty()) "No logs captured yet" else "No matching logs found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredLogs, key = { it.id }) { logEntry ->
                        LogItemCard(entry = logEntry)
                    }
                }
            }
        }

        if (showTagDialog) {
            AlertDialog(
                onDismissRequest = { showTagDialog = false },
                title = { Text("Label Campus AP", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            text = "Router BSSID:",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = tagDialogBssid,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = tagDialogText,
                            onValueChange = { tagDialogText = it },
                            label = { Text("Room / Location Tag") },
                            placeholder = { Text("e.g. GJBC 4th Floor Classroom 402") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            BssidDatabase.setTag(context, tagDialogBssid, tagDialogText.trim())
                            showTagDialog = false
                            Toast.makeText(context, "Location tag saved", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTagDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showApListDialog) {
            AlertDialog(
                onDismissRequest = { showApListDialog = false },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Mapped APs (${campusAps.size})", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        IconButton(
                            onClick = {
                                val json = BssidDatabase.exportJson(context)
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(Intent.EXTRA_TEXT, json)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Export Campus AP Database"))
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Export JSON")
                        }
                    }
                },
                text = {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(380.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(campusAps, key = { it.bssid }) { ap ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = ap.bssid,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = "${ap.band} ${if (ap.channel > 0) "Ch ${ap.channel}" else ""}".trim(),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    if (!ap.tag.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "🏷️ ${ap.tag}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = StatusGreen
                                        )
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (ap.gateway != null) "GW: ${ap.gateway}" else "SSID: ${ap.ssid}",
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "Edit Tag",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.clickable {
                                                tagDialogBssid = ap.bssid
                                                tagDialogText = ap.tag ?: ""
                                                showTagDialog = true
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showApListDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}

@Composable
fun LiveTelemetryCard(
    telemetry: WifiTelemetry,
    campusAps: List<CampusAp>,
    onTriggerScan: () -> Unit,
    onExportDatabase: () -> Unit,
    onOpenTagDialog: (bssid: String, currentTag: String) -> Unit,
    onOpenApList: () -> Unit
) {
    val currentAp = remember(campusAps, telemetry.bssid) {
        if (!telemetry.bssid.isNullOrBlank()) {
            campusAps.find { it.bssid.equals(telemetry.bssid, ignoreCase = true) }
        } else null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
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
                        imageVector = if (telemetry.isConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = if (telemetry.isCampus) StatusGreen else if (telemetry.isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = telemetry.ssid ?: if (telemetry.isConnected) "Wi-Fi Connected" else "Wi-Fi Disconnected",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                val (badgeBg, badgeFg, label) = when {
                    telemetry.isCampus -> Triple(StatusGreen.copy(alpha = 0.2f), StatusGreen, "CAMPUS")
                    telemetry.isConnected -> Triple(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, "EXTERNAL")
                    else -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, "OFFLINE")
                }
                Box(
                    modifier = Modifier
                        .background(badgeBg, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeFg
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "ROUTER BSSID",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = telemetry.bssid ?: "--:--:--:--:--:--",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "SIGNAL / CH",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (telemetry.rssi != null) "${telemetry.rssi} dBm${telemetry.channel?.let { " (Ch $it)" } ?: ""}" else "--",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (telemetry.ip != null || telemetry.gateway != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "IP: ${telemetry.ip ?: "--"}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "GW: ${telemetry.gateway ?: "--"}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (currentAp?.tag != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        onOpenTagDialog(telemetry.bssid ?: "", currentAp.tag ?: "")
                    }
                ) {
                    Text(
                        text = "🏷️ ${currentAp.tag}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = StatusGreen
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Tag",
                        tint = StatusGreen,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            )
            Spacer(modifier = Modifier.height(6.dp))

            // Campus AP Database Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onOpenApList() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Mapped APs: ",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${campusAps.size}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = " (view list)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (telemetry.bssid != null && telemetry.bssid != "02:00:00:00:00:00" && currentAp?.tag == null) {
                        Text(
                            text = "🏷️ Label AP",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = StatusGreen,
                            modifier = Modifier
                                .clickable {
                                    onOpenTagDialog(telemetry.bssid, "")
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }

                    IconButton(
                        onClick = onTriggerScan,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Scan APs",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = onExportDatabase,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Export APs",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LogItemCard(entry: LogEntry) {
    var expanded by remember { mutableStateOf(false) }

    val isRoam = entry.tag.contains("Roam", ignoreCase = true)
    val isWifi = entry.tag.contains("Wifi", ignoreCase = true)
    val isPortal = entry.tag.contains("Portal", ignoreCase = true)
    val isWatchdog = entry.tag.contains("Watchdog", ignoreCase = true)

    val (levelBg, levelFg) = when (entry.level) {
        LogLevel.ERROR -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        LogLevel.WARN -> StatusAmber.copy(alpha = 0.2f) to StatusAmber
        LogLevel.INFO -> StatusGreen.copy(alpha = 0.2f) to StatusGreen
        LogLevel.DEBUG -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    val tagColor = when {
        isRoam -> Color(0xFF00ACC1) // Teal
        isWifi -> Color(0xFF1E88E5) // Blue
        isPortal -> Color(0xFF8E24AA) // Purple
        isWatchdog -> Color(0xFFFB8C00) // Orange
        entry.level == LogLevel.ERROR -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (entry.stackTrace != null || entry.message.length > 120) expanded = !expanded
            },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(levelBg, RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = entry.level.name,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = levelFg
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = entry.tag,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = tagColor
                )

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = entry.timestamp,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(5.dp))

            Text(
                text = entry.message,
                fontSize = 12.sp,
                fontFamily = if (isWifi || isRoam || isPortal) FontFamily.Monospace else FontFamily.Default,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (expanded) Int.MAX_VALUE else 6
            )

            if (entry.stackTrace != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (expanded) "Hide Stacktrace ▲" else "View Stacktrace ▼",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                AnimatedVisibility(visible = expanded) {
                    Text(
                        text = entry.stackTrace,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}
