package be.drakarah.intonation

import android.app.Application
import be.drakarah.intonation.di.AppContainer

class IntonationApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
