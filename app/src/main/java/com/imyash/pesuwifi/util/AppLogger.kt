package com.imyash.pesuwifi.util

import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR
}

data class LogEntry(
    val id: Long,
    val timestamp: String,
    val timeMillis: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val stackTrace: String? = null
)

object AppLogger {
    private const val MAX_LOGS = 1000
    private val idCounter = AtomicLong(1)
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val fullDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private val lock = Any()
    private val logList = ArrayDeque<LogEntry>(MAX_LOGS)

    private val _logsFlow = MutableStateFlow<List<LogEntry>>(emptyList())
    val logsFlow: StateFlow<List<LogEntry>> = _logsFlow.asStateFlow()

    fun d(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)
    fun i(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun w(tag: String, message: String, tr: Throwable? = null) = log(LogLevel.WARN, tag, message, tr)
    fun e(tag: String, message: String, tr: Throwable? = null) = log(LogLevel.ERROR, tag, message, tr)

    fun log(level: LogLevel, tag: String, message: String, tr: Throwable? = null) {
        val now = System.currentTimeMillis()
        val timeStr = synchronized(timeFormat) { timeFormat.format(Date(now)) }
        val stackTraceStr = tr?.let { throwable ->
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            sw.toString().trim()
        }

        val entry = LogEntry(
            id = idCounter.getAndIncrement(),
            timestamp = timeStr,
            timeMillis = now,
            level = level,
            tag = tag,
            message = message,
            stackTrace = stackTraceStr
        )

        // Log to standard Android logcat
        when (level) {
            LogLevel.DEBUG -> Log.d(tag, message, tr)
            LogLevel.INFO -> Log.i(tag, message, tr)
            LogLevel.WARN -> Log.w(tag, message, tr)
            LogLevel.ERROR -> Log.e(tag, message, tr)
        }

        synchronized(lock) {
            if (logList.size >= MAX_LOGS) {
                logList.removeFirst()
            }
            logList.addLast(entry)
            _logsFlow.value = logList.toList()
        }
    }

    fun clear() {
        synchronized(lock) {
            logList.clear()
            _logsFlow.value = emptyList()
        }
    }

    fun exportLogsText(): String {
        val snapshot: List<LogEntry>
        synchronized(lock) {
            snapshot = logList.toList()
        }

        val sb = StringBuilder()
        sb.appendLine("=== PESU WiFi Diagnostic Logs ===")
        sb.appendLine("Exported: ${fullDateFormat.format(Date())}")
        sb.appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})")
        sb.appendLine("Total Entries: ${snapshot.size}")
        sb.appendLine("=================================")
        sb.appendLine()

        for (entry in snapshot) {
            sb.appendLine("[${entry.timestamp}] [${entry.level.name}] [${entry.tag}] ${entry.message}")
            entry.stackTrace?.let { st ->
                sb.appendLine(st)
            }
        }

        return sb.toString()
    }
}
