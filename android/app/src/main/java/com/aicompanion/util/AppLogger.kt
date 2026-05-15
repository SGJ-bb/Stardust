package com.aicompanion.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private val logs = mutableListOf<String>()
    private const val MAX_LOGS = 300
    private val dateFmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())

    fun d(tag: String, msg: String) {
        val line = "[${dateFmt.format(Date())}] D/$tag: $msg"
        add(line)
        android.util.Log.d(tag, msg)
    }

    fun e(tag: String, msg: String, throwable: Throwable? = null) {
        val line = buildString {
            append("[${dateFmt.format(Date())}] E/$tag: $msg")
            if (throwable != null) append(" | ${throwable.javaClass.simpleName}: ${throwable.message}")
        }
        add(line)
        if (throwable != null) android.util.Log.e(tag, msg, throwable)
        else android.util.Log.e(tag, msg)
    }

    fun w(tag: String, msg: String) {
        val line = "[${dateFmt.format(Date())}] W/$tag: $msg"
        add(line)
        android.util.Log.w(tag, msg)
    }

    fun i(tag: String, msg: String) {
        val line = "[${dateFmt.format(Date())}] I/$tag: $msg"
        add(line)
        android.util.Log.i(tag, msg)
    }

    private fun add(line: String) {
        synchronized(logs) {
            logs.add(line)
            if (logs.size > MAX_LOGS) logs.removeAt(0)
        }
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
    }
}