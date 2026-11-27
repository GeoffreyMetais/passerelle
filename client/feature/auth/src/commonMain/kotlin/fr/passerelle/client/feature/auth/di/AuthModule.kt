package fr.passerelle.client.feature.auth.di

import fr.passerelle.client.feature.auth.presentation.AuthViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val authModule = module {
    viewModel {
        AuthViewModel(
            loginUseCase = get(),
            serverUrlProvider = get(),
            platformActions = get(),
        )
    }
}
