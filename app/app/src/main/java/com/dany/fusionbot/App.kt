package com.dany.fusionbot

import android.app.Application
import com.dany.fusionbot.core.LogBus
import com.dany.fusionbot.core.ScooterClient

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        ScooterClient.init(this)
        LogBus.info("APP", "FusionBot ${BuildConfig.VERSION_NAME} запущен")
    }
}
