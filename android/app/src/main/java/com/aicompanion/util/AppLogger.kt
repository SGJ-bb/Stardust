package com.aicompanion.util

import android.content.Context
import com.aicompanion.BuildConfig
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private val logs = mutableListOf<String>()
    private const val MAX_LOGS = 500
    private const val PERSIST_FILE = "app_logs.txt"
    private const val FLUSH_INTERVAL = 20
    private val dateFmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val isDebug = BuildConfig.DEBUG

    @Volatile
    var enabled: Boolean = true

    @Volatile
    var debugVerbose: Boolean = false

    private var dCount = 0
    private const val D_SAMPLE_RATE = 5

    private var logFile: File? = null
    private var fileWriter: FileWriter? = null
    private var pendingCount = 0

    fun init(context: Context) {
        try {
            val file = File(context.filesDir, PERSIST_FILE)
            logFile = file
            if (file.exists()) {
                try {
                    val lines = file.readLines()
                    synchronized(logs) {
                        logs.clear()
                        logs.addAll(lines.takeLast(MAX_LOGS))
                    }
                } catch (e: Exception) {
                    android.util.Log.w("AppLogger", "Failed to load persisted logs: ${e.message}")
                }
            }
            fileWriter = FileWriter(file, true)
        } catch (e: Exception) {
            android.util.Log.w("AppLogger", "Failed to init log file: ${e.message}")
        }
    }

    fun d(tag: String, msg: String) {
        if (!enabled) return
        if (!debugVerbose && !isDebug) {
            dCount++
            if (dCount % D_SAMPLE_RATE != 0) return
        }
        val safe = sanitize(msg)
        val line = "[${dateFmt.format(Date())}] D/$tag: $safe"
        add(line)
        if (isDebug) android.util.Log.d(tag, safe)
    }

    fun e(tag: String, msg: String, throwable: Throwable? = null) {
        if (!enabled) return
        addLog("E", tag, msg, throwable)
        if (isDebug) {
            if (throwable != null) android.util.Log.e(tag, sanitize(msg), throwable)
            else android.util.Log.e(tag, sanitize(msg))
        }
    }

    fun w(tag: String, msg: String, throwable: Throwable? = null) {
        if (!enabled) return
        addLog("W", tag, msg, throwable)
        if (isDebug) {
            if (throwable != null) android.util.Log.w(tag, sanitize(msg), throwable)
            else android.util.Log.w(tag, sanitize(msg))
        }
    }

    fun i(tag: String, msg: String) {
        if (!enabled) return
        val safe = sanitize(msg)
        val line = "[${dateFmt.format(Date())}] I/$tag: $safe"
        add(line)
        if (isDebug) android.util.Log.i(tag, safe)
    }

    private fun addLog(level: String, tag: String, msg: String, throwable: Throwable?) {
        val safe = sanitize(msg)
        val line = buildString {
            append("[${dateFmt.format(Date())}] $level/$tag: $safe")
            if (throwable != null) {
                append(" | ${throwable.javaClass.simpleName}: ${throwable.message}")
                throwable.stackTrace.take(3).forEach { frame ->
                    val raw = "  at ${frame.toString()}"
                    append("\n${raw.take(120)}")
                }
            }
        }
        add(line)
    }

    private fun add(line: String) {
        synchronized(logs) {
            logs.add(line)
            if (logs.size > MAX_LOGS) logs.removeAt(0)
        }
        persistLine(line)
    }

    private fun persistLine(line: String) {
        try {
            val writer = fileWriter ?: return
            writer.write(line)
            writer.write("\n")
            pendingCount++
            if (pendingCount >= FLUSH_INTERVAL) {
                writer.flush()
                pendingCount = 0
            }
        } catch (_: Exception) {
        }
    }

    private fun sanitize(message: String): String {
        return message
            .replace(Regex("sk-[a-zA-Z0-9_-]{8,}"), "sk-***")
            .replace(Regex("Bearer\\s+\\S+", RegexOption.IGNORE_CASE), "Bearer ***")
            .replace(Regex("(key|api[_-]?key|token|secret|access[_-]?token)=[^&\\s]+", RegexOption.IGNORE_CASE), "$1=***")
    }

    fun getRecentLogs(count: Int = 150): String {
        return synchronized(logs) {
            logs.takeLast(count).joinToString("\n")
        }
    }

    fun getAll(): String {
        return synchronized(logs) {
            logs.joinToString("\n")
        }
    }

    fun clear() {
        synchronized(logs) {
            logs.clear()
        }
        try {
            fileWriter?.flush()
            fileWriter?.close()
            logFile?.delete()
            fileWriter = logFile?.let { FileWriter(it, false) }
        } catch (_: Exception) {
        }
        pendingCount = 0
    }

    fun flush() {
        try {
            fileWriter?.flush()
            pendingCount = 0
        } catch (_: Exception) {
        }
    }

    fun trimLogFile() {
        try {
            val file = logFile ?: return
            if (!file.exists()) return
            if (file.length() < 512 * 1024) return
            val lines = file.readLines()
            val kept = lines.takeLast(MAX_LOGS)
            fileWriter?.flush()
            fileWriter?.close()
            file.writeText(kept.joinToString("\n"))
            fileWriter = FileWriter(file, true)
            synchronized(logs) {
                logs.clear()
                logs.addAll(kept)
            }
        } catch (_: Exception) {
        }
    }
}
