package fr.passerelle.client.feature.shares.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.passerelle.client.core.domain.error.onError
import fr.passerelle.client.core.domain.error.onSuccess
import fr.passerelle.client.core.domain.model.ShareLink
import fr.passerelle.client.core.domain.model.UpdateShareParams
import fr.passerelle.client.core.domain.usecase.DeleteShareUseCase
import fr.passerelle.client.core.domain.usecase.GetShareQrSvgUseCase
import fr.passerelle.client.core.domain.usecase.GetSharesUseCase
import fr.passerelle.client.core.domain.usecase.UpdateShareUseCase
import fr.passerelle.client.core.platform.PlatformActions
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SharesViewModel(
    private val getSharesUseCase: GetSharesUseCase,
    private val updateShareUseCase: UpdateShareUseCase,
    private val deleteShareUseCase: DeleteShareUseCase,
    private val getShareQrSvgUseCase: GetShareQrSvgUseCase,
    private val platformActions: PlatformActions,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SharesUiState())
    val uiState: StateFlow<SharesUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SharesEvent>(replay = 0, extraBufferCapacity = 16)
    val events: SharedFlow<SharesEvent> = _events.asSharedFlow()

    private var loadJob: Job? = null

    init {
        loadShares()
    }

    fun loadShares() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            getSharesUseCase(path = null, activeOnly = false)
                .onSuccess { shares ->
                    _uiState.update { state ->
                        state.copy(
                            allShares = shares,
                            filteredShares = applyFilter(shares, state.filter, state.searchQuery),
                            isLoading = false,
                            error = null,
                        )
                    }
                }
                .onError { err ->
                    _uiState.update { it.copy(isLoading = false, error = err.toSharesUiError()) }
                }
        }
    }

    fun onFilterChanged(filter: ShareFilter) {
        _uiState.update { state ->
            state.copy(
                filter = filter,
                filteredShares = applyFilter(state.allShares, filter, state.searchQuery),
            )
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                filteredShares = applyFilter(state.allShares, state.filter, query),
            )
        }
    }

    fun copyLink(share: ShareLink) {
        platformActions.copyToClipboard(share.publicUrl)
        viewModelScope.launch {
            _events.emit(SharesEvent.ShowMessage(SharesEvent.ShowMessage.MessageType.LINK_COPIED))
        }
    }

    fun shareLink(share: ShareLink) {
        viewModelScope.launch {
            platformActions.shareLink(share.publicUrl, share.targetName)
                .toSharesMessageOrNull()
                ?.let { _events.emit(SharesEvent.ShowMessage(it)) }
        }
    }

    fun openQrModal(share: ShareLink) {
        viewModelScope.launch {
            getShareQrSvgUseCase(share.id).onSuccess { svg ->
                _uiState.update {
                    it.copy(qrSvgForModal = svg, qrShareTitle = share.targetName)
                }
            }
        }
    }

    fun dismissQrModal() {
        _uiState.update { it.copy(qrSvgForModal = null, qrShareTitle = null) }
    }

    fun openEditDialog(share: ShareLink) {
        _uiState.update {
            it.copy(
                editingShare = EditShareDialogState(
                    share = share,
                    isRevoked = share.isRevoked,
                    maxDownloadsText = share.maxDownloads?.toString().orEmpty(),
                    clearMaxDownloads = false,
                    clearExpiration = false,
                )
            )
        }
    }

    fun dismissEditDialog() {
        _uiState.update { it.copy(editingShare = null) }
    }

    fun onEditRevokedChanged(revoked: Boolean) {
        _uiState.update { it.copy(editingShare = it.editingShare?.copy(isRevoked = revoked, error = null)) }
    }

    fun onEditMaxDownloadsChanged(value: String) {
        val digits = value.filter { it.isDigit() }
        _uiState.update { it.copy(editingShare = it.editingShare?.copy(maxDownloadsText = digits, error = null)) }
    }

    fun onEditClearMaxDownloadsChanged(clear: Boolean) {
        _uiState.update { it.copy(editingShare = it.editingShare?.copy(clearMaxDownloads = clear, error = null)) }
    }

    fun onEditClearExpirationChanged(clear: Boolean) {
        _uiState.update { it.copy(editingShare = it.editingShare?.copy(clearExpiration = clear, error = null)) }
    }

    fun onEditNewPasswordChanged(password: String) {
        _uiState.update { it.copy(editingShare = it.editingShare?.copy(newPassword = password, error = null)) }
    }

    fun onEditClearPasswordChanged(clear: Boolean) {
        _uiState.update { it.copy(editingShare = it.editingShare?.copy(clearPassword = clear, error = null)) }
    }

    fun submitEdit() {
        val edit = _uiState.value.editingShare ?: return
        if (edit.isSubmitting) return

        val parsedQuota = if (!edit.clearMaxDownloads && edit.maxDownloadsText.isNotEmpty()) {
            edit.maxDownloadsText.toIntOrNull()
        } else {
            null
        }

        viewModelScope.launch {
            _uiState.update { it.copy(editingShare = edit.copy(isSubmitting = true, error = null)) }
            updateShareUseCase(
                UpdateShareParams(
                    id = edit.share.id,
                    isRevoked = edit.isRevoked,
                    clearExpiration = edit.clearExpiration,
                    maxDownloads = parsedQuota,
                    clearMaxDownloads = edit.clearMaxDownloads,
                    newPassword = if (!edit.clearPassword) edit.newPassword.takeIf { it.isNotEmpty() } else null,
                    clearPassword = edit.clearPassword,
                )
            ).onSuccess { updated ->
                _uiState.update { state ->
                    val newList = state.allShares.map { if (it.id == updated.id) updated else it }
                    state.copy(
                        allShares = newList,
                        filteredShares = applyFilter(newList, state.filter, state.searchQuery),
                        editingShare = null,
                    )
                }
                _events.emit(SharesEvent.ShowMessage(SharesEvent.ShowMessage.MessageType.SHARE_UPDATED))
            }.onError { err ->
                _uiState.update { it.copy(editingShare = edit.copy(isSubmitting = false, error = err.toSharesUiError())) }
            }
        }
    }

    fun requestDelete(share: ShareLink) {
        _uiState.update { it.copy(deletingShare = share) }
    }

    fun dismissDelete() {
        _uiState.update { it.copy(deletingShare = null) }
    }

    fun confirmDelete() {
        val target = _uiState.value.deletingShare ?: return
        viewModelScope.launch {
            deleteShareUseCase(target.id)
                .onSuccess {
                    _uiState.update { state ->
                        val newList = state.allShares.filterNot { it.id == target.id }
                        state.copy(
                            allShares = newList,
                            filteredShares = applyFilter(newList, state.filter, state.searchQuery),
                            deletingShare = null,
                        )
                    }
                    _events.emit(SharesEvent.ShowMessage(SharesEvent.ShowMessage.MessageType.SHARE_DELETED))
                }
                .onError { err ->
                    _uiState.update { it.copy(deletingShare = null, error = err.toSharesUiError()) }
                }
        }
    }

    private fun applyFilter(
        shares: List<ShareLink>,
        filter: ShareFilter,
        query: String,
    ): List<ShareLink> {
        val q = query.trim()
        return shares.filter { share ->
            val matchesFilter = when (filter) {
                ShareFilter.ALL -> true
                ShareFilter.ACTIVE -> share.isActive
                ShareFilter.INACTIVE -> !share.isActive
            }
            val matchesQuery = q.isEmpty() ||
                share.slug.contains(q, ignoreCase = true) ||
                share.targetName.contains(q, ignoreCase = true) ||
                share.relativePath.contains(q, ignoreCase = true)
            matchesFilter && matchesQuery
        }
    }
}
