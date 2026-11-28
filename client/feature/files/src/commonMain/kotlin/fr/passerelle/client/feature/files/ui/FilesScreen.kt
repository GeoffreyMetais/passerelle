package fr.passerelle.client.feature.files.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.passerelle.client.core.designsystem.component.SvgQrCodeView
import fr.passerelle.client.core.designsystem.error.resolveMessage
import fr.passerelle.client.core.designsystem.resources.Res
import fr.passerelle.client.core.designsystem.resources.action_close
import fr.passerelle.client.core.designsystem.resources.action_retry
import fr.passerelle.client.core.designsystem.resources.files_empty_folder
import fr.passerelle.client.core.designsystem.resources.files_empty_search
import fr.passerelle.client.core.designsystem.resources.files_select_hint
import fr.passerelle.client.core.designsystem.resources.shares_qr_title
import fr.passerelle.client.core.designsystem.resources.snackbar_link_copied
import fr.passerelle.client.core.designsystem.resources.snackbar_share_created
import fr.passerelle.client.core.designsystem.theme.Spacing
import fr.passerelle.client.core.domain.model.Breadcrumb
import fr.passerelle.client.core.domain.model.FileItem
import fr.passerelle.client.core.domain.model.ShareLink
import fr.passerelle.client.core.domain.model.SortOption
import fr.passerelle.client.feature.files.presentation.ExpiryPreset
import fr.passerelle.client.feature.files.presentation.FilesEvent
import fr.passerelle.client.feature.files.presentation.FilesUiState
import fr.passerelle.client.feature.files.presentation.FilesViewMode
import fr.passerelle.client.feature.files.presentation.FilesViewModel
import fr.passerelle.client.feature.files.presentation.QuotaPreset
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesRoute(
    viewModel: FilesViewModel,
    onOpenExternalUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val copiedMessage = stringResource(Res.string.snackbar_link_copied)
    val shareCreatedMessage = stringResource(Res.string.snackbar_share_created)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is FilesEvent.ShowMessage -> {
                    val msg = when (event.type) {
                        FilesEvent.ShowMessage.MessageType.LINK_COPIED -> copiedMessage
                        FilesEvent.ShowMessage.MessageType.SHARE_CREATED -> shareCreatedMessage
                    }
                    snackbarHostState.showSnackbar(msg)
                }
                is FilesEvent.OpenUrl -> onOpenExternalUrl(event.url)
            }
        }
    }

    FilesScreenContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBreadcrumbClick = { viewModel.loadDirectory(it.path) },
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onClearSearch = viewModel::onClearSearch,
        onSortSelected = viewModel::onSortSelected,
        onToggleViewMode = viewModel::onToggleViewMode,
        onToggleShowHidden = viewModel::onToggleShowHidden,
        onRefresh = { viewModel.refresh(fromPullToRefresh = false) },
        onPullToRefresh = { viewModel.refresh(fromPullToRefresh = true) },
        onItemClick = viewModel::onItemClicked,
        onInspectItem = viewModel::onInspectItem,
        onQuickShare = viewModel::createQuickShareAndTriggerNativeShare,
        onDismissSelection = viewModel::onDismissSelection,
        onExpiryPresetChanged = viewModel::onExpiryPresetChanged,
        onQuotaPresetChanged = viewModel::onQuotaPresetChanged,
        onCustomQuotaChanged = viewModel::onCustomQuotaChanged,
        onSharePasswordChanged = viewModel::onSharePasswordChanged,
        onCustomSlugChanged = viewModel::onCustomSlugChanged,
        onCreateShare = viewModel::createShare,
        onCopyLink = viewModel::copyLink,
        onShareLink = viewModel::shareLink,
        onOpenQr = viewModel::openQrModal,
        onDismissQr = viewModel::dismissQrModal,
        onDownloadDirect = viewModel::downloadDirect,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreenContent(
    uiState: FilesUiState,
    snackbarHostState: SnackbarHostState,
    onBreadcrumbClick: (Breadcrumb) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onClearSearch: () -> Unit,
    onSortSelected: (SortOption) -> Unit,
    onToggleViewMode: () -> Unit,
    onToggleShowHidden: () -> Unit,
    onRefresh: () -> Unit,
    onPullToRefresh: () -> Unit,
    onItemClick: (FileItem, Boolean) -> Unit,
    onInspectItem: (FileItem) -> Unit,
    onQuickShare: (FileItem) -> Unit,
    onDismissSelection: () -> Unit,
    onExpiryPresetChanged: (ExpiryPreset) -> Unit,
    onQuotaPresetChanged: (QuotaPreset) -> Unit,
    onCustomQuotaChanged: (String) -> Unit,
    onSharePasswordChanged: (String) -> Unit,
    onCustomSlugChanged: (String) -> Unit,
    onCreateShare: () -> Unit,
    onCopyLink: (String) -> Unit,
    onShareLink: (ShareLink) -> Unit,
    onOpenQr: (ShareLink) -> Unit,
    onDismissQr: () -> Unit,
    onDownloadDirect: (FileItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val searchFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var customPanelWidth by rememberSaveable { mutableStateOf<Float?>(null) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when {
                        (event.isCtrlPressed || event.isMetaPressed) && event.key == Key.K -> {
                            searchFocusRequester.requestFocus()
                            true
                        }
                        event.key == Key.Escape -> {
                            when {
                                uiState.qrSvgForModal != null -> {
                                    onDismissQr()
                                    true
                                }
                                uiState.isSearchActive -> {
                                    onClearSearch()
                                    focusManager.clearFocus()
                                    true
                                }
                                uiState.selectedItem != null -> {
                                    onDismissSelection()
                                    true
                                }
                                else -> false
                            }
                        }
                        else -> false
                    }
                } else {
                    false
                }
            },
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val isExpanded = maxWidth >= 840.dp
            val maxPanelWidth = maxWidth.value * 0.80f
            val defaultPanelWidth = (maxWidth.value * 0.34f).coerceIn(380f, 440f)

            Row(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    FilesHeaderToolbar(
                        uiState = uiState,
                        isExpanded = isExpanded,
                        searchFocusRequester = searchFocusRequester,
                        onBreadcrumbClick = onBreadcrumbClick,
                        onSearchQueryChanged = onSearchQueryChanged,
                        onClearSearch = onClearSearch,
                        onSortSelected = onSortSelected,
                        onToggleViewMode = onToggleViewMode,
                        onToggleShowHidden = onToggleShowHidden,
                        onRefresh = onRefresh,
                    )

                    if (uiState.isLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    PullToRefreshBox(
                        isRefreshing = uiState.isPullToRefreshVisible,
                        onRefresh = onPullToRefresh,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    ) {
                        when {
                            uiState.error != null && uiState.entries.isEmpty() -> {
                                Column(
                                    modifier = Modifier.align(Alignment.Center).padding(Spacing.lg),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                                ) {
                                    Text(
                                        text = uiState.error.resolveMessage(),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                    FilledTonalButton(onClick = onRefresh) {
                                        Text(stringResource(Res.string.action_retry))
                                    }
                                }
                            }
                            !uiState.isLoading && uiState.entries.isEmpty() -> {
                                val emptyText = if (uiState.isSearchActive) {
                                    stringResource(Res.string.files_empty_search, uiState.searchQuery)
                                } else {
                                    stringResource(Res.string.files_empty_folder)
                                }
                                Text(
                                    text = emptyText,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.align(Alignment.Center).padding(Spacing.lg),
                                )
                            }
                            uiState.viewMode == FilesViewMode.LIST -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(
                                        start = Spacing.md,
                                        end = Spacing.md,
                                        top = Spacing.xs,
                                        bottom = Spacing.xl,
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp),
                                ) {
                                    items(uiState.entries, key = { it.path }) { item ->
                                        FileListRow(
                                            item = item,
                                            thumbnailBytes = uiState.thumbnails[item.path],
                                            isSelected = uiState.selectedItem?.path == item.path,
                                            onClick = { onItemClick(item, true) },
                                            onShareClick = { onInspectItem(item) },
                                            onQuickShareClick = { onQuickShare(item) },
                                        )
                                    }
                                }
                            }
                            else -> {
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = 180.dp),
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(Spacing.md),
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs),
                                ) {
                                    items(uiState.entries, key = { it.path }) { item ->
                                        FileGridCard(
                                            item = item,
                                            thumbnailBytes = uiState.thumbnails[item.path],
                                            isSelected = uiState.selectedItem?.path == item.path,
                                            onClick = { onItemClick(item, true) },
                                            onShareClick = { onInspectItem(item) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (isExpanded) {
                    val effectivePanelWidth = (customPanelWidth ?: defaultPanelWidth).coerceIn(300f, maxPanelWidth)
                    DetailPanelDivider(
                        onDragDelta = { deltaDp ->
                            val current = (customPanelWidth ?: defaultPanelWidth).coerceIn(300f, maxPanelWidth)
                            customPanelWidth = (current - deltaDp).coerceIn(300f, maxPanelWidth)
                        },
                    )
                    Surface(
                        modifier = Modifier
                            .width(effectivePanelWidth.dp)
                            .fillMaxHeight(),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    ) {
                        val selected = uiState.selectedItem
                        if (selected == null) {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(Spacing.lg),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(Res.string.files_select_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            InspectorAndSharePanel(
                                item = selected,
                                thumbnailBytes = uiState.thumbnails[selected.path],
                                existingShares = uiState.selectedItemShares,
                                isLoadingShares = uiState.isLoadingSelectedShares,
                                form = uiState.createShareForm,
                                lastCreatedShare = uiState.lastCreatedShare,
                                onDismiss = onDismissSelection,
                                onDownloadDirect = { onDownloadDirect(selected) },
                                onQuickShare = { onQuickShare(selected) },
                                onExpiryPresetChanged = onExpiryPresetChanged,
                                onQuotaPresetChanged = onQuotaPresetChanged,
                                onCustomQuotaChanged = onCustomQuotaChanged,
                                onSharePasswordChanged = onSharePasswordChanged,
                                onCustomSlugChanged = onCustomSlugChanged,
                                onCreateShare = onCreateShare,
                                onCopyLink = onCopyLink,
                                onShareLink = onShareLink,
                                onOpenQr = onOpenQr,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(Spacing.md),
                            )
                        }
                    }
                }
            }

            if (!isExpanded && uiState.selectedItem != null) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(
                    onDismissRequest = onDismissSelection,
                    sheetState = sheetState,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                ) {
                    val selected = uiState.selectedItem
                    InspectorAndSharePanel(
                        item = selected,
                        thumbnailBytes = uiState.thumbnails[selected.path],
                        existingShares = uiState.selectedItemShares,
                        isLoadingShares = uiState.isLoadingSelectedShares,
                        form = uiState.createShareForm,
                        lastCreatedShare = uiState.lastCreatedShare,
                        onDismiss = onDismissSelection,
                        onDownloadDirect = { onDownloadDirect(selected) },
                        onQuickShare = { onQuickShare(selected) },
                        onExpiryPresetChanged = onExpiryPresetChanged,
                        onQuotaPresetChanged = onQuotaPresetChanged,
                        onCustomQuotaChanged = onCustomQuotaChanged,
                        onSharePasswordChanged = onSharePasswordChanged,
                        onCustomSlugChanged = onCustomSlugChanged,
                        onCreateShare = onCreateShare,
                        onCopyLink = onCopyLink,
                        onShareLink = onShareLink,
                        onOpenQr = onOpenQr,
                        modifier = Modifier
                            .fillMaxWidth()
                            .imePadding()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = Spacing.md)
                            .padding(bottom = Spacing.lg),
                    )
                }
            }
        }
    }

    uiState.qrSvgForModal?.let { svg ->
        AlertDialog(
            onDismissRequest = onDismissQr,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            title = {
                Text(
                    text = uiState.qrShareTitle ?: stringResource(Res.string.shares_qr_title),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    SvgQrCodeView(svgContent = svg)
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissQr) {
                    Text(stringResource(Res.string.action_close))
                }
            },
        )
    }
}
