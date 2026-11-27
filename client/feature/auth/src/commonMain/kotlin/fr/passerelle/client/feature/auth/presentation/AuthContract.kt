package fr.passerelle.client.feature.auth.presentation

import fr.passerelle.client.core.designsystem.error.UiError
import fr.passerelle.client.core.domain.error.AppError

internal fun AppError.toAuthUiError(): UiError = when (this) {
    AppError.InvalidCredentials -> UiError(fr.passerelle.client.core.designsystem.error.ErrorMessageKey.INVALID_CREDENTIALS)
    AppError.Unauthorized, AppError.TokenExpired -> UiError(fr.passerelle.client.core.designsystem.error.ErrorMessageKey.UNAUTHORIZED)
    AppError.RateLimited -> UiError(fr.passerelle.client.core.designsystem.error.ErrorMessageKey.RATE_LIMITED)
    AppError.NetworkError -> UiError(fr.passerelle.client.core.designsystem.error.ErrorMessageKey.NETWORK_ERROR)
    AppError.TimeoutError -> UiError(fr.passerelle.client.core.designsystem.error.ErrorMessageKey.TIMEOUT_ERROR)
    is AppError.ServerError -> UiError(fr.passerelle.client.core.designsystem.error.ErrorMessageKey.SERVER_ERROR, statusCode)
    is AppError.ValidationError -> when (field) {
        "serverUrl" -> UiError(fr.passerelle.client.core.designsystem.error.ErrorMessageKey.INVALID_SERVER_URL)
        "username", "password" -> UiError(fr.passerelle.client.core.designsystem.error.ErrorMessageKey.REQUIRED_FIELDS)
        else -> UiError(fr.passerelle.client.core.designsystem.error.ErrorMessageKey.GENERIC)
    }
    else -> UiError(fr.passerelle.client.core.designsystem.error.ErrorMessageKey.GENERIC)
}

data class AuthUiState(
    val serverUrl: String = "",
    val isServerUrlEditable: Boolean = true,
    val username: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val error: UiError? = null,
)

sealed interface AuthEvent {
    data object LoginSucceeded : AuthEvent
}
