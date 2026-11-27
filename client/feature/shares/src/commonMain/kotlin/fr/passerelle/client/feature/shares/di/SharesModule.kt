package fr.passerelle.client.feature.shares.di

import fr.passerelle.client.feature.shares.presentation.SharesViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val sharesModule = module {
    viewModel {
        SharesViewModel(
            getSharesUseCase = get(),
            updateShareUseCase = get(),
            deleteShareUseCase = get(),
            getShareQrSvgUseCase = get(),
            platformActions = get(),
        )
    }
}
