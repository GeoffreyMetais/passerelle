package fr.passerelle.client.core.domain.usecase

import app.cash.turbine.test
import fr.passerelle.client.core.domain.error.AppError
import fr.passerelle.client.core.domain.error.Resource
import fr.passerelle.client.core.domain.fake.FakeAuthRepository
import fr.passerelle.client.core.domain.fake.FakeFileRepository
import fr.passerelle.client.core.domain.fake.FakeShareRepository
import fr.passerelle.client.core.domain.model.CreateShareParams
import fr.passerelle.client.core.domain.model.SortDirection
import fr.passerelle.client.core.domain.model.SortOption
import fr.passerelle.client.core.domain.model.UpdateShareParams
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthUseCasesTest {

    private val authRepository = FakeAuthRepository()
    private val loginUseCase = LoginUseCase(authRepository)
    private val logoutUseCase = LogoutUseCase(authRepository)
    private val observeAuthStateUseCase = ObserveAuthStateUseCase(authRepository)

    @Test
    fun `login trims url and username and delegates to repository`() = runTest {
        val result = loginUseCase(
            serverUrl = " https://s.domaine.fr/ ",
            username = " admin ",
            password = "secret",
            deviceName = "Android",
        )

        assertIs<Resource.Success<*>>(result)
        assertEquals("https://s.domaine.fr", authRepository.lastLoginCall?.serverUrl)
        assertEquals("admin", authRepository.lastLoginCall?.username)
    }

    @Test
    fun `login rejects blank server url before network call`() = runTest {
        val result = loginUseCase(
            serverUrl = "   ",
            username = "admin",
            password = "secret",
            deviceName = "Android",
        )

        val error = assertIs<Resource.Error>(result)
        val validation = assertIs<AppError.ValidationError>(error.error)
        assertEquals("serverUrl", validation.field)
        assertNull(authRepository.lastLoginCall)
    }

    @Test
    fun `login rejects invalid scheme server url`() = runTest {
        val result = loginUseCase(
            serverUrl = "ftp://s.domaine.fr",
            username = "admin",
            password = "secret",
            deviceName = "Android",
        )

        val error = assertIs<Resource.Error>(result)
        val validation = assertIs<AppError.ValidationError>(error.error)
        assertEquals("serverUrl", validation.field)
        assertNull(authRepository.lastLoginCall)
    }

    @Test
    fun `login rejects blank username or password`() = runTest {
        val missingUser = loginUseCase("https://s.domaine.fr", "", "secret", "Web")
        val missingPass = loginUseCase("https://s.domaine.fr", "admin", "", "Web")

        assertIs<AppError.ValidationError>(assertIs<Resource.Error>(missingUser).error)
        assertIs<AppError.ValidationError>(assertIs<Resource.Error>(missingPass).error)
        assertNull(authRepository.lastLoginCall)
    }

    @Test
    fun `observe auth state and logout update state`() = runTest {
        observeAuthStateUseCase().test {
            assertFalse(awaitItem())
            loginUseCase("https://s.domaine.fr", "admin", "secret", "Web")
            assertTrue(awaitItem())
            logoutUseCase()
            assertFalse(awaitItem())
        }
    }
}

class FileUseCasesTest {

    private val fileRepository = FakeFileRepository()
    private val browseDirectoryUseCase = BrowseDirectoryUseCase(fileRepository)
    private val searchFilesUseCase = SearchFilesUseCase(fileRepository)

    @Test
    fun `browse delegates normalized path and options`() = runTest {
        val result = browseDirectoryUseCase(
            path = "/Photos/Vacances/",
            sortBy = SortOption.SIZE,
            sortOrder = SortDirection.DESC,
            showHidden = true,
        )

        assertIs<Resource.Success<*>>(result)
        assertEquals("Photos/Vacances", fileRepository.lastBrowseQuery?.path)
        assertEquals(SortOption.SIZE, fileRepository.lastBrowseQuery?.sortBy)
        assertEquals(SortDirection.DESC, fileRepository.lastBrowseQuery?.sortOrder)
        assertTrue(fileRepository.lastBrowseQuery?.showHidden == true)
    }

    @Test
    fun `search rejects blank query before calling repository`() = runTest {
        val result = searchFilesUseCase(query = "   ")

        val error = assertIs<Resource.Error>(result)
        assertIs<AppError.ValidationError>(error.error)
        assertNull(fileRepository.lastSearchQuery)
    }

    @Test
    fun `search trims query and delegates`() = runTest {
        val result = searchFilesUseCase(query = " plage ", path = "/Photos/")

        assertIs<Resource.Success<*>>(result)
        assertEquals("plage", fileRepository.lastSearchQuery?.query)
        assertEquals("Photos", fileRepository.lastSearchQuery?.path)
    }
}

class ShareUseCasesTest {

    private val shareRepository = FakeShareRepository()
    private val createShareUseCase = CreateShareUseCase(shareRepository)
    private val getSharesUseCase = GetSharesUseCase(shareRepository)
    private val updateShareUseCase = UpdateShareUseCase(shareRepository)
    private val deleteShareUseCase = DeleteShareUseCase(shareRepository)

    @Test
    fun `create share rejects invalid custom slug format`() = runTest {
        val tooShort = createShareUseCase(CreateShareParams("file.jpg", customSlug = "ab"))
        val invalidChars = createShareUseCase(CreateShareParams("file.jpg", customSlug = "my slug!"))

        assertEquals(AppError.InvalidSlugFormat, assertIs<Resource.Error>(tooShort).error)
        assertEquals(AppError.InvalidSlugFormat, assertIs<Resource.Error>(invalidChars).error)
        assertNull(shareRepository.lastCreateParams)
    }

    @Test
    fun `create share rejects reserved slugs case-insensitively`() = runTest {
        for (reserved in listOf("api", "API", "d", "health", "assets", "robots.txt", "favicon.ico")) {
            val result = createShareUseCase(CreateShareParams("file.jpg", customSlug = reserved))
            assertEquals(AppError.ReservedSlug, assertIs<Resource.Error>(result).error)
        }
        assertNull(shareRepository.lastCreateParams)
    }

    @Test
    fun `create share rejects non positive expiresInSeconds or maxDownloads`() = runTest {
        val badExpiry = createShareUseCase(CreateShareParams("file.jpg", expiresInSeconds = 0L))
        val badQuota = createShareUseCase(CreateShareParams("file.jpg", maxDownloads = 0))

        assertIs<AppError.ValidationError>(assertIs<Resource.Error>(badExpiry).error)
        assertIs<AppError.ValidationError>(assertIs<Resource.Error>(badQuota).error)
        assertNull(shareRepository.lastCreateParams)
    }

    @Test
    fun `create share normalizes blank optional fields to null and succeeds`() = runTest {
        val result = createShareUseCase(
            CreateShareParams(
                relativePath = "/Photos/Vacances/",
                customSlug = "   ",
                password = "",
                expiresInSeconds = 3600L,
                maxDownloads = 5,
            )
        )

        val success = assertIs<Resource.Success<*>>(result)
        assertEquals("Photos/Vacances", shareRepository.lastCreateParams?.relativePath)
        assertNull(shareRepository.lastCreateParams?.customSlug)
        assertNull(shareRepository.lastCreateParams?.password)
        assertEquals(1, (getSharesUseCase() as Resource.Success).data.size)
        assertTrue(success.data != null)
    }

    @Test
    fun `update and delete share work through repository`() = runTest {
        val created = (createShareUseCase(CreateShareParams("a.txt", customSlug = "mon-lien")) as Resource.Success).data
        val updated = updateShareUseCase(UpdateShareParams(id = created.id, isRevoked = true))
        assertIs<Resource.Success<*>>(updated)
        assertFalse((updated as Resource.Success).data.isActive)

        val deleted = deleteShareUseCase(created.id)
        assertIs<Resource.Success<Unit>>(deleted)
        assertTrue((getSharesUseCase() as Resource.Success).data.isEmpty())
    }
}
