package ru.dany.urent

import android.app.Application
import ru.dany.urent.core.LogBus
import ru.dany.urent.core.ScooterClient

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        ScooterClient.init(this)
        LogBus.info("APP", "DanyUrent ${BuildConfig.VERSION_NAME} запущен")
    }
}
