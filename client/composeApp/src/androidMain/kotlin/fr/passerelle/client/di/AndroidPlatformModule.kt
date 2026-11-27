package fr.passerelle.client.di

import fr.passerelle.client.core.platform.AndroidPlatformActions
import fr.passerelle.client.core.platform.AndroidServerUrlProvider
import fr.passerelle.client.core.platform.AndroidSessionStore
import fr.passerelle.client.core.platform.PlatformActions
import fr.passerelle.client.core.platform.ServerUrlProvider
import fr.passerelle.client.core.platform.SessionStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single<SessionStore> { AndroidSessionStore(androidContext()) }
    single<ServerUrlProvider> { AndroidServerUrlProvider(androidContext()) }
    single<PlatformActions> { AndroidPlatformActions(androidContext()) }
}
