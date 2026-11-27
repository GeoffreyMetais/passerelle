package fr.passerelle.client.core.designsystem.error

import androidx.compose.runtime.Composable
import fr.passerelle.client.core.designsystem.resources.Res
import fr.passerelle.client.core.designsystem.resources.error_forbidden_path
import fr.passerelle.client.core.designsystem.resources.error_generic
import fr.passerelle.client.core.designsystem.resources.error_invalid_credentials
import fr.passerelle.client.core.designsystem.resources.error_invalid_server_url
import fr.passerelle.client.core.designsystem.resources.error_invalid_slug
import fr.passerelle.client.core.designsystem.resources.error_network
import fr.passerelle.client.core.designsystem.resources.error_not_found
import fr.passerelle.client.core.designsystem.resources.error_rate_limited
import fr.passerelle.client.core.designsystem.resources.error_required_fields
import fr.passerelle.client.core.designsystem.resources.error_reserved_slug
import fr.passerelle.client.core.designsystem.resources.error_server
import fr.passerelle.client.core.designsystem.resources.error_slug_exists
import fr.passerelle.client.core.designsystem.resources.error_timeout
import fr.passerelle.client.core.designsystem.resources.error_unauthorized
import org.jetbrains.compose.resources.stringResource

enum class ErrorMessageKey {
    INVALID_CREDENTIALS,
    UNAUTHORIZED,
    FORBIDDEN_PATH,
    NOT_FOUND,
    SLUG_ALREADY_EXISTS,
    RESERVED_SLUG,
    INVALID_SLUG_FORMAT,
    RATE_LIMITED,
    NETWORK_ERROR,
    TIMEOUT_ERROR,
    INVALID_SERVER_URL,
    REQUIRED_FIELDS,
    SERVER_ERROR,
    GENERIC,
}

data class UiError(
    val key: ErrorMessageKey,
    val statusCode: Int? = null,
)

fun mapErrorCodeToUiError(
    errorTypeName: String,
    field: String? = null,
    statusCode: Int? = null,
): UiError = when (errorTypeName) {
    "InvalidCredentials" -> UiError(ErrorMessageKey.INVALID_CREDENTIALS)
    "Unauthorized", "TokenExpired" -> UiError(ErrorMessageKey.UNAUTHORIZED)
    "ForbiddenPath" -> UiError(ErrorMessageKey.FORBIDDEN_PATH)
    "NotFound" -> UiError(ErrorMessageKey.NOT_FOUND)
    "SlugAlreadyExists" -> UiError(ErrorMessageKey.SLUG_ALREADY_EXISTS)
    "ReservedSlug" -> UiError(ErrorMessageKey.RESERVED_SLUG)
    "InvalidSlugFormat" -> UiError(ErrorMessageKey.INVALID_SLUG_FORMAT)
    "RateLimited" -> UiError(ErrorMessageKey.RATE_LIMITED)
    "NetworkError" -> UiError(ErrorMessageKey.NETWORK_ERROR)
    "TimeoutError" -> UiError(ErrorMessageKey.TIMEOUT_ERROR)
    "ServerError" -> UiError(ErrorMessageKey.SERVER_ERROR, statusCode = statusCode)
    "ValidationError" -> when (field) {
        "serverUrl" -> UiError(ErrorMessageKey.INVALID_SERVER_URL)
        "username", "password" -> UiError(ErrorMessageKey.REQUIRED_FIELDS)
        else -> UiError(ErrorMessageKey.GENERIC)
    }
    else -> UiError(ErrorMessageKey.GENERIC)
}

@Composable
fun UiError.resolveMessage(): String = when (key) {
    ErrorMessageKey.INVALID_CREDENTIALS -> stringResource(Res.string.error_invalid_credentials)
    ErrorMessageKey.UNAUTHORIZED -> stringResource(Res.string.error_unauthorized)
    ErrorMessageKey.FORBIDDEN_PATH -> stringResource(Res.string.error_forbidden_path)
    ErrorMessageKey.NOT_FOUND -> stringResource(Res.string.error_not_found)
    ErrorMessageKey.SLUG_ALREADY_EXISTS -> stringResource(Res.string.error_slug_exists)
    ErrorMessageKey.RESERVED_SLUG -> stringResource(Res.string.error_reserved_slug)
    ErrorMessageKey.INVALID_SLUG_FORMAT -> stringResource(Res.string.error_invalid_slug)
    ErrorMessageKey.RATE_LIMITED -> stringResource(Res.string.error_rate_limited)
    ErrorMessageKey.NETWORK_ERROR -> stringResource(Res.string.error_network)
    ErrorMessageKey.TIMEOUT_ERROR -> stringResource(Res.string.error_timeout)
    ErrorMessageKey.INVALID_SERVER_URL -> stringResource(Res.string.error_invalid_server_url)
    ErrorMessageKey.REQUIRED_FIELDS -> stringResource(Res.string.error_required_fields)
    ErrorMessageKey.SERVER_ERROR -> stringResource(Res.string.error_server, statusCode ?: 500)
    ErrorMessageKey.GENERIC -> stringResource(Res.string.error_generic)
}
