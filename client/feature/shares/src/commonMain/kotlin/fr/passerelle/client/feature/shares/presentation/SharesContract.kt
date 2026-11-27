package fr.passerelle.client.feature.shares.presentation

import fr.passerelle.client.core.designsystem.error.ErrorMessageKey
import fr.passerelle.client.core.designsystem.error.UiError
import fr.passerelle.client.core.domain.error.AppError
import fr.passerelle.client.core.domain.model.ShareLink
import fr.passerelle.client.core.platform.ShareOutcome

enum class ShareFilter {
    ALL,
    ACTIVE,
    INACTIVE,
}

data class EditShareDialogState(
    val share: ShareLink,
    val isRevoked: Boolean,
    val maxDownloadsText: String,
    val clearMaxDownloads: Boolean,
    val clearExpiration: Boolean,
    val newPassword: String = "",
    val clearPassword: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: UiError? = null,
)

data class SharesUiState(
    val allShares: List<ShareLink> = emptyList(),
    val filteredShares: List<ShareLink> = emptyList(),
    val filter: ShareFilter = ShareFilter.ALL,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val error: UiError? = null,
    val editingShare: EditShareDialogState? = null,
    val deletingShare: ShareLink? = null,
    val qrSvgForModal: String? = null,
    val qrShareTitle: String? = null,
)

sealed interface SharesEvent {
    data class ShowMessage(val type: MessageType) : SharesEvent {
        enum class MessageType {
            LINK_COPIED,
            SHARE_UPDATED,
            SHARE_DELETED,
        }
    }
}

internal fun AppError.toSharesUiError(): UiError = when (this) {
    AppError.Unauthorized, AppError.TokenExpired -> UiError(ErrorMessageKey.UNAUTHORIZED)
    AppError.NotFound -> UiError(ErrorMessageKey.NOT_FOUND)
    AppError.RateLimited -> UiError(ErrorMessageKey.RATE_LIMITED)
    AppError.NetworkError -> UiError(ErrorMessageKey.NETWORK_ERROR)
    AppError.TimeoutError -> UiError(ErrorMessageKey.TIMEOUT_ERROR)
    is AppError.ServerError -> UiError(ErrorMessageKey.SERVER_ERROR, statusCode)
    else -> UiError(ErrorMessageKey.GENERIC)
}

internal fun ShareOutcome.toSharesMessageOrNull(): SharesEvent.ShowMessage.MessageType? = when (this) {
    ShareOutcome.COPIED_TO_CLIPBOARD -> SharesEvent.ShowMessage.MessageType.LINK_COPIED
    ShareOutcome.OPENED_NATIVE_SHEET -> null
}
