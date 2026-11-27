package fr.passerelle.client.di

import fr.passerelle.client.core.platform.PlatformActions
import fr.passerelle.client.core.platform.ServerUrlProvider
import fr.passerelle.client.core.platform.SessionStore
import fr.passerelle.client.core.platform.WebPlatformActions
import fr.passerelle.client.core.platform.WebServerUrlProvider
import fr.passerelle.client.core.platform.WebSessionStore
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single<SessionStore> { WebSessionStore() }
    single<ServerUrlProvider> { WebServerUrlProvider() }
    single<PlatformActions> { WebPlatformActions() }
}
