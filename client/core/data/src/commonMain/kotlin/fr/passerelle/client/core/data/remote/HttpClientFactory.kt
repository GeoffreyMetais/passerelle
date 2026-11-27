package fr.passerelle.client.core.data.remote

import fr.passerelle.client.core.data.remote.dto.ApiErrorDto
import fr.passerelle.client.core.data.remote.dto.AuthSessionDto
import fr.passerelle.client.core.data.remote.dto.RefreshRequestDto
import fr.passerelle.client.core.domain.error.AppError
import fr.passerelle.client.core.domain.error.Resource
import fr.passerelle.client.core.platform.ServerUrlProvider
import fr.passerelle.client.core.platform.SessionStore
import fr.passerelle.client.core.platform.StoredSession
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

internal val PasserelleJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}

internal expect fun createDefaultHttpClientEngine(): HttpClientEngine

internal fun createPasserelleHttpClient(
    engine: HttpClientEngine = createDefaultHttpClientEngine(),
    serverUrlProvider: ServerUrlProvider,
    sessionStore: SessionStore,
): HttpClient = HttpClient(engine) {
    expectSuccess = true

    install(ContentNegotiation) {
        json(PasserelleJson)
    }

    install(HttpTimeout) {
        requestTimeoutMillis = 30_000
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 30_000
    }

    install(Auth) {
        bearer {
            loadTokens {
                sessionStore.current()?.let { stored ->
                    BearerTokens(stored.accessToken, stored.refreshToken)
                }
            }

            refreshTokens {
                val refreshToken = oldTokens?.refreshToken
                    ?: sessionStore.current()?.refreshToken
                    ?: return@refreshTokens null
                val baseUrl = serverUrlProvider.getServerUrl().trimEnd('/')
                if (baseUrl.isEmpty()) return@refreshTokens null

                try {
                    val refreshed = client.post("$baseUrl/api/v1/auth/refresh") {
                        markAsRefreshTokenRequest()
                        contentType(ContentType.Application.Json)
                        setBody(RefreshRequestDto(refreshToken = refreshToken))
                    }.body<AuthSessionDto>()

                    sessionStore.save(
                        StoredSession(
                            accessToken = refreshed.accessToken,
                            refreshToken = refreshed.refreshToken,
                            publicBaseUrl = refreshed.publicBaseUrl,
                        )
                    )
                    BearerTokens(refreshed.accessToken, refreshed.refreshToken)
                } catch (e: ClientRequestException) {
                    if (e.response.status == HttpStatusCode.Unauthorized) {
                        sessionStore.clear()
                    }
                    null
                } catch (_: Throwable) {
                    null
                }
            }

            sendWithoutRequest { request ->
                val path = request.url.encodedPath
                !path.endsWith("/api/v1/auth/login") &&
                    !path.endsWith("/api/v1/auth/refresh") &&
                    !path.endsWith("/health")
            }
        }
    }
}

internal suspend fun <T> safeApiCall(block: suspend () -> T): Resource<T> {
    return try {
        Resource.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: ResponseException) {
        val rawBody = runCatching { e.response.bodyAsText() }.getOrNull()
        val parsed = rawBody?.let {
            runCatching { PasserelleJson.decodeFromString<ApiErrorDto>(it) }.getOrNull()
        }
        Resource.Error(mapApiError(e.response.status, parsed))
    } catch (_: HttpRequestTimeoutException) {
        Resource.Error(AppError.TimeoutError)
    } catch (e: Throwable) {
        Resource.Error(
            if (e is ServerResponseException) {
                AppError.ServerError(e.response.status.value, e.message)
            } else {
                AppError.NetworkError
            }
        )
    }
}

internal fun mapApiError(status: HttpStatusCode, dto: ApiErrorDto?): AppError {
    if (dto != null) {
        when (dto.code) {
            "INVALID_CREDENTIALS" -> return AppError.InvalidCredentials
            "UNAUTHORIZED" -> return AppError.Unauthorized
            "TOKEN_EXPIRED" -> return AppError.TokenExpired
            "FORBIDDEN_PATH" -> return AppError.ForbiddenPath
            "NOT_FOUND" -> return AppError.NotFound
            "SLUG_ALREADY_EXISTS" -> return AppError.SlugAlreadyExists
            "RESERVED_SLUG" -> return AppError.ReservedSlug
            "PAYLOAD_TOO_LARGE" -> return AppError.PayloadTooLarge
            "SHARE_EXPIRED" -> return AppError.ShareExpired
            "SHARE_REVOKED" -> return AppError.ShareRevoked
            "QUOTA_EXCEEDED" -> return AppError.QuotaExceeded
            "INVALID_PASSWORD" -> return AppError.InvalidPassword
            "RATE_LIMITED" -> return AppError.RateLimited
            "INTERNAL_ERROR" -> return AppError.ServerError(status.value, dto.message)
        }
    }
    return when (status) {
        HttpStatusCode.Unauthorized -> AppError.Unauthorized
        HttpStatusCode.Forbidden -> AppError.ForbiddenPath
        HttpStatusCode.NotFound -> AppError.NotFound
        HttpStatusCode.Conflict -> AppError.SlugAlreadyExists
        HttpStatusCode.PayloadTooLarge -> AppError.PayloadTooLarge
        HttpStatusCode.TooManyRequests -> AppError.RateLimited
        else -> AppError.ServerError(status.value, dto?.message)
    }
}
