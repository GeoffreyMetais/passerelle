package fr.passerelle.client.core.domain.usecase

import app.cash.turbine.test
import fr.passerelle.client.core.domain.error.AppError
import fr.passerelle.client.core.domain.error.Resource
import fr.passerelle.client.core.domain.fake.FakeAuthRepository
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
