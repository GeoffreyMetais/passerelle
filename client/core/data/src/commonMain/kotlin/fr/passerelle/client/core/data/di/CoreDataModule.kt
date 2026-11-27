package fr.passerelle.client.core.data.di

import fr.passerelle.client.core.data.remote.createPasserelleHttpClient
import fr.passerelle.client.core.data.repository.AuthRepositoryImpl
import fr.passerelle.client.core.data.repository.FileRepositoryImpl
import fr.passerelle.client.core.data.repository.ShareRepositoryImpl
import fr.passerelle.client.core.domain.repository.AuthRepository
import fr.passerelle.client.core.domain.repository.FileRepository
import fr.passerelle.client.core.domain.repository.ShareRepository
import fr.passerelle.client.core.domain.usecase.BrowseDirectoryUseCase
import fr.passerelle.client.core.domain.usecase.CreateShareUseCase
import fr.passerelle.client.core.domain.usecase.DeleteShareUseCase
import fr.passerelle.client.core.domain.usecase.GetShareQrSvgUseCase
import fr.passerelle.client.core.domain.usecase.GetSharesUseCase
import fr.passerelle.client.core.domain.usecase.LoginUseCase
import fr.passerelle.client.core.domain.usecase.LogoutUseCase
import fr.passerelle.client.core.domain.usecase.ObserveAuthStateUseCase
import fr.passerelle.client.core.domain.usecase.SearchFilesUseCase
import fr.passerelle.client.core.domain.usecase.UpdateShareUseCase
import org.koin.dsl.module

val coreDataModule = module {
    single {
        createPasserelleHttpClient(
            serverUrlProvider = get(),
            sessionStore = get(),
        )
    }

    single<AuthRepository> { AuthRepositoryImpl(get(), get(), get()) }
    single<FileRepository> { FileRepositoryImpl(get(), get()) }
    single<ShareRepository> { ShareRepositoryImpl(get(), get()) }

    factory { LoginUseCase(get()) }
    factory { LogoutUseCase(get()) }
    factory { ObserveAuthStateUseCase(get()) }
    factory { BrowseDirectoryUseCase(get()) }
    factory { SearchFilesUseCase(get()) }
    factory { CreateShareUseCase(get()) }
    factory { GetSharesUseCase(get()) }
    factory { UpdateShareUseCase(get()) }
    factory { DeleteShareUseCase(get()) }
    factory { GetShareQrSvgUseCase(get()) }
}
