package com.imyash.pesuwifi.util

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
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
    private const val MAX_LOGS = 5000
    private const val MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024L // 5 MB
    private val idCounter = AtomicLong(1)
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val fullDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private val lock = Any()
    private val logList = ArrayDeque<LogEntry>(MAX_LOGS)

    private val _logsFlow = MutableStateFlow<List<LogEntry>>(emptyList())
    val logsFlow: StateFlow<List<LogEntry>> = _logsFlow.asStateFlow()

    private var logDir: File? = null
    private var logFile: File? = null
    private val fileExecutor = Executors.newSingleThreadExecutor()

    fun init(context: Context) {
        synchronized(lock) {
            if (logDir == null) {
                val dir = File(context.applicationContext.filesDir, "logs")
                if (!dir.exists()) dir.mkdirs()
                logDir = dir
                logFile = File(dir, "pesuwifi_universal.log")
            }
        }
        i("AppLogger", "Universal Logger initialized on ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})")
    }

    fun d(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)
    fun i(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun w(tag: String, message: String, tr: Throwable? = null) = log(LogLevel.WARN, tag, message, tr)
    fun e(tag: String, message: String, tr: Throwable? = null) = log(LogLevel.ERROR, tag, message, tr)

    // Specialized universal logging helpers
    fun roam(tag: String, message: String) = log(LogLevel.INFO, "$tag:Roam", message)
    fun wifi(tag: String, message: String) = log(LogLevel.INFO, "$tag:Wifi", message)
    fun watchdog(tag: String, message: String) = log(LogLevel.INFO, "$tag:Watchdog", message)
    fun portal(tag: String, message: String) = log(LogLevel.INFO, "$tag:Portal", message)

    fun log(level: LogLevel, tag: String, message: String, tr: Throwable? = null) {
        val now = System.currentTimeMillis()
        val timeStr = synchronized(timeFormat) { timeFormat.format(Date(now)) }
        val fullTimeStr = synchronized(fullDateFormat) { fullDateFormat.format(Date(now)) }
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

        // Keep in-memory entries for UI
        synchronized(lock) {
            if (logList.size >= MAX_LOGS) {
                logList.removeFirst()
            }
            logList.addLast(entry)
            _logsFlow.value = logList.toList()
        }

        // Asynchronously persist to file
        val targetFile = logFile
        if (targetFile != null) {
            fileExecutor.execute {
                try {
                    synchronized(targetFile) {
                        if (targetFile.exists() && targetFile.length() > MAX_FILE_SIZE_BYTES) {
                            val backup = File(targetFile.parentFile, "${targetFile.name}.1")
                            if (backup.exists()) backup.delete()
                            targetFile.renameTo(backup)
                        }
                        FileWriter(targetFile, true).use { fw ->
                            fw.append("[$fullTimeStr] [${level.name}] [$tag] $message\n")
                            if (stackTraceStr != null) {
                                fw.append(stackTraceStr).append("\n")
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Ignore write failures to prevent crash loops
                }
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            logList.clear()
            _logsFlow.value = emptyList()
        }
        val targetFile = logFile
        if (targetFile != null) {
            fileExecutor.execute {
                try {
                    synchronized(targetFile) {
                        if (targetFile.exists()) targetFile.delete()
                        val backup = File(targetFile.parentFile, "${targetFile.name}.1")
                        if (backup.exists()) backup.delete()
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun getLogFile(): File? = logFile

    fun exportLogsText(): String {
        val targetFile = logFile
        if (targetFile != null && targetFile.exists()) {
            try {
                val fileContent = targetFile.readText()
                if (fileContent.isNotBlank()) {
                    return fileContent
                }
            } catch (_: Exception) {}
        }

        val snapshot: List<LogEntry>
        synchronized(lock) {
            snapshot = logList.toList()
        }

        val sb = StringBuilder()
        sb.appendLine("=== PESU WiFi Universal Diagnostic Logs ===")
        sb.appendLine("Exported: ${fullDateFormat.format(Date())}")
        sb.appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})")
        sb.appendLine("Total In-Memory Entries: ${snapshot.size}")
        sb.appendLine("===========================================")
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
