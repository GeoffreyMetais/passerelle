package fr.passerelle.client.core.domain.usecase

import fr.passerelle.client.core.domain.error.AppError
import fr.passerelle.client.core.domain.error.Resource
import fr.passerelle.client.core.domain.model.AuthSession
import fr.passerelle.client.core.domain.model.CreateShareParams
import fr.passerelle.client.core.domain.model.DirectoryContent
import fr.passerelle.client.core.domain.model.FileItem
import fr.passerelle.client.core.domain.model.ShareLink
import fr.passerelle.client.core.domain.model.SortDirection
import fr.passerelle.client.core.domain.model.SortOption
import fr.passerelle.client.core.domain.model.UpdateShareParams
import fr.passerelle.client.core.domain.repository.AuthRepository
import fr.passerelle.client.core.domain.repository.FileRepository
import fr.passerelle.client.core.domain.repository.ShareRepository
import kotlinx.coroutines.flow.Flow

class LoginUseCase(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(
        serverUrl: String,
        username: String,
        password: String,
        deviceName: String,
    ): Resource<AuthSession> {
        val normalizedUrl = serverUrl.trim().trimEnd('/')
        if (normalizedUrl.isEmpty() ||
            (!normalizedUrl.startsWith("http://") && !normalizedUrl.startsWith("https://"))
        ) {
            return Resource.Error(AppError.ValidationError("serverUrl", "Invalid URL scheme"))
        }
        val normalizedUser = username.trim()
        if (normalizedUser.isEmpty()) {
            return Resource.Error(AppError.ValidationError("username", "Username is required"))
        }
        if (password.isEmpty()) {
            return Resource.Error(AppError.ValidationError("password", "Password is required"))
        }
        return authRepository.login(
            serverUrl = normalizedUrl,
            username = normalizedUser,
            password = password,
            deviceName = deviceName,
        )
    }
}

class LogoutUseCase(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(): Resource<Unit> = authRepository.logout()
}

class ObserveAuthStateUseCase(
    private val authRepository: AuthRepository,
) {
    operator fun invoke(): Flow<Boolean> = authRepository.observeIsAuthenticated()
}

class BrowseDirectoryUseCase(
    private val fileRepository: FileRepository,
) {
    suspend operator fun invoke(
        path: String = "",
        sortBy: SortOption = SortOption.NAME,
        sortOrder: SortDirection = SortDirection.ASC,
        showHidden: Boolean = false,
    ): Resource<DirectoryContent> {
        val normalizedPath = path.trim().trim('/')
        return fileRepository.browse(
            path = normalizedPath,
            sortBy = sortBy,
            sortOrder = sortOrder,
            showHidden = showHidden,
        )
    }
}

class SearchFilesUseCase(
    private val fileRepository: FileRepository,
) {
    suspend operator fun invoke(
        query: String,
        path: String = "",
        showHidden: Boolean = false,
    ): Resource<List<FileItem>> {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) {
            return Resource.Error(AppError.ValidationError("query", "Search query cannot be blank"))
        }
        return fileRepository.search(
            query = trimmedQuery,
            path = path.trim().trim('/'),
            showHidden = showHidden,
        )
    }
}

class CreateShareUseCase(
    private val shareRepository: ShareRepository,
) {
    suspend operator fun invoke(params: CreateShareParams): Resource<ShareLink> {
        val normalizedPath = params.relativePath.trim().trim('/')
        val normalizedSlug = params.customSlug?.trim()?.takeIf { it.isNotEmpty() }
        if (normalizedSlug != null) {
            if (normalizedSlug.lowercase() in RESERVED_SLUGS) {
                return Resource.Error(AppError.ReservedSlug)
            }
            if (!SLUG_REGEX.matches(normalizedSlug)) {
                return Resource.Error(AppError.InvalidSlugFormat)
            }
        }
        if (params.expiresInSeconds != null && params.expiresInSeconds <= 0L) {
            return Resource.Error(AppError.ValidationError("expiresInSeconds", "Must be positive"))
        }
        if (params.maxDownloads != null && params.maxDownloads <= 0) {
            return Resource.Error(AppError.ValidationError("maxDownloads", "Must be positive"))
        }
        val normalizedPassword = params.password?.takeIf { it.isNotEmpty() }
        return shareRepository.createShare(
            params.copy(
                relativePath = normalizedPath,
                customSlug = normalizedSlug,
                password = normalizedPassword,
            )
        )
    }

    companion object {
        private val SLUG_REGEX = Regex("^[a-zA-Z0-9_-]{3,64}$")
        val RESERVED_SLUGS = setOf("api", "d", "health", "assets", "robots.txt", "favicon.ico")
    }
}

class GetSharesUseCase(
    private val shareRepository: ShareRepository,
) {
    suspend operator fun invoke(
        path: String? = null,
        activeOnly: Boolean = false,
    ): Resource<List<ShareLink>> = shareRepository.getShares(
        path = path?.trim()?.trim('/'),
        activeOnly = activeOnly,
    )
}

class UpdateShareUseCase(
    private val shareRepository: ShareRepository,
) {
    suspend operator fun invoke(params: UpdateShareParams): Resource<ShareLink> {
        if (params.maxDownloads != null && params.maxDownloads <= 0) {
            return Resource.Error(AppError.ValidationError("maxDownloads", "Must be positive"))
        }
        return shareRepository.updateShare(params)
    }
}

class DeleteShareUseCase(
    private val shareRepository: ShareRepository,
) {
    suspend operator fun invoke(id: String): Resource<Unit> = shareRepository.deleteShare(id)
}

class GetShareQrSvgUseCase(
    private val shareRepository: ShareRepository,
) {
    suspend operator fun invoke(id: String): Resource<String> = shareRepository.getShareQrSvg(id)
}
