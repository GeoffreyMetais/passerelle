package fr.passerelle.client.core.data

import fr.passerelle.client.core.data.remote.createPasserelleHttpClient
import fr.passerelle.client.core.data.repository.AuthRepositoryImpl
import fr.passerelle.client.core.data.repository.FileRepositoryImpl
import fr.passerelle.client.core.data.repository.ShareRepositoryImpl
import fr.passerelle.client.core.domain.error.AppError
import fr.passerelle.client.core.domain.error.Resource
import fr.passerelle.client.core.domain.model.CreateShareParams
import fr.passerelle.client.core.domain.model.SortDirection
import fr.passerelle.client.core.domain.model.SortOption
import fr.passerelle.client.core.domain.model.UpdateShareParams
import fr.passerelle.client.core.platform.ServerUrlProvider
import fr.passerelle.client.core.platform.SessionStore
import fr.passerelle.client.core.platform.StoredSession
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class InMemorySessionStore(initial: StoredSession? = null) : SessionStore {
    private val _session = MutableStateFlow(initial)
    override val session: StateFlow<StoredSession?> = _session.asStateFlow()
    override fun current(): StoredSession? = _session.value
    override fun save(session: StoredSession) { _session.value = session }
    override fun clear() { _session.value = null }
}

private class FixedServerUrlProvider(private var url: String = "https://s.domaine.fr") : ServerUrlProvider {
    override val isServerUrlConfigurable: Boolean = true
    override suspend fun getServerUrl(): String = url
    override suspend fun setServerUrl(url: String) { this.url = url }
}

class CoreDataRepositoriesTest {

    @Test
    fun `auth repository login serializes camelCase payload and stores tokens`() = runTest {
        var capturedRequest: HttpRequestData? = null
        val engine = MockEngine { request ->
            capturedRequest = request
            respond(
                content = """
                    {
                      "accessToken": "acc_123",
                      "refreshToken": "ref_456",
                      "expiresInSeconds": 900,
                      "publicBaseUrl": "https://s.domaine.fr"
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val sessionStore = InMemorySessionStore()
        val urlProvider = FixedServerUrlProvider()
        val client = createPasserelleHttpClient(engine, urlProvider, sessionStore)
        val repo = AuthRepositoryImpl(client, urlProvider, sessionStore)

        val result = repo.login("https://s.domaine.fr/", "admin", "secret", "PWA Web")

        val session = assertIs<Resource.Success<*>>(result)
        assertEquals("https://s.domaine.fr/api/v1/auth/login", capturedRequest?.url.toString())
        val bodyText = (capturedRequest?.body as TextContent).text
        assertTrue(bodyText.contains(""""username":"admin""""))
        assertTrue(bodyText.contains(""""password":"secret""""))
        assertTrue(bodyText.contains(""""deviceName":"PWA Web""""))
        assertEquals("acc_123", sessionStore.current()?.accessToken)
        assertEquals("ref_456", sessionStore.current()?.refreshToken)
        assertNotNull(session.data)
    }

    @Test
    fun `auth repository maps INVALID_CREDENTIALS and RATE_LIMITED error codes`() = runTest {
        val engine = MockEngine { request ->
            val body = (request.body as TextContent).text
            if (body.contains("bad")) {
                respond(
                    content = """{"code":"INVALID_CREDENTIALS","message":"Identifiants invalides"}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            } else {
                respond(
                    content = """{"code":"RATE_LIMITED","message":"Trop de tentatives"}""",
                    status = HttpStatusCode.TooManyRequests,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }
        }
        val repo = AuthRepositoryImpl(
            createPasserelleHttpClient(engine, FixedServerUrlProvider(), InMemorySessionStore()),
            FixedServerUrlProvider(),
            InMemorySessionStore(),
        )

        val invalid = repo.login("https://s.domaine.fr", "bad", "pass", "Web")
        val limited = repo.login("https://s.domaine.fr", "admin", "pass", "Web")

        assertEquals(AppError.InvalidCredentials, assertIs<Resource.Error>(invalid).error)
        assertEquals(AppError.RateLimited, assertIs<Resource.Error>(limited).error)
    }

    @Test
    fun `bearer auth plugin refreshes expired access token transparently and retries`() = runTest {
        var calls = 0
        val engine = MockEngine { request ->
            calls++
            when (request.url.encodedPath) {
                "/api/v1/files" -> {
                    val authHeader = request.headers[HttpHeaders.Authorization]
                    if (authHeader == "Bearer expired_token") {
                        respond(
                            content = """{"code":"TOKEN_EXPIRED","message":"Expired"}""",
                            status = HttpStatusCode.Unauthorized,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    } else if (authHeader == "Bearer fresh_token") {
                        respond(
                            content = """
                                {
                                  "currentPath": "",
                                  "parentPath": null,
                                  "breadcrumbs": [{"name":"Racine","path":""}],
                                  "entries": []
                                }
                            """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    } else {
                        respond("unauthorized", HttpStatusCode.Unauthorized)
                    }
                }
                "/api/v1/auth/refresh" -> {
                    respond(
                        content = """
                            {
                              "accessToken": "fresh_token",
                              "refreshToken": "fresh_refresh",
                              "expiresInSeconds": 900,
                              "publicBaseUrl": "https://s.domaine.fr"
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
                else -> respond("not found", HttpStatusCode.NotFound)
            }
        }
        val sessionStore = InMemorySessionStore(
            StoredSession("expired_token", "old_refresh", "https://s.domaine.fr")
        )
        val urlProvider = FixedServerUrlProvider()
        val fileRepo = FileRepositoryImpl(
            createPasserelleHttpClient(engine, urlProvider, sessionStore),
            urlProvider,
        )

        val result = fileRepo.browse(path = "", sortBy = SortOption.NAME, sortOrder = SortDirection.ASC)

        assertIs<Resource.Success<*>>(result)
        assertEquals("fresh_token", sessionStore.current()?.accessToken)
        assertEquals("fresh_refresh", sessionStore.current()?.refreshToken)
        assertTrue(calls >= 3)
    }

    @Test
    fun `refresh returning 401 clears stored session`() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/files" -> respond(
                    content = """{"code":"TOKEN_EXPIRED","message":"Expired"}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
                "/api/v1/auth/refresh" -> respond(
                    content = """{"code":"UNAUTHORIZED","message":"Invalid refresh"}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
                else -> respond("not found", HttpStatusCode.NotFound)
            }
        }
        val sessionStore = InMemorySessionStore(
            StoredSession("expired_token", "dead_refresh", "https://s.domaine.fr")
        )
        val urlProvider = FixedServerUrlProvider()
        val fileRepo = FileRepositoryImpl(
            createPasserelleHttpClient(engine, urlProvider, sessionStore),
            urlProvider,
        )

        val result = fileRepo.browse()

        assertIs<Resource.Error>(result)
        assertNull(sessionStore.current())
    }

    @Test
    fun `file repository parses browse search thumbnail and direct download url`() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/files" -> respond(
                    content = """
                        {
                          "currentPath": "Photos/Vacances",
                          "parentPath": "Photos",
                          "breadcrumbs": [
                            { "name": "Racine", "path": "" },
                            { "name": "Photos", "path": "Photos" },
                            { "name": "Vacances", "path": "Photos/Vacances" }
                          ],
                          "entries": [
                            {
                              "name": "plage.jpg",
                              "path": "Photos/Vacances/plage.jpg",
                              "isDirectory": false,
                              "sizeBytes": 3450120,
                              "modifiedAt": "2026-10-14T08:15:30Z",
                              "mimeType": "image/jpeg",
                              "hasThumbnail": true,
                              "activeShareCount": 2
                            }
                          ]
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
                "/api/v1/files/search" -> respond(
                    content = """{"query":"plage","results":[]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
                "/api/v1/files/thumbnail" -> respond(
                    content = byteArrayOf(1, 2, 3, 4),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "image/webp"),
                )
                else -> respond("not found", HttpStatusCode.NotFound)
            }
        }
        val sessionStore = InMemorySessionStore(StoredSession("tok", "ref", "https://s.domaine.fr"))
        val urlProvider = FixedServerUrlProvider()
        val repo = FileRepositoryImpl(createPasserelleHttpClient(engine, urlProvider, sessionStore), urlProvider)

        val browse = assertIs<Resource.Success<*>>(repo.browse("Photos/Vacances")).data as fr.passerelle.client.core.domain.model.DirectoryContent
        assertEquals("Photos", browse.parentPath)
        assertEquals(3, browse.breadcrumbs.size)
        assertEquals(2, browse.entries.first().activeShareCount)

        val search = assertIs<Resource.Success<*>>(repo.search("plage"))
        assertTrue((search.data as List<*>).isEmpty())

        val thumb = assertIs<Resource.Success<ByteArray>>(repo.getThumbnail("Photos/Vacances/plage.jpg"))
        assertEquals(4, thumb.data.size)

        val downloadUrl = repo.getDirectDownloadUrl("Photos/Vacances/plage.jpg", inline = true)
        assertTrue(downloadUrl.contains("/api/v1/files/download"))
        assertTrue(downloadUrl.contains("inline=true"))
    }

    @Test
    fun `share repository maps create get patch delete and qr svg`() = runTest {
        val shareJson = """
            {
              "id": "01936d8a-7c10-7000-8000-000000000001",
              "slug": "vacances-ete",
              "publicUrl": "https://s.domaine.fr/vacances-ete",
              "directDownloadUrl": "https://s.domaine.fr/d/vacances-ete",
              "targetName": "Vacances",
              "relativePath": "Photos/Vacances",
              "isDirectory": true,
              "hasPassword": true,
              "expiresAt": "2026-12-04T09:30:00Z",
              "maxDownloads": 10,
              "downloadCount": 2,
              "isRevoked": false,
              "isActive": true,
              "createdAt": "2026-11-27T09:30:00Z",
              "lastAccessedAt": "2026-11-27T10:00:00Z"
            }
        """.trimIndent()
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath == "/api/v1/shares" && request.method == HttpMethod.Post -> respond(
                    content = shareJson,
                    status = HttpStatusCode.Created,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
                request.url.encodedPath == "/api/v1/shares" && request.method == HttpMethod.Get -> respond(
                    content = """{"shares":[$shareJson]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
                request.url.encodedPath.startsWith("/api/v1/shares/") && request.method == HttpMethod.Patch -> respond(
                    content = shareJson.replace(""""isRevoked": false""", """"isRevoked": true""").replace(""""isActive": true""", """"isActive": false"""),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
                request.url.encodedPath.endsWith("/qr") && request.method == HttpMethod.Get -> respond(
                    content = """<svg viewBox="0 0 21 21"><rect x="0" y="0" width="1" height="1"/></svg>""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "image/svg+xml"),
                )
                request.url.encodedPath.startsWith("/api/v1/shares/") && request.method == HttpMethod.Delete -> respond(
                    content = "",
                    status = HttpStatusCode.NoContent,
                )
                else -> respond("not found", HttpStatusCode.NotFound)
            }
        }
        val sessionStore = InMemorySessionStore(StoredSession("tok", "ref", "https://s.domaine.fr"))
        val urlProvider = FixedServerUrlProvider()
        val repo = ShareRepositoryImpl(createPasserelleHttpClient(engine, urlProvider, sessionStore), urlProvider)

        val created = assertIs<Resource.Success<*>>(
            repo.createShare(CreateShareParams("Photos/Vacances", customSlug = "vacances-ete", expiresInSeconds = 604800, maxDownloads = 10))
        ).data as fr.passerelle.client.core.domain.model.ShareLink
        assertEquals("vacances-ete", created.slug)
        assertTrue(created.isDirectory)

        val list = assertIs<Resource.Success<*>>(repo.getShares(path = "Photos/Vacances", activeOnly = true)).data as List<*>
        assertEquals(1, list.size)

        val updated = assertIs<Resource.Success<*>>(repo.updateShare(UpdateShareParams(created.id, isRevoked = true))).data as fr.passerelle.client.core.domain.model.ShareLink
        assertFalse(updated.isActive)

        val qr = assertIs<Resource.Success<String>>(repo.getShareQrSvg(created.id))
        assertTrue(qr.data.contains("<svg"))

        val deleted = repo.deleteShare(created.id)
        assertIs<Resource.Success<Unit>>(deleted)
    }

    @Test
    fun `repositories support subpath baseUrl for login files shares and token refresh`() = runTest {
        val capturedUrls = mutableListOf<String>()
        val engine = MockEngine { request ->
            val fullUrl = request.url.toString()
            capturedUrls.add("${request.method.value} $fullUrl")
            when {
                fullUrl == "https://partage.exemple.fr/passerelle-api/api/v1/auth/login" &&
                    request.method == HttpMethod.Post -> respond(
                    content = """
                        {
                          "accessToken": "expired_subpath_token",
                          "refreshToken": "subpath_refresh_1",
                          "expiresInSeconds": 900,
                          "publicBaseUrl": "https://partage.exemple.fr/passerelle-api"
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
                fullUrl.startsWith("https://partage.exemple.fr/passerelle-api/api/v1/files") &&
                    request.method == HttpMethod.Get -> {
                    val authHeader = request.headers[HttpHeaders.Authorization]
                    if (authHeader == "Bearer expired_subpath_token") {
                        respond(
                            content = """{"code":"TOKEN_EXPIRED","message":"Expired"}""",
                            status = HttpStatusCode.Unauthorized,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    } else if (authHeader == "Bearer fresh_subpath_token") {
                        respond(
                            content = """
                                {
                                  "currentPath": "",
                                  "parentPath": null,
                                  "breadcrumbs": [{"name":"Racine","path":""}],
                                  "entries": []
                                }
                            """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    } else {
                        respond("unauthorized", HttpStatusCode.Unauthorized)
                    }
                }
                fullUrl == "https://partage.exemple.fr/passerelle-api/api/v1/auth/refresh" &&
                    request.method == HttpMethod.Post -> respond(
                    content = """
                        {
                          "accessToken": "fresh_subpath_token",
                          "refreshToken": "subpath_refresh_2",
                          "expiresInSeconds": 900,
                          "publicBaseUrl": "https://partage.exemple.fr/passerelle-api"
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
                fullUrl == "https://partage.exemple.fr/passerelle-api/api/v1/shares" &&
                    request.method == HttpMethod.Post -> respond(
                    content = """
                        {
                          "id": "01936d8a-7c10-7000-8000-000000000009",
                          "slug": "subpath-doc",
                          "publicUrl": "https://partage.exemple.fr/passerelle-api/subpath-doc",
                          "directDownloadUrl": "https://partage.exemple.fr/passerelle-api/d/subpath-doc",
                          "targetName": "readme.txt",
                          "relativePath": "readme.txt",
                          "isDirectory": false,
                          "hasPassword": false,
                          "expiresAt": null,
                          "maxDownloads": null,
                          "downloadCount": 0,
                          "isRevoked": false,
                          "isActive": true,
                          "createdAt": "2026-11-27T09:30:00Z",
                          "lastAccessedAt": null
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.Created,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
                else -> respond("not found: $fullUrl", HttpStatusCode.NotFound)
            }
        }

        val sessionStore = InMemorySessionStore()
        val urlProvider = FixedServerUrlProvider("https://partage.exemple.fr/passerelle-api")
        val client = createPasserelleHttpClient(engine, urlProvider, sessionStore)
        val authRepo = AuthRepositoryImpl(client, urlProvider, sessionStore)
        val fileRepo = FileRepositoryImpl(client, urlProvider)
        val shareRepo = ShareRepositoryImpl(client, urlProvider)

        val loginRes = authRepo.login(
            serverUrl = "https://partage.exemple.fr/passerelle-api/",
            username = "admin",
            password = "secret",
            deviceName = "PWA Web",
        )
        assertIs<Resource.Success<*>>(loginRes)

        val filesRes = fileRepo.browse(path = "", sortBy = SortOption.NAME, sortOrder = SortDirection.ASC)
        assertIs<Resource.Success<*>>(filesRes)

        val shareRes = shareRepo.createShare(CreateShareParams(relativePath = "readme.txt", customSlug = "subpath-doc"))
        val createdShare = assertIs<Resource.Success<*>>(shareRes).data as fr.passerelle.client.core.domain.model.ShareLink
        assertEquals("https://partage.exemple.fr/passerelle-api/subpath-doc", createdShare.publicUrl)

        assertTrue(capturedUrls.any { it == "POST https://partage.exemple.fr/passerelle-api/api/v1/auth/login" })
        assertTrue(capturedUrls.any { it.startsWith("GET https://partage.exemple.fr/passerelle-api/api/v1/files") })
        assertTrue(capturedUrls.any { it == "POST https://partage.exemple.fr/passerelle-api/api/v1/auth/refresh" })
        assertTrue(capturedUrls.any { it == "POST https://partage.exemple.fr/passerelle-api/api/v1/shares" })
    }
}
