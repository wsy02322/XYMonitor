package com.xymonitor.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: Prefs
    private lateinit var userIdInput: EditText
    private lateinit var statusView: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    private val notifyPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted || Build.VERSION.SDK_INT < 33) {
            startMonitor()
        } else {
            Toast.makeText(this, "需要通知权限才能在后台运行", Toast.LENGTH_SHORT).show()
        }
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            render()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)
        userIdInput = findViewById(R.id.userId)
        statusView = findViewById(R.id.status)
        startButton = findViewById(R.id.start)
        stopButton = findViewById(R.id.stop)
        userIdInput.setText(prefs.userId)
        startButton.setOnClickListener { requestStart() }
        stopButton.setOnClickListener {
            MonitorService.stop(this)
            render()
        }
        findViewById<Button>(R.id.battery).setOnClickListener { openBatterySettings() }
        render()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(MonitorService.ACTION_STATUS)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(statusReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(statusReceiver, filter)
        }
        render()
    }

    override fun onStop() {
        unregisterReceiver(statusReceiver)
        super.onStop()
    }

    private fun requestStart() {
        val userId = userIdInput.text.toString().trim()
        if (userId.isEmpty() || !userId.all { it.isDigit() }) {
            Toast.makeText(this, "请填写数字 userId", Toast.LENGTH_SHORT).show()
            return
        }
        prefs.userId = userId
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        startMonitor()
    }

    private fun startMonitor() {
        askIgnoreBatteryOnce()
        MonitorService.start(this, userIdInput.text.toString().trim())
        render()
    }

    private fun askIgnoreBatteryOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName")),
            )
        } catch (_: Exception) {
            openBatterySettings()
        }
    }

    private fun openBatterySettings() {
        try {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (_: Exception) {
            Toast.makeText(this, "请在系统设置中关闭电池优化", Toast.LENGTH_SHORT).show()
        }
    }

    private fun render() {
        val running = prefs.running
        startButton.isEnabled = !running
        stopButton.isEnabled = running
        userIdInput.isEnabled = !running
        val time = if (prefs.lastCheckAt > 0) {
            TIME_FMT.format(Date(prefs.lastCheckAt))
        } else {
            "—"
        }
        val error = prefs.lastError.ifBlank { "无" }
        statusView.text = buildString {
            append(if (running) "状态：运行中（每 3 分钟巡检）" else "状态：已停止")
            append('\n')
            append("上次巡检：$time")
            append('\n')
            append("结果：${prefs.lastStatus}")
            append('\n')
            append("已知商品：${prefs.knownIds.size} 个")
            append('\n')
            append("错误：$error")
        }
    }

    companion object {
        private val TIME_FMT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
    }
}
