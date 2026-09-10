package com.imyash.pesuwifi.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.imyash.pesuwifi.ui.theme.StatusAmber
import com.imyash.pesuwifi.ui.theme.StatusGreen
import com.imyash.pesuwifi.util.AppLogger
import com.imyash.pesuwifi.util.LogEntry
import com.imyash.pesuwifi.util.LogLevel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val allLogs by AppLogger.logsFlow.collectAsState()
    val listState = rememberLazyListState()

    var selectedLevel by remember { mutableStateOf<LogLevel?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }

    val filteredLogs = remember(allLogs, selectedLevel, searchQuery) {
        allLogs.asReversed().filter { entry ->
            val matchesLevel = selectedLevel == null || entry.level == selectedLevel
            val matchesSearch = searchQuery.isBlank() ||
                    entry.message.contains(searchQuery, ignoreCase = true) ||
                    entry.tag.contains(searchQuery, ignoreCase = true) ||
                    (entry.stackTrace?.contains(searchQuery, ignoreCase = true) == true)
            matchesLevel && matchesSearch
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Diagnostic Logs",
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
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("PESU WiFi Logs", exported))
                        Toast.makeText(context, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy All"
                        )
                    }
                    IconButton(onClick = {
                        val exported = AppLogger.exportLogsText()
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, exported)
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, "Share PESU WiFi Logs")
                        context.startActivity(shareIntent)
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
            AnimatedVisibility(visible = showSearch) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filter logs by keyword...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
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

            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedLevel == null,
                    onClick = { selectedLevel = null },
                    label = { Text("All (${allLogs.size})") },
                    shape = RoundedCornerShape(8.dp)
                )

                val errorCount = remember(allLogs) { allLogs.count { it.level == LogLevel.ERROR } }
                FilterChip(
                    selected = selectedLevel == LogLevel.ERROR,
                    onClick = { selectedLevel = if (selectedLevel == LogLevel.ERROR) null else LogLevel.ERROR },
                    label = { Text("Errors ($errorCount)") },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                )

                val warnCount = remember(allLogs) { allLogs.count { it.level == LogLevel.WARN } }
                FilterChip(
                    selected = selectedLevel == LogLevel.WARN,
                    onClick = { selectedLevel = if (selectedLevel == LogLevel.WARN) null else LogLevel.WARN },
                    label = { Text("Warnings ($warnCount)") },
                    shape = RoundedCornerShape(8.dp)
                )

                val infoCount = remember(allLogs) { allLogs.count { it.level == LogLevel.INFO } }
                FilterChip(
                    selected = selectedLevel == LogLevel.INFO,
                    onClick = { selectedLevel = if (selectedLevel == LogLevel.INFO) null else LogLevel.INFO },
                    label = { Text("Info ($infoCount)") },
                    shape = RoundedCornerShape(8.dp)
                )
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
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredLogs, key = { it.id }) { logEntry ->
                        LogItemCard(entry = logEntry)
                    }
                }
            }
        }
    }
}

@Composable
fun LogItemCard(entry: LogEntry) {
    var expanded by remember { mutableStateOf(false) }

    val (badgeColor, badgeTextColor) = when (entry.level) {
        LogLevel.ERROR -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        LogLevel.WARN -> StatusAmber.copy(alpha = 0.2f) to StatusAmber
        LogLevel.INFO -> StatusGreen.copy(alpha = 0.2f) to StatusGreen
        LogLevel.DEBUG -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (entry.stackTrace != null) expanded = !expanded
            },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(badgeColor, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = entry.level.name,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeTextColor
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = entry.tag,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = entry.timestamp,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = entry.message,
                fontSize = 13.sp,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
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
