package com.imyash.pesuwifi

import android.app.Application
import com.imyash.pesuwifi.service.SystemRuleManager
import com.imyash.pesuwifi.util.AppLogger

class PesuWifiApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        SystemRuleManager.registerAllRules(this)
    }
}
