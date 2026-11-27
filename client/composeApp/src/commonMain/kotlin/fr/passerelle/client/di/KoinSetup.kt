package fr.passerelle.client.di

import fr.passerelle.client.core.data.di.coreDataModule
import fr.passerelle.client.feature.auth.di.authModule
import fr.passerelle.client.feature.files.di.filesModule
import fr.passerelle.client.feature.shares.di.sharesModule
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration

expect fun platformModule(): Module

fun appModules(): List<Module> = listOf(
    platformModule(),
    coreDataModule,
    authModule,
    filesModule,
    sharesModule,
)

fun initKoin(appDeclaration: KoinAppDeclaration = {}) {
    startKoin {
        appDeclaration()
        modules(appModules())
    }
}
