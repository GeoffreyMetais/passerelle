package fr.passerelle.client.feature.files.di

import fr.passerelle.client.feature.files.presentation.FilesViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val filesModule = module {
    viewModel {
        FilesViewModel(
            browseDirectoryUseCase = get(),
            searchFilesUseCase = get(),
            createShareUseCase = get(),
            getSharesUseCase = get(),
            getShareQrSvgUseCase = get(),
            fileRepository = get(),
            platformActions = get(),
        )
    }
}
