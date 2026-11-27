package fr.passerelle.client.core.domain.error

sealed class AppError {
    data object InvalidCredentials : AppError()
    data object Unauthorized : AppError()
    data object TokenExpired : AppError()
    data object ForbiddenPath : AppError()
    data object NotFound : AppError()
    data object SlugAlreadyExists : AppError()
    data object ReservedSlug : AppError()
    data object InvalidSlugFormat : AppError()
    data object PayloadTooLarge : AppError()
    data object ShareExpired : AppError()
    data object ShareRevoked : AppError()
    data object QuotaExceeded : AppError()
    data object InvalidPassword : AppError()
    data object RateLimited : AppError()
    data object NetworkError : AppError()
    data object TimeoutError : AppError()
    data class ServerError(val statusCode: Int, val serverMessage: String? = null) : AppError()
    data class ValidationError(val field: String, val reason: String) : AppError()
    data class UnexpectedError(val details: String? = null) : AppError()
}

sealed class Resource<out T> {
    data class Success<out T>(val data: T) : Resource<T>()
    data class Error(val error: AppError) : Resource<Nothing>()
}

inline fun <T> Resource<T>.onSuccess(action: (T) -> Unit): Resource<T> {
    if (this is Resource.Success) action(data)
    return this
}

inline fun <T> Resource<T>.onError(action: (AppError) -> Unit): Resource<T> {
    if (this is Resource.Error) action(error)
    return this
}

inline fun <T, R> Resource<T>.map(transform: (T) -> R): Resource<R> = when (this) {
    is Resource.Success -> Resource.Success(transform(data))
    is Resource.Error -> this
}
