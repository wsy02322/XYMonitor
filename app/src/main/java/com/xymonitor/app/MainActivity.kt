package com.xymonitor.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
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
    private lateinit var intervalAInput: EditText
    private lateinit var intervalBInput: EditText
    private lateinit var soundLabel: TextView
    private lateinit var errorSoundBox: CheckBox
    private lateinit var statusView: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var fullscreenStatus: TextView

    private val notifyPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted || Build.VERSION.SDK_INT < 33) {
            startMonitor()
        } else {
            Toast.makeText(this, "需要通知权限才能在后台运行", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickSound = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val uri = pickedRingtone(result.data)
        persistSoundUri(uri)
        prefs.newItemSoundUri = uri?.toString().orEmpty()
        AlertChannels.sync(this, prefs.newItemSoundUri)
        render()
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
        intervalAInput = findViewById(R.id.intervalA)
        intervalBInput = findViewById(R.id.intervalB)
        soundLabel = findViewById(R.id.soundLabel)
        errorSoundBox = findViewById(R.id.errorSound)
        statusView = findViewById(R.id.status)
        startButton = findViewById(R.id.start)
        stopButton = findViewById(R.id.stop)
        fullscreenStatus = findViewById(R.id.fullscreenStatus)
        userIdInput.setText(prefs.userId)
        intervalAInput.setText(prefs.intervalA.toString())
        intervalBInput.setText(prefs.intervalB.toString())
        startButton.setOnClickListener { requestStart() }
        stopButton.setOnClickListener {
            persistSettings()
            MonitorService.stop(this)
            render()
        }
        findViewById<Button>(R.id.pickSound).setOnClickListener { openSoundPicker() }
        findViewById<Button>(R.id.resetSound).setOnClickListener {
            prefs.newItemSoundUri = ""
            AlertChannels.sync(this, "")
            render()
        }
        errorSoundBox.setOnCheckedChangeListener { _, checked ->
            prefs.errorSound = checked
        }
        intervalAInput.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) persistSettings() }
        intervalBInput.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) persistSettings() }
        findViewById<Button>(R.id.battery).setOnClickListener { openBatterySettings() }
        findViewById<Button>(R.id.fullscreen).setOnClickListener { openFullScreenSettings() }
        statusView.setOnClickListener { AlertHaptic.stop(this) }
        AlertChannels.sync(this, prefs.newItemSoundUri)
        render()
    }

    override fun onStart() {
        super.onStart()
        AppForeground.monitorVisible = true
        AlertHaptic.stop(this)
        val filter = IntentFilter(MonitorService.ACTION_STATUS)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(statusReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(statusReceiver, filter)
        }
        render()
    }

    override fun onStop() {
        AppForeground.monitorVisible = false
        persistSettings()
        unregisterReceiver(statusReceiver)
        super.onStop()
    }

    private fun requestStart() {
        if (!persistSettings()) {
            Toast.makeText(this, "请填写有效的间隔秒数", Toast.LENGTH_SHORT).show()
            return
        }
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

    private fun persistSettings(): Boolean {
        val a = intervalAInput.text.toString().trim().toIntOrNull() ?: return false
        val b = intervalBInput.text.toString().trim().toIntOrNull() ?: return false
        prefs.intervalA = a
        prefs.intervalB = b
        prefs.errorSound = errorSoundBox.isChecked
        if (!intervalAInput.hasFocus()) intervalAInput.setText(prefs.intervalA.toString())
        if (!intervalBInput.hasFocus()) intervalBInput.setText(prefs.intervalB.toString())
        return true
    }

    private fun openSoundPicker() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.pick_sound))
        val current = prefs.newItemSoundUri
        if (current.isNotBlank()) {
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(current))
        }
        pickSound.launch(intent)
    }

    private fun pickedRingtone(data: Intent?): Uri? {
        if (data == null) return null
        return if (Build.VERSION.SDK_INT >= 33) {
            data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        }
    }

    private fun soundName(): String {
        val uri = prefs.newItemSoundUri
        if (uri.isBlank()) return getString(R.string.sound_default)
        return try {
            RingtoneManager.getRingtone(this, Uri.parse(uri))?.getTitle(this)
                ?: getString(R.string.sound_custom)
        } catch (_: Exception) {
            getString(R.string.sound_custom)
        }
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

    private fun persistSoundUri(uri: Uri?) {
        if (uri == null) return
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: Exception) {
        }
    }

    private fun openFullScreenSettings() {
        if (Build.VERSION.SDK_INT < 34) {
            Toast.makeText(this, getString(R.string.fullscreen_on), Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                    .setData(Uri.parse("package:$packageName")),
            )
        } catch (_: Exception) {
            Toast.makeText(this, "请在系统设置中允许全屏通知", Toast.LENGTH_SHORT).show()
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
        errorSoundBox.setOnCheckedChangeListener(null)
        errorSoundBox.isChecked = prefs.errorSound
        errorSoundBox.setOnCheckedChangeListener { _, checked -> prefs.errorSound = checked }
        soundLabel.text = getString(R.string.sound_value, soundName())
        fullscreenStatus.text = if (AlertChannels.canUseFullScreen(this)) {
            getString(R.string.fullscreen_on)
        } else {
            getString(R.string.fullscreen_off)
        }
        if (!intervalAInput.hasFocus()) intervalAInput.setText(prefs.intervalA.toString())
        if (!intervalBInput.hasFocus()) intervalBInput.setText(prefs.intervalB.toString())
        val time = if (prefs.lastCheckAt > 0) {
            TIME_FMT.format(Date(prefs.lastCheckAt))
        } else {
            "—"
        }
        val wait = if (running && prefs.nextWaitMs > 0) {
            "下次约 ${Interval.formatSeconds(prefs.nextWaitMs)} 秒"
        } else {
            "下次：—"
        }
        val error = prefs.lastError.ifBlank { "无" }
        statusView.text = buildString {
            append(if (running) "状态：运行中（A～B 秒随机间隔）" else "状态：已停止")
            append('\n')
            append(wait)
            append('\n')
            append("上次巡检：$time")
            append('\n')
            append("结果：${prefs.lastStatus}")
            append('\n')
            append("当前第一件：${prefs.lastFirstItemId.ifBlank { "—" }}")
            append('\n')
            append("错误：$error")
        }
    }

    companion object {
        private val TIME_FMT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
    }
}
