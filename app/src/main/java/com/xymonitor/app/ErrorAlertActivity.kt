package com.xymonitor.app

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class ErrorAlertActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty().ifBlank { "未知错误" }
        AlertDialog.Builder(this)
            .setTitle("巡检失败")
            .setMessage(message)
            .setCancelable(true)
            .setPositiveButton("确定") { _, _ -> finish() }
            .setOnDismissListener { if (!isFinishing) finish() }
            .show()
    }

    companion object {
        const val EXTRA_MESSAGE = "message"
    }
}
