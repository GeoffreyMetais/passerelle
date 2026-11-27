package fr.passerelle.client.feature.files.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.passerelle.client.core.designsystem.error.ErrorMessageKey
import fr.passerelle.client.core.designsystem.error.UiError
import fr.passerelle.client.core.domain.error.Resource
import fr.passerelle.client.core.domain.error.onError
import fr.passerelle.client.core.domain.error.onSuccess
import fr.passerelle.client.core.domain.model.CreateShareParams
import fr.passerelle.client.core.domain.model.FileItem
import fr.passerelle.client.core.domain.model.ShareLink
import fr.passerelle.client.core.domain.model.SortDirection
import fr.passerelle.client.core.domain.model.SortOption
import fr.passerelle.client.core.domain.repository.FileRepository
import fr.passerelle.client.core.domain.usecase.BrowseDirectoryUseCase
import fr.passerelle.client.core.domain.usecase.CreateShareUseCase
import fr.passerelle.client.core.domain.usecase.GetShareQrSvgUseCase
import fr.passerelle.client.core.domain.usecase.GetSharesUseCase
import fr.passerelle.client.core.domain.usecase.SearchFilesUseCase
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

class FilesViewModel(
    private val browseDirectoryUseCase: BrowseDirectoryUseCase,
    private val searchFilesUseCase: SearchFilesUseCase,
    private val createShareUseCase: CreateShareUseCase,
    private val getSharesUseCase: GetSharesUseCase,
    private val getShareQrSvgUseCase: GetShareQrSvgUseCase,
    private val fileRepository: FileRepository,
    private val platformActions: PlatformActions,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FilesUiState())
    val uiState: StateFlow<FilesUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<FilesEvent>(replay = 0, extraBufferCapacity = 16)
    val events: SharedFlow<FilesEvent> = _events.asSharedFlow()

    private val thumbnailCache = mutableMapOf<String, ByteArray>()
    private var browseJob: Job? = null
    private var searchJob: Job? = null
    private var selectedSharesJob: Job? = null

    init {
        loadDirectory("")
    }

    fun loadDirectory(path: String = _uiState.value.currentPath) {
        browseJob?.cancel()
        searchJob?.cancel()
        browseJob = viewModelScope.launch {
            val current = _uiState.value
            _uiState.update {
                it.copy(
                    currentPath = path,
                    isSearchActive = false,
                    searchQuery = "",
                    isLoading = true,
                    error = null,
                )
            }
            browseDirectoryUseCase(
                path = path,
                sortBy = current.sortOption,
                sortOrder = current.sortDirection,
                showHidden = current.showHidden,
            ).onSuccess { content ->
                _uiState.update {
                    it.copy(
                        currentPath = content.currentPath,
                        breadcrumbs = content.breadcrumbs,
                        entries = content.entries,
                        isLoading = false,
                        error = null,
                    )
                }
                prefetchThumbnails(content.entries)
            }.onError { err ->
                _uiState.update {
                    it.copy(isLoading = false, error = err.toFilesUiError())
                }
            }
        }
    }

    fun refresh() {
        val state = _uiState.value
        if (state.isSearchActive && state.searchQuery.isNotBlank()) {
            performSearch(state.searchQuery)
        } else {
            loadDirectory(state.currentPath)
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        if (query.isBlank()) {
            searchJob?.cancel()
            if (_uiState.value.isSearchActive) {
                loadDirectory(_uiState.value.currentPath)
            }
            return
        }
        performSearch(query)
    }

    private fun performSearch(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val current = _uiState.value
            _uiState.update {
                it.copy(isSearchActive = true, isLoading = true, error = null)
            }
            searchFilesUseCase(
                query = query,
                path = current.currentPath,
                showHidden = current.showHidden,
            ).onSuccess { results ->
                _uiState.update {
                    it.copy(entries = results, isLoading = false, error = null)
                }
                prefetchThumbnails(results)
            }.onError { err ->
                _uiState.update {
                    it.copy(isLoading = false, error = err.toFilesUiError())
                }
            }
        }
    }

    fun onClearSearch() {
        searchJob?.cancel()
        _uiState.update { it.copy(searchQuery = "", isSearchActive = false) }
        loadDirectory(_uiState.value.currentPath)
    }

    fun onSortSelected(option: SortOption) {
        val current = _uiState.value
        val nextDirection = if (current.sortOption == option) {
            if (current.sortDirection == SortDirection.ASC) SortDirection.DESC else SortDirection.ASC
        } else {
            SortDirection.ASC
        }
        _uiState.update { it.copy(sortOption = option, sortDirection = nextDirection) }
        refresh()
    }

    fun onToggleViewMode() {
        _uiState.update {
            val next = if (it.viewMode == FilesViewMode.LIST) FilesViewMode.GRID else FilesViewMode.LIST
            it.copy(viewMode = next)
        }
    }

    fun onToggleShowHidden() {
        _uiState.update { it.copy(showHidden = !it.showHidden) }
        refresh()
    }

    fun onItemClicked(item: FileItem, openDirectoryOnClick: Boolean = true) {
        if (item.isDirectory && openDirectoryOnClick) {
            loadDirectory(item.path)
        } else {
            onInspectItem(item)
        }
    }

    fun onInspectItem(item: FileItem) {
        _uiState.update {
            it.copy(
                selectedItem = item,
                selectedItemShares = emptyList(),
                createShareForm = CreateShareFormState(),
                lastCreatedShare = null,
            )
        }
        loadSharesForItem(item.path)
    }

    fun onDismissSelection() {
        selectedSharesJob?.cancel()
        _uiState.update {
            it.copy(
                selectedItem = null,
                selectedItemShares = emptyList(),
                lastCreatedShare = null,
            )
        }
    }

    private fun loadSharesForItem(path: String) {
        selectedSharesJob?.cancel()
        selectedSharesJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSelectedShares = true) }
            getSharesUseCase(path = path, activeOnly = true)
                .onSuccess { shares ->
                    _uiState.update { it.copy(selectedItemShares = shares, isLoadingSelectedShares = false) }
                }
                .onError {
                    _uiState.update { it.copy(isLoadingSelectedShares = false) }
                }
        }
    }

    fun onExpiryPresetChanged(preset: ExpiryPreset) {
        _uiState.update {
            it.copy(createShareForm = it.createShareForm.copy(expiryPreset = preset, error = null))
        }
    }

    fun onQuotaPresetChanged(preset: QuotaPreset) {
        _uiState.update {
            it.copy(
                createShareForm = it.createShareForm.copy(
                    quotaPreset = preset,
                    customQuotaText = "",
                    error = null,
                )
            )
        }
    }

    fun onCustomQuotaChanged(value: String) {
        val digitsOnly = value.filter { it.isDigit() }
        _uiState.update {
            it.copy(createShareForm = it.createShareForm.copy(customQuotaText = digitsOnly, error = null))
        }
    }

    fun onSharePasswordChanged(value: String) {
        _uiState.update {
            it.copy(createShareForm = it.createShareForm.copy(password = value, error = null))
        }
    }

    fun onCustomSlugChanged(value: String) {
        _uiState.update {
            it.copy(createShareForm = it.createShareForm.copy(customSlug = value, error = null))
        }
    }

    fun createShare() {
        val selected = _uiState.value.selectedItem ?: return
        val form = _uiState.value.createShareForm
        if (form.isSubmitting) return

        val resolvedMaxDownloads = if (form.customQuotaText.isNotEmpty()) {
            val parsed = form.customQuotaText.toIntOrNull()
            if (parsed == null || parsed <= 0) {
                _uiState.update {
                    it.copy(createShareForm = form.copy(error = UiError(ErrorMessageKey.GENERIC)))
                }
                return
            }
            parsed
        } else {
            form.quotaPreset.limit
        }

        viewModelScope.launch {
            _uiState.update { it.copy(createShareForm = form.copy(isSubmitting = true, error = null)) }
            createShareUseCase(
                CreateShareParams(
                    relativePath = selected.path,
                    customSlug = form.customSlug.takeIf { it.isNotBlank() },
                    password = form.password.takeIf { it.isNotEmpty() },
                    expiresInSeconds = form.expiryPreset.seconds,
                    maxDownloads = resolvedMaxDownloads,
                )
            ).onSuccess { created ->
                _uiState.update { state ->
                    val updatedEntries = state.entries.map { entry ->
                        if (entry.path == selected.path) {
                            entry.copy(activeShareCount = entry.activeShareCount + 1)
                        } else {
                            entry
                        }
                    }
                    state.copy(
                        entries = updatedEntries,
                        lastCreatedShare = created,
                        selectedItemShares = listOf(created) + state.selectedItemShares,
                        createShareForm = CreateShareFormState(),
                    )
                }
                _events.emit(FilesEvent.ShowMessage(FilesEvent.ShowMessage.MessageType.SHARE_CREATED))
            }.onError { err ->
                _uiState.update {
                    it.copy(createShareForm = form.copy(isSubmitting = false, error = err.toFilesUiError()))
                }
            }
        }
    }

    fun createQuickShareAndTriggerNativeShare(item: FileItem) {
        viewModelScope.launch {
            createShareUseCase(
                CreateShareParams(
                    relativePath = item.path,
                    expiresInSeconds = ExpiryPreset.SEVEN_DAYS.seconds,
                )
            ).onSuccess { created ->
                _uiState.update { state ->
                    val updatedEntries = state.entries.map { entry ->
                        if (entry.path == item.path) {
                            entry.copy(activeShareCount = entry.activeShareCount + 1)
                        } else {
                            entry
                        }
                    }
                    state.copy(entries = updatedEntries, lastCreatedShare = created)
                }
                val outcome = platformActions.shareLink(created.publicUrl, created.targetName)
                outcome.toMessageTypeOrNull()?.let { msgType ->
                    _events.emit(FilesEvent.ShowMessage(msgType))
                }
            }.onError { err ->
                _uiState.update { it.copy(error = err.toFilesUiError()) }
            }
        }
    }

    fun copyLink(url: String) {
        platformActions.copyToClipboard(url)
        viewModelScope.launch {
            _events.emit(FilesEvent.ShowMessage(FilesEvent.ShowMessage.MessageType.LINK_COPIED))
        }
    }

    fun shareLink(share: ShareLink) {
        viewModelScope.launch {
            val outcome = platformActions.shareLink(share.publicUrl, share.targetName)
            outcome.toMessageTypeOrNull()?.let { msgType ->
                _events.emit(FilesEvent.ShowMessage(msgType))
            }
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

    fun downloadDirect(item: FileItem) {
        viewModelScope.launch {
            val url = fileRepository.getDirectDownloadUrl(item.path, inline = false)
            _events.emit(FilesEvent.OpenUrl(url))
        }
    }

    private fun prefetchThumbnails(entries: List<FileItem>) {
        entries.filter { it.hasThumbnail }.forEach { item ->
            val cached = thumbnailCache[item.path]
            if (cached != null) {
                _uiState.update { it.copy(thumbnails = it.thumbnails + (item.path to cached)) }
                return@forEach
            }
            viewModelScope.launch {
                val res = fileRepository.getThumbnail(item.path)
                if (res is Resource.Success) {
                    thumbnailCache[item.path] = res.data
                    _uiState.update { it.copy(thumbnails = it.thumbnails + (item.path to res.data)) }
                }
            }
        }
    }
}
