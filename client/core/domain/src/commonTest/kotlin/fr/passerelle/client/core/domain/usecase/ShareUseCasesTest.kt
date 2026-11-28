package fr.passerelle.client.core.domain.usecase

import fr.passerelle.client.core.domain.error.AppError
import fr.passerelle.client.core.domain.error.Resource
import fr.passerelle.client.core.domain.fake.FakeShareRepository
import fr.passerelle.client.core.domain.model.CreateShareParams
import fr.passerelle.client.core.domain.model.UpdateShareParams
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
