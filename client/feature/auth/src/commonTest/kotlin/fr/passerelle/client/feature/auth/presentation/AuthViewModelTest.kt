package fr.passerelle.client.feature.auth.presentation

import app.cash.turbine.test
import fr.passerelle.client.core.designsystem.error.ErrorMessageKey
import fr.passerelle.client.core.domain.error.AppError
import fr.passerelle.client.core.domain.error.Resource
import fr.passerelle.client.core.domain.model.AuthSession
import fr.passerelle.client.core.domain.repository.AuthRepository
import fr.passerelle.client.core.domain.usecase.LoginUseCase
import fr.passerelle.client.core.platform.PlatformActions
import fr.passerelle.client.core.platform.ServerUrlProvider
import fr.passerelle.client.core.platform.ShareOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeAuthRepositoryForVm : AuthRepository {
    var result: Resource<AuthSession> = Resource.Success(
        AuthSession("acc", "ref", 900, "https://s.domaine.fr")
    )
    var lastLoginUsername: String? = null

    override fun observeIsAuthenticated(): Flow<Boolean> = MutableStateFlow(false)
    override suspend fun login(
        serverUrl: String,
        username: String,
        password: String,
        deviceName: String,
    ): Resource<AuthSession> {
        lastLoginUsername = username
        return result
    }

    override suspend fun logout(): Resource<Unit> = Resource.Success(Unit)
}

private class FakeServerUrlProviderForAuth(
    private var url: String = "https://s.domaine.fr",
    override val isServerUrlConfigurable: Boolean = true,
) : ServerUrlProvider {
    override suspend fun getServerUrl(): String = url
    override suspend fun setServerUrl(url: String) { this.url = url }
}

private class FakePlatformActionsForAuth : PlatformActions {
    override val deviceName: String = "Test Device"
    override fun copyToClipboard(text: String) {}
    override suspend fun shareLink(url: String, title: String): ShareOutcome = ShareOutcome.COPIED_TO_CLIPBOARD
    override fun openExternalUrl(url: String) {}
}

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val authRepo = FakeAuthRepositoryForVm()
    private val serverUrlProvider = FakeServerUrlProviderForAuth()
    private val platformActions = FakePlatformActionsForAuth()
    private lateinit var viewModel: AuthViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = AuthViewModel(
            loginUseCase = LoginUseCase(authRepo),
            serverUrlProvider = serverUrlProvider,
            platformActions = platformActions,
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initializes serverUrl from ServerUrlProvider`() = runTest {
        assertEquals("https://s.domaine.fr", viewModel.uiState.value.serverUrl)
        assertTrue(viewModel.uiState.value.isServerUrlEditable)
    }

    @Test
    fun `toggle password visibility flips flag`() = runTest {
        assertFalse(viewModel.uiState.value.isPasswordVisible)
        viewModel.onTogglePasswordVisibility()
        assertTrue(viewModel.uiState.value.isPasswordVisible)
    }

    @Test
    fun `submit with valid credentials emits LoginSucceeded`() = runTest {
        viewModel.onUsernameChanged("admin")
        viewModel.onPasswordChanged("secret")

        viewModel.events.test {
            viewModel.submit()
            assertEquals(AuthEvent.LoginSucceeded, awaitItem())
            assertFalse(viewModel.uiState.value.isLoading)
            assertNull(viewModel.uiState.value.error)
            assertEquals("admin", authRepo.lastLoginUsername)
        }
    }

    @Test
    fun `submit surfaces INVALID_CREDENTIALS and RATE_LIMITED errors`() = runTest {
        authRepo.result = Resource.Error(AppError.InvalidCredentials)
        viewModel.onUsernameChanged("admin")
        viewModel.onPasswordChanged("wrong")

        viewModel.submit()
        assertEquals(ErrorMessageKey.INVALID_CREDENTIALS, viewModel.uiState.value.error?.key)

        authRepo.result = Resource.Error(AppError.RateLimited)
        viewModel.submit()
        assertEquals(ErrorMessageKey.RATE_LIMITED, viewModel.uiState.value.error?.key)
    }
}
