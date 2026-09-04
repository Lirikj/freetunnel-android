package com.freetunnel.android

import android.app.Application
import com.adguard.trusttunnel.VpnService

class FreeTunnelApp : Application() {
    override fun onCreate() {
        super.onCreate()
        VpnService.initialize(this)
    }
}
