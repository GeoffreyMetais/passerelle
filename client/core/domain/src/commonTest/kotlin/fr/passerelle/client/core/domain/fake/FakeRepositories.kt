package fr.passerelle.client.core.domain.fake

import fr.passerelle.client.core.domain.error.AppError
import fr.passerelle.client.core.domain.error.Resource
import fr.passerelle.client.core.domain.model.AuthSession
import fr.passerelle.client.core.domain.model.Breadcrumb
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.Instant

class FakeAuthRepository : AuthRepository {
    private val authState = MutableStateFlow(false)
    var nextLoginResult: Resource<AuthSession> = Resource.Success(
        AuthSession(
            accessToken = "access-token",
            refreshToken = "refresh-token",
            expiresInSeconds = 900L,
            publicBaseUrl = "https://s.domaine.fr",
        )
    )
    var nextLogoutResult: Resource<Unit> = Resource.Success(Unit)
    var lastLoginCall: LoginCall? = null
    var logoutCalled = false

    data class LoginCall(
        val serverUrl: String,
        val username: String,
        val password: String,
        val deviceName: String,
    )

    fun setAuthenticated(authenticated: Boolean) {
        authState.value = authenticated
    }

    override fun observeIsAuthenticated(): Flow<Boolean> = authState

    override suspend fun login(
        serverUrl: String,
        username: String,
        password: String,
        deviceName: String,
    ): Resource<AuthSession> {
        lastLoginCall = LoginCall(serverUrl, username, password, deviceName)
        if (nextLoginResult is Resource.Success) {
            authState.value = true
        }
        return nextLoginResult
    }

    override suspend fun logout(): Resource<Unit> {
        logoutCalled = true
        authState.value = false
        return nextLogoutResult
    }
}

class FakeFileRepository : FileRepository {
    var nextBrowseResult: Resource<DirectoryContent> = Resource.Success(
        DirectoryContent(
            currentPath = "",
            parentPath = null,
            breadcrumbs = listOf(Breadcrumb("Racine", "")),
            entries = emptyList(),
        )
    )
    var nextSearchResult: Resource<List<FileItem>> = Resource.Success(emptyList())
    var lastBrowseQuery: BrowseQuery? = null
    var lastSearchQuery: SearchQuery? = null

    data class BrowseQuery(
        val path: String,
        val sortBy: SortOption,
        val sortOrder: SortDirection,
        val showHidden: Boolean,
    )

    data class SearchQuery(
        val query: String,
        val path: String,
        val showHidden: Boolean,
    )

    override suspend fun browse(
        path: String,
        sortBy: SortOption,
        sortOrder: SortDirection,
        showHidden: Boolean,
    ): Resource<DirectoryContent> {
        lastBrowseQuery = BrowseQuery(path, sortBy, sortOrder, showHidden)
        return nextBrowseResult
    }

    override suspend fun search(
        query: String,
        path: String,
        showHidden: Boolean,
    ): Resource<List<FileItem>> {
        lastSearchQuery = SearchQuery(query, path, showHidden)
        return nextSearchResult
    }

    override suspend fun getThumbnail(path: String): Resource<ByteArray> =
        Resource.Error(AppError.NotFound)

    override suspend fun getDirectDownloadUrl(path: String, inline: Boolean): String =
        "http://localhost:8080/api/v1/files/download?path=$path"
}

class FakeShareRepository : ShareRepository {
    val shares = mutableListOf<ShareLink>()
    var nextCreateError: AppError? = null
    var lastCreateParams: CreateShareParams? = null
    var lastUpdateParams: UpdateShareParams? = null
    var lastDeletedId: String? = null

    override suspend fun createShare(params: CreateShareParams): Resource<ShareLink> {
        lastCreateParams = params
        nextCreateError?.let { return Resource.Error(it) }
        val slug = params.customSlug ?: "abc12345"
        val created = ShareLink(
            id = "share-${shares.size + 1}",
            slug = slug,
            publicUrl = "https://s.domaine.fr/$slug",
            directDownloadUrl = "https://s.domaine.fr/d/$slug",
            targetName = params.relativePath.substringAfterLast('/').ifEmpty { "Racine" },
            relativePath = params.relativePath,
            isDirectory = false,
            hasPassword = !params.password.isNullOrEmpty(),
            expiresAt = null,
            maxDownloads = params.maxDownloads,
            downloadCount = 0,
            isRevoked = false,
            isActive = true,
            createdAt = Instant.parse("2026-11-27T09:30:00Z"),
            lastAccessedAt = null,
        )
        shares.add(created)
        return Resource.Success(created)
    }

    override suspend fun getShares(path: String?, activeOnly: Boolean): Resource<List<ShareLink>> {
        val filtered = shares.filter { share ->
            (path == null || share.relativePath == path) &&
                (!activeOnly || share.isActive)
        }
        return Resource.Success(filtered)
    }

    override suspend fun updateShare(params: UpdateShareParams): Resource<ShareLink> {
        lastUpdateParams = params
        val index = shares.indexOfFirst { it.id == params.id }
        if (index == -1) return Resource.Error(AppError.NotFound)
        val existing = shares[index]
        val updated = existing.copy(
            isRevoked = params.isRevoked ?: existing.isRevoked,
            isActive = !(params.isRevoked ?: existing.isRevoked),
            expiresAt = if (params.clearExpiration) null else params.expiresAt ?: existing.expiresAt,
            maxDownloads = if (params.clearMaxDownloads) null else params.maxDownloads ?: existing.maxDownloads,
            hasPassword = if (params.clearPassword) false else if (params.newPassword != null) true else existing.hasPassword,
        )
        shares[index] = updated
        return Resource.Success(updated)
    }

    override suspend fun deleteShare(id: String): Resource<Unit> {
        lastDeletedId = id
        val removed = shares.removeAll { it.id == id }
        return if (removed) Resource.Success(Unit) else Resource.Error(AppError.NotFound)
    }

    override suspend fun getShareQrSvg(id: String): Resource<String> =
        Resource.Success("<svg viewBox=\"0 0 21 21\"><rect x=\"0\" y=\"0\" width=\"1\" height=\"1\"/></svg>")
}
