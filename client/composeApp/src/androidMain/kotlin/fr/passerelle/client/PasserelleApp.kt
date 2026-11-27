package fr.passerelle.client

import android.app.Application
import fr.passerelle.client.di.initKoin
import org.koin.android.ext.koin.androidContext

class PasserelleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@PasserelleApp)
        }
    }
}
