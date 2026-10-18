package com.dany.fusionbot

import android.app.Application
import android.content.Context
import com.dany.fusionbot.core.LogBus
import com.dany.fusionbot.core.Prefs
import com.dany.fusionbot.core.ScooterClient

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        ScooterClient.init(this)
        LogBus.info("APP", "FusionBot ${BuildConfig.VERSION_NAME} запущен")
    }

    companion object {
        @Volatile
        lateinit var instance: App
            private set

        fun prefs(context: Context): Prefs = Prefs.get(context)
    }
}
