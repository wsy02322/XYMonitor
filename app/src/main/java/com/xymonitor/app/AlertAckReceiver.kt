package com.xymonitor.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlertAckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        AlertHaptic.stop(context, "点通知")
    }
}
