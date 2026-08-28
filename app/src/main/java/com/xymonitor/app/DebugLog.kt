package com.xymonitor.app

import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugLog {
    const val ACTION = "com.xymonitor.app.LOG"
    const val TAG = "XYMonitor"
    private const val MAX_LINES = 200
    private val lock = Any()
    private val lines = ArrayDeque<String>()
    private var app: Context? = null
    private var file: File? = null
    private val timeFmt = SimpleDateFormat("HH:mm:ss.SS", Locale.US)

    fun init(context: Context) {
        val ctx = context.applicationContext
        synchronized(lock) {
            app = ctx
            file = File(ctx.filesDir, "xymonitor-debug.log")
            if (lines.isEmpty()) {
                val existing = file?.takeIf { it.exists() }?.readLines().orEmpty()
                existing.takeLast(MAX_LINES).forEach { lines.addLast(it) }
            }
        }
    }

    fun i(message: String) {
        val line = "${timeFmt.format(Date())} $message"
        Log.i(TAG, message)
        val ctx: Context?
        synchronized(lock) {
            lines.addLast(line)
            while (lines.size > MAX_LINES) lines.removeFirst()
            try {
                file?.writeText(lines.joinToString("\n") + "\n")
            } catch (_: Exception) {
            }
            ctx = app
        }
        ctx?.sendBroadcast(Intent(ACTION).setPackage(ctx.packageName))
    }

    fun snapshot(limit: Int = 20): String {
        synchronized(lock) {
            if (lines.isEmpty()) return "（暂无事件）"
            return lines.toList().takeLast(limit).joinToString("\n")
        }
    }

    fun dump(): String {
        synchronized(lock) {
            return lines.joinToString("\n")
        }
    }
}
