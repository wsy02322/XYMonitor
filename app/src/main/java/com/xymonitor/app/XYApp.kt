package com.xymonitor.app

import android.app.Application
import android.os.Build

class XYApp : Application() {
    override fun onCreate() {
        System.setProperty("java.net.preferIPv4Stack", "true")
        System.setProperty("java.net.preferIPv6Addresses", "false")
        super.onCreate()
        DebugLog.init(this)
        DebugLog.i("进程启动 sdk=${Build.VERSION.SDK_INT}")
    }
}
