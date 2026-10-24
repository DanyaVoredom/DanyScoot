package ru.dany.scoot

import android.app.Application
import ru.dany.scoot.core.LogBus
import ru.dany.scoot.core.Prefs
import ru.dany.scoot.core.ScooterClient
import ru.dany.scoot.core.UpdateManager

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        ScooterClient.init(this)
        LogBus.verbose = Prefs.get(this).verboseLog
        LogBus.info("APP", "DanyScoot ${BuildConfig.VERSION_NAME} запущен")
        UpdateManager.autoCheck(this)
    }
}
