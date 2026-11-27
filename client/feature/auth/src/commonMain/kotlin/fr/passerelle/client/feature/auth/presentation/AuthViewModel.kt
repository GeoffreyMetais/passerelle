package fr.passerelle.client.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.passerelle.client.core.domain.error.onError
import fr.passerelle.client.core.domain.error.onSuccess
import fr.passerelle.client.core.domain.usecase.LoginUseCase
import fr.passerelle.client.core.platform.PlatformActions
import fr.passerelle.client.core.platform.ServerUrlProvider
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(
    private val loginUseCase: LoginUseCase,
    private val serverUrlProvider: ServerUrlProvider,
    private val platformActions: PlatformActions,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AuthUiState(isServerUrlEditable = serverUrlProvider.isServerUrlConfigurable)
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AuthEvent>(replay = 0, extraBufferCapacity = 16)
    val events: SharedFlow<AuthEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            val savedUrl = serverUrlProvider.getServerUrl()
            _uiState.update {
                it.copy(
                    serverUrl = savedUrl,
                    isServerUrlEditable = serverUrlProvider.isServerUrlConfigurable,
                )
            }
        }
    }

    fun onServerUrlChanged(value: String) {
        _uiState.update { it.copy(serverUrl = value, error = null) }
    }

    fun onUsernameChanged(value: String) {
        _uiState.update { it.copy(username = value, error = null) }
    }

    fun onPasswordChanged(value: String) {
        _uiState.update { it.copy(password = value, error = null) }
    }

    fun onTogglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun submit() {
        val current = _uiState.value
        if (current.isLoading) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val resolvedUrl = current.serverUrl.ifBlank { serverUrlProvider.getServerUrl() }
            loginUseCase(
                serverUrl = resolvedUrl,
                username = current.username,
                password = current.password,
                deviceName = platformActions.deviceName,
            ).onSuccess {
                _uiState.update { it.copy(isLoading = false, password = "", error = null) }
                _events.emit(AuthEvent.LoginSucceeded)
            }.onError { appError ->
                _uiState.update { it.copy(isLoading = false, error = appError.toAuthUiError()) }
            }
        }
    }
}
