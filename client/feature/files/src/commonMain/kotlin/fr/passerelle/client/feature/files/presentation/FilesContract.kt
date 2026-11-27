package fr.passerelle.client.feature.files.presentation

import fr.passerelle.client.core.designsystem.error.ErrorMessageKey
import fr.passerelle.client.core.designsystem.error.UiError
import fr.passerelle.client.core.domain.error.AppError
import fr.passerelle.client.core.domain.model.Breadcrumb
import fr.passerelle.client.core.domain.model.FileItem
import fr.passerelle.client.core.domain.model.ShareLink
import fr.passerelle.client.core.domain.model.SortDirection
import fr.passerelle.client.core.domain.model.SortOption
import fr.passerelle.client.core.platform.ShareOutcome

enum class FilesViewMode {
    LIST,
    GRID,
}

enum class ExpiryPreset(val seconds: Long?) {
    ONE_HOUR(3600L),
    ONE_DAY(86400L),
    SEVEN_DAYS(604800L),
    THIRTY_DAYS(2592000L),
    NEVER(null),
}

enum class QuotaPreset(val limit: Int?) {
    ONE(1),
    FIVE(5),
    TEN(10),
    UNLIMITED(null),
}

data class CreateShareFormState(
    val expiryPreset: ExpiryPreset = ExpiryPreset.SEVEN_DAYS,
    val quotaPreset: QuotaPreset = QuotaPreset.UNLIMITED,
    val customQuotaText: String = "",
    val password: String = "",
    val customSlug: String = "",
    val isSubmitting: Boolean = false,
    val error: UiError? = null,
)

data class FilesUiState(
    val currentPath: String = "",
    val breadcrumbs: List<Breadcrumb> = listOf(Breadcrumb("Racine", "")),
    val entries: List<FileItem> = emptyList(),
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val sortOption: SortOption = SortOption.NAME,
    val sortDirection: SortDirection = SortDirection.ASC,
    val showHidden: Boolean = false,
    val viewMode: FilesViewMode = FilesViewMode.LIST,
    val isLoading: Boolean = false,
    val error: UiError? = null,
    val selectedItem: FileItem? = null,
    val selectedItemShares: List<ShareLink> = emptyList(),
    val isLoadingSelectedShares: Boolean = false,
    val createShareForm: CreateShareFormState = CreateShareFormState(),
    val lastCreatedShare: ShareLink? = null,
    val thumbnails: Map<String, ByteArray> = emptyMap(),
    val qrSvgForModal: String? = null,
    val qrShareTitle: String? = null,
)

sealed interface FilesEvent {
    data class ShowMessage(val type: MessageType) : FilesEvent {
        enum class MessageType {
            LINK_COPIED,
            SHARE_CREATED,
        }
    }
    data class OpenUrl(val url: String) : FilesEvent
}

internal fun AppError.toFilesUiError(): UiError = when (this) {
    AppError.Unauthorized, AppError.TokenExpired -> UiError(ErrorMessageKey.UNAUTHORIZED)
    AppError.ForbiddenPath -> UiError(ErrorMessageKey.FORBIDDEN_PATH)
    AppError.NotFound -> UiError(ErrorMessageKey.NOT_FOUND)
    AppError.SlugAlreadyExists -> UiError(ErrorMessageKey.SLUG_ALREADY_EXISTS)
    AppError.ReservedSlug -> UiError(ErrorMessageKey.RESERVED_SLUG)
    AppError.InvalidSlugFormat -> UiError(ErrorMessageKey.INVALID_SLUG_FORMAT)
    AppError.RateLimited -> UiError(ErrorMessageKey.RATE_LIMITED)
    AppError.NetworkError -> UiError(ErrorMessageKey.NETWORK_ERROR)
    AppError.TimeoutError -> UiError(ErrorMessageKey.TIMEOUT_ERROR)
    is AppError.ServerError -> UiError(ErrorMessageKey.SERVER_ERROR, statusCode)
    is AppError.ValidationError -> UiError(ErrorMessageKey.GENERIC)
    else -> UiError(ErrorMessageKey.GENERIC)
}

internal fun ShareOutcome.toMessageTypeOrNull(): FilesEvent.ShowMessage.MessageType? = when (this) {
    ShareOutcome.COPIED_TO_CLIPBOARD -> FilesEvent.ShowMessage.MessageType.LINK_COPIED
    ShareOutcome.OPENED_NATIVE_SHEET -> null
}
