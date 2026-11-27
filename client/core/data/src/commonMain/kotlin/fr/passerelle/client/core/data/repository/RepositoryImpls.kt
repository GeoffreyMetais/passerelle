package fr.passerelle.client.core.data.repository

import fr.passerelle.client.core.data.mapper.toDomain
import fr.passerelle.client.core.data.mapper.toDto
import fr.passerelle.client.core.data.remote.dto.AuthSessionDto
import fr.passerelle.client.core.data.remote.dto.DirectoryContentDto
import fr.passerelle.client.core.data.remote.dto.LoginRequestDto
import fr.passerelle.client.core.data.remote.dto.SearchResponseDto
import fr.passerelle.client.core.data.remote.dto.ShareDto
import fr.passerelle.client.core.data.remote.dto.SharesResponseDto
import fr.passerelle.client.core.data.remote.safeApiCall
import fr.passerelle.client.core.domain.error.AppError
import fr.passerelle.client.core.domain.error.Resource
import fr.passerelle.client.core.domain.error.onSuccess
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
import fr.passerelle.client.core.platform.ServerUrlProvider
import fr.passerelle.client.core.platform.SessionStore
import fr.passerelle.client.core.platform.StoredSession
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.URLBuilder
import io.ktor.http.contentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class AuthRepositoryImpl(
    private val httpClient: HttpClient,
    private val serverUrlProvider: ServerUrlProvider,
    private val sessionStore: SessionStore,
) : AuthRepository {

    override fun observeIsAuthenticated(): Flow<Boolean> =
        sessionStore.session.map { it != null }

    override suspend fun login(
        serverUrl: String,
        username: String,
        password: String,
        deviceName: String,
    ): Resource<AuthSession> {
        val normalizedUrl = serverUrl.trim().trimEnd('/')
        serverUrlProvider.setServerUrl(normalizedUrl)
        return safeApiCall {
            httpClient.post("$normalizedUrl/api/v1/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(
                    LoginRequestDto(
                        username = username,
                        password = password,
                        deviceName = deviceName,
                    )
                )
            }.body<AuthSessionDto>().toDomain()
        }.onSuccess { session ->
            sessionStore.save(
                StoredSession(
                    accessToken = session.accessToken,
                    refreshToken = session.refreshToken,
                    publicBaseUrl = session.publicBaseUrl,
                )
            )
        }
    }

    override suspend fun logout(): Resource<Unit> {
        val baseUrl = serverUrlProvider.getServerUrl().trimEnd('/')
        val result = if (baseUrl.isNotEmpty() && sessionStore.current() != null) {
            safeApiCall<Unit> {
                httpClient.delete("$baseUrl/api/v1/auth/logout")
            }
        } else {
            Resource.Success(Unit)
        }
        sessionStore.clear()
        return when (result) {
            is Resource.Success -> Resource.Success(Unit)
            is Resource.Error -> if (result.error is AppError.Unauthorized || result.error is AppError.TokenExpired) {
                Resource.Success(Unit)
            } else {
                result
            }
        }
    }
}

internal class FileRepositoryImpl(
    private val httpClient: HttpClient,
    private val serverUrlProvider: ServerUrlProvider,
) : FileRepository {

    override suspend fun browse(
        path: String,
        sortBy: SortOption,
        sortOrder: SortDirection,
        showHidden: Boolean,
    ): Resource<DirectoryContent> {
        val baseUrl = serverUrlProvider.getServerUrl().trimEnd('/')
        return safeApiCall {
            httpClient.get("$baseUrl/api/v1/files") {
                parameter("path", path)
                parameter("sortBy", sortBy.apiValue)
                parameter("sortOrder", sortOrder.apiValue)
                parameter("showHidden", showHidden)
            }.body<DirectoryContentDto>().toDomain()
        }
    }

    override suspend fun search(
        query: String,
        path: String,
        showHidden: Boolean,
    ): Resource<List<FileItem>> {
        val baseUrl = serverUrlProvider.getServerUrl().trimEnd('/')
        return safeApiCall {
            httpClient.get("$baseUrl/api/v1/files/search") {
                parameter("q", query)
                parameter("path", path)
                parameter("showHidden", showHidden)
            }.body<SearchResponseDto>().results.map { it.toDomain() }
        }
    }

    override suspend fun getThumbnail(path: String): Resource<ByteArray> {
        val baseUrl = serverUrlProvider.getServerUrl().trimEnd('/')
        return safeApiCall {
            httpClient.get("$baseUrl/api/v1/files/thumbnail") {
                parameter("path", path)
            }.bodyAsBytes()
        }
    }

    override suspend fun getDirectDownloadUrl(path: String, inline: Boolean): String {
        val baseUrl = serverUrlProvider.getServerUrl().trimEnd('/')
        return URLBuilder("$baseUrl/api/v1/files/download").apply {
            parameters.append("path", path)
            if (inline) {
                parameters.append("inline", "true")
            }
        }.buildString()
    }
}

internal class ShareRepositoryImpl(
    private val httpClient: HttpClient,
    private val serverUrlProvider: ServerUrlProvider,
) : ShareRepository {

    override suspend fun createShare(params: CreateShareParams): Resource<ShareLink> {
        val baseUrl = serverUrlProvider.getServerUrl().trimEnd('/')
        return safeApiCall {
            httpClient.post("$baseUrl/api/v1/shares") {
                contentType(ContentType.Application.Json)
                setBody(params.toDto())
            }.body<ShareDto>().toDomain()
        }
    }

    override suspend fun getShares(path: String?, activeOnly: Boolean): Resource<List<ShareLink>> {
        val baseUrl = serverUrlProvider.getServerUrl().trimEnd('/')
        return safeApiCall {
            httpClient.get("$baseUrl/api/v1/shares") {
                if (path != null) parameter("path", path)
                parameter("activeOnly", activeOnly)
            }.body<SharesResponseDto>().shares.map { it.toDomain() }
        }
    }

    override suspend fun updateShare(params: UpdateShareParams): Resource<ShareLink> {
        val baseUrl = serverUrlProvider.getServerUrl().trimEnd('/')
        return safeApiCall {
            httpClient.patch("$baseUrl/api/v1/shares/${params.id}") {
                contentType(ContentType.Application.Json)
                setBody(params.toDto())
            }.body<ShareDto>().toDomain()
        }
    }

    override suspend fun deleteShare(id: String): Resource<Unit> {
        val baseUrl = serverUrlProvider.getServerUrl().trimEnd('/')
        return safeApiCall {
            httpClient.delete("$baseUrl/api/v1/shares/$id")
            Unit
        }
    }

    override suspend fun getShareQrSvg(id: String): Resource<String> {
        val baseUrl = serverUrlProvider.getServerUrl().trimEnd('/')
        return safeApiCall {
            httpClient.get("$baseUrl/api/v1/shares/$id/qr").bodyAsText()
        }
    }
}
