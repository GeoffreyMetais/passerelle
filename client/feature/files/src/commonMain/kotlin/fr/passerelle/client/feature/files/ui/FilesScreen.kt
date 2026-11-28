package fr.passerelle.client.feature.files.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.passerelle.client.core.designsystem.component.PasserelleFilterPill
import fr.passerelle.client.core.designsystem.component.PasserelleIconBadge
import fr.passerelle.client.core.designsystem.component.PasserelleSearchBar
import fr.passerelle.client.core.designsystem.component.PasserelleStatusPill
import fr.passerelle.client.core.designsystem.component.PasserelleStatusTone
import fr.passerelle.client.core.designsystem.component.PasserelleSurfaceCard
import fr.passerelle.client.core.designsystem.component.PasserelleToolbarButton
import fr.passerelle.client.core.designsystem.component.SvgQrCodeView
import fr.passerelle.client.core.designsystem.component.TooltipIconButton
import fr.passerelle.client.core.designsystem.error.resolveMessage
import fr.passerelle.client.core.designsystem.format.formatFileSize
import fr.passerelle.client.core.designsystem.resources.Res
import fr.passerelle.client.core.designsystem.resources.action_close
import fr.passerelle.client.core.designsystem.resources.action_copy_link
import fr.passerelle.client.core.designsystem.resources.action_download
import fr.passerelle.client.core.designsystem.resources.action_qr_code
import fr.passerelle.client.core.designsystem.resources.action_refresh
import fr.passerelle.client.core.designsystem.resources.action_retry
import fr.passerelle.client.core.designsystem.resources.action_share
import fr.passerelle.client.core.designsystem.resources.files_active_shares_badge
import fr.passerelle.client.core.designsystem.resources.files_clear_search
import fr.passerelle.client.core.designsystem.resources.files_create_share_button
import fr.passerelle.client.core.designsystem.resources.files_create_share_title
import fr.passerelle.client.core.designsystem.resources.files_custom_slug_optional
import fr.passerelle.client.core.designsystem.resources.files_empty_folder
import fr.passerelle.client.core.designsystem.resources.files_empty_search
import fr.passerelle.client.core.designsystem.resources.files_existing_shares
import fr.passerelle.client.core.designsystem.resources.files_expiry_1h
import fr.passerelle.client.core.designsystem.resources.files_expiry_24h
import fr.passerelle.client.core.designsystem.resources.files_expiry_30d
import fr.passerelle.client.core.designsystem.resources.files_expiry_7d
import fr.passerelle.client.core.designsystem.resources.files_expiry_label
import fr.passerelle.client.core.designsystem.resources.files_expiry_never
import fr.passerelle.client.core.designsystem.resources.files_folder_label
import fr.passerelle.client.core.designsystem.resources.files_item_count
import fr.passerelle.client.core.designsystem.resources.files_item_count_single
import fr.passerelle.client.core.designsystem.resources.files_password_optional
import fr.passerelle.client.core.designsystem.resources.files_quota_1
import fr.passerelle.client.core.designsystem.resources.files_quota_10
import fr.passerelle.client.core.designsystem.resources.files_quota_5
import fr.passerelle.client.core.designsystem.resources.files_quota_custom
import fr.passerelle.client.core.designsystem.resources.files_quota_label
import fr.passerelle.client.core.designsystem.resources.files_quota_unlimited
import fr.passerelle.client.core.designsystem.resources.files_search_placeholder_short
import fr.passerelle.client.core.designsystem.resources.files_search_shortcut
import fr.passerelle.client.core.designsystem.resources.files_select_hint
import fr.passerelle.client.core.designsystem.resources.files_share_action_quick
import fr.passerelle.client.core.designsystem.resources.files_share_created_title
import fr.passerelle.client.core.designsystem.resources.files_show_hidden
import fr.passerelle.client.core.designsystem.resources.files_sort_date
import fr.passerelle.client.core.designsystem.resources.files_sort_name
import fr.passerelle.client.core.designsystem.resources.files_sort_size
import fr.passerelle.client.core.designsystem.resources.files_view_grid
import fr.passerelle.client.core.designsystem.resources.files_view_list
import fr.passerelle.client.core.designsystem.resources.shares_qr_title
import fr.passerelle.client.core.designsystem.resources.snackbar_link_copied
import fr.passerelle.client.core.designsystem.resources.snackbar_share_created
import fr.passerelle.client.core.designsystem.theme.Spacing
import fr.passerelle.client.core.domain.model.Breadcrumb
import fr.passerelle.client.core.domain.model.FileItem
import fr.passerelle.client.core.domain.model.ShareLink
import fr.passerelle.client.core.domain.model.SortDirection
import fr.passerelle.client.core.domain.model.SortOption
import fr.passerelle.client.feature.files.presentation.CreateShareFormState
import fr.passerelle.client.feature.files.presentation.ExpiryPreset
import fr.passerelle.client.feature.files.presentation.FilesEvent
import fr.passerelle.client.feature.files.presentation.FilesUiState
import fr.passerelle.client.feature.files.presentation.FilesViewMode
import fr.passerelle.client.feature.files.presentation.FilesViewModel
import fr.passerelle.client.feature.files.presentation.QuotaPreset
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
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

@Composable
private fun DetailPanelDivider(
    onDragDelta: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    val currentOnDragDelta by rememberUpdatedState(onDragDelta)
    Box(
        modifier = modifier
            .width(12.dp)
            .fillMaxHeight()
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { change, dragAmount ->
                    change.consume()
                    currentOnDragDelta(dragAmount.x.toDp().value)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(if (dragging) 3.dp else 1.dp)
                .fillMaxHeight()
                .background(
                    color = if (dragging) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                    },
                ),
        )
    }
}

@Composable
private fun FilesHeaderToolbar(
    uiState: FilesUiState,
    isExpanded: Boolean,
    searchFocusRequester: FocusRequester,
    onBreadcrumbClick: (Breadcrumb) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onClearSearch: () -> Unit,
    onSortSelected: (SortOption) -> Unit,
    onToggleViewMode: () -> Unit,
    onToggleShowHidden: () -> Unit,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm + Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            PasserelleSearchBar(
                query = uiState.searchQuery,
                onQueryChange = onSearchQueryChanged,
                placeholder = stringResource(Res.string.files_search_placeholder_short),
                clearContentDescription = stringResource(Res.string.files_clear_search),
                shortcutHint = if (isExpanded) stringResource(Res.string.files_search_shortcut) else null,
                onClear = onClearSearch,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(searchFocusRequester),
            )

            PasserelleToolbarButton(
                tooltipText = if (uiState.viewMode == FilesViewMode.LIST) {
                    stringResource(Res.string.files_view_grid)
                } else {
                    stringResource(Res.string.files_view_list)
                },
                icon = if (uiState.viewMode == FilesViewMode.LIST) {
                    Icons.Default.GridView
                } else {
                    Icons.AutoMirrored.Filled.ViewList
                },
                onClick = onToggleViewMode,
            )

            PasserelleToolbarButton(
                tooltipText = stringResource(Res.string.action_refresh),
                icon = Icons.Default.Refresh,
                onClick = onRefresh,
            )
        }

        if (!uiState.isSearchActive) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    uiState.breadcrumbs.forEachIndexed { index, crumb ->
                        if (index > 0) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        TextButton(
                            onClick = { onBreadcrumbClick(crumb) },
                            contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.xs),
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                        ) {
                            Text(
                                text = crumb.name,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (index == uiState.breadcrumbs.lastIndex) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
                val itemCountLabel = if (uiState.entries.size == 1) {
                    stringResource(Res.string.files_item_count_single)
                } else {
                    stringResource(Res.string.files_item_count, uiState.entries.size)
                }
                Text(
                    text = itemCountLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SortChip(
                label = stringResource(Res.string.files_sort_name),
                selected = uiState.sortOption == SortOption.NAME,
                direction = uiState.sortDirection,
                onClick = { onSortSelected(SortOption.NAME) },
            )
            SortChip(
                label = stringResource(Res.string.files_sort_size),
                selected = uiState.sortOption == SortOption.SIZE,
                direction = uiState.sortDirection,
                onClick = { onSortSelected(SortOption.SIZE) },
            )
            SortChip(
                label = stringResource(Res.string.files_sort_date),
                selected = uiState.sortOption == SortOption.MODIFIED_AT,
                direction = uiState.sortDirection,
                onClick = { onSortSelected(SortOption.MODIFIED_AT) },
            )
            PasserelleFilterPill(
                label = stringResource(Res.string.files_show_hidden),
                selected = uiState.showHidden,
                onClick = onToggleShowHidden,
                leadingIcon = if (uiState.showHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
            )
        }
    }
}

@Composable
private fun SortChip(
    label: String,
    selected: Boolean,
    direction: SortDirection,
    onClick: () -> Unit,
) {
    PasserelleFilterPill(
        label = label,
        selected = selected,
        onClick = onClick,
        trailingIcon = if (selected) {
            if (direction == SortDirection.ASC) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward
        } else {
            null
        },
    )
}

@Composable
private fun FileListRow(
    item: FileItem,
    thumbnailBytes: ByteArray?,
    isSelected: Boolean,
    onClick: () -> Unit,
    onShareClick: () -> Unit,
    onQuickShareClick: () -> Unit,
) {
    PasserelleSurfaceCard(
        selected = isSelected,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .defaultMinSize(minHeight = 60.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm + 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                FileThumbnailOrIcon(item = item, thumbnailBytes = thumbnailBytes, sizeDp = 40)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val sizeText = if (item.isDirectory) {
                        stringResource(Res.string.files_folder_label)
                    } else {
                        formatFileSize(item.sizeBytes)
                    }
                    Text(
                        text = sizeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    if (item.activeShareCount > 0) {
                        PasserelleStatusPill(
                            label = item.activeShareCount.toString(),
                            tone = PasserelleStatusTone.PRIMARY,
                        )
                    }
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        } else {
                            Color.Transparent
                        },
                    ) {
                        TooltipIconButton(
                            tooltipText = stringResource(Res.string.action_share),
                            icon = Icons.Default.Share,
                            tint = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            onClick = if (item.isDirectory) onShareClick else onQuickShareClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileGridCard(
    item: FileItem,
    thumbnailBytes: ByteArray?,
    isSelected: Boolean,
    onClick: () -> Unit,
    onShareClick: () -> Unit,
) {
    PasserelleSurfaceCard(
        selected = isSelected,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.sm + Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(108.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
                contentAlignment = Alignment.Center,
            ) {
                FileThumbnailOrIcon(item = item, thumbnailBytes = thumbnailBytes, sizeDp = 96)
                if (item.activeShareCount > 0) {
                    PasserelleStatusPill(
                        label = stringResource(Res.string.files_active_shares_badge, item.activeShareCount),
                        tone = PasserelleStatusTone.PRIMARY,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(Spacing.xs + 2.dp),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (item.isDirectory) {
                            stringResource(Res.string.files_folder_label)
                        } else {
                            formatFileSize(item.sizeBytes)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = onShareClick,
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = stringResource(Res.string.action_share),
                        tint = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun FileThumbnailOrIcon(
    item: FileItem,
    thumbnailBytes: ByteArray?,
    sizeDp: Int,
) {
    val bitmap: ImageBitmap? = remember(thumbnailBytes) {
        thumbnailBytes?.let { bytes ->
            runCatching { bytes.decodeToImageBitmap() }.getOrNull()
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(MaterialTheme.shapes.small),
        )
    } else {
        val (containerColor, contentColor) = fileBadgeColors(item)
        PasserelleIconBadge(
            icon = iconForFileItem(item),
            containerColor = containerColor,
            contentColor = contentColor,
            size = sizeDp.dp,
            iconSize = (sizeDp * 0.52f).dp,
        )
    }
}

@Composable
private fun fileBadgeColors(item: FileItem): Pair<Color, Color> {
    if (item.isDirectory) {
        return MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
    }
    val mime = item.mimeType.orEmpty().lowercase()
    return when {
        mime == "application/pdf" ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        mime.startsWith("image/") || mime.startsWith("video/") || mime.startsWith("audio/") ->
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        else ->
            MaterialTheme.colorScheme.surfaceContainer to MaterialTheme.colorScheme.onSurfaceVariant
    }
}

private fun iconForFileItem(item: FileItem): ImageVector {
    if (item.isDirectory) return Icons.Default.Folder
    val mime = item.mimeType.orEmpty().lowercase()
    return when {
        mime.startsWith("image/") -> Icons.Default.Image
        mime.startsWith("video/") -> Icons.Default.VideoFile
        mime.startsWith("audio/") -> Icons.Default.AudioFile
        mime == "application/pdf" -> Icons.Default.PictureAsPdf
        mime.contains("zip") || mime.contains("tar") || mime.contains("rar") || mime.contains("7z") ->
            Icons.Default.Archive
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun InspectorAndSharePanel(
    item: FileItem,
    thumbnailBytes: ByteArray?,
    existingShares: List<ShareLink>,
    isLoadingShares: Boolean,
    form: CreateShareFormState,
    lastCreatedShare: ShareLink?,
    onDismiss: () -> Unit,
    onDownloadDirect: () -> Unit,
    onQuickShare: () -> Unit,
    onExpiryPresetChanged: (ExpiryPreset) -> Unit,
    onQuotaPresetChanged: (QuotaPreset) -> Unit,
    onCustomQuotaChanged: (String) -> Unit,
    onSharePasswordChanged: (String) -> Unit,
    onCustomSlugChanged: (String) -> Unit,
    onCreateShare: () -> Unit,
    onCopyLink: (String) -> Unit,
    onShareLink: (ShareLink) -> Unit,
    onOpenQr: (ShareLink) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            val (badgeBg, badgeFg) = fileBadgeColors(item)
            PasserelleIconBadge(
                icon = iconForFileItem(item),
                containerColor = badgeBg,
                contentColor = badgeFg,
                size = 36.dp,
                iconSize = 18.dp,
            )
            Text(
                text = stringResource(Res.string.files_create_share_title, item.name),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TooltipIconButton(
                tooltipText = stringResource(Res.string.action_close),
                icon = Icons.Default.Close,
                onClick = onDismiss,
            )
        }

        val largeBitmap = remember(thumbnailBytes) {
            thumbnailBytes?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() }
        }
        if (largeBitmap != null) {
            Image(
                bitmap = largeBitmap,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(MaterialTheme.shapes.medium),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            FilledTonalButton(
                onClick = onQuickShare,
                shape = CircleShape,
                modifier = Modifier
                    .weight(1f)
                    .pointerHoverIcon(PointerIcon.Hand),
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    text = stringResource(Res.string.files_share_action_quick),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (!item.isDirectory) {
                OutlinedButton(
                    onClick = onDownloadDirect,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text(stringResource(Res.string.action_download))
                }
            }
        }

        lastCreatedShare?.takeIf { it.relativePath == item.path }?.let { created ->
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.sm + Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = stringResource(Res.string.files_share_created_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            Text(
                                text = created.publicUrl,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            TooltipIconButton(
                                tooltipText = stringResource(Res.string.action_copy_link),
                                icon = Icons.Default.ContentCopy,
                                tint = MaterialTheme.colorScheme.primary,
                                onClick = { onCopyLink(created.publicUrl) },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        AssistChip(
                            onClick = { onShareLink(created) },
                            label = { Text(stringResource(Res.string.action_share)) },
                            leadingIcon = { Icon(Icons.Default.Share, null, Modifier.size(16.dp)) },
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                        )
                        AssistChip(
                            onClick = { onOpenQr(created) },
                            label = { Text(stringResource(Res.string.action_qr_code)) },
                            leadingIcon = { Icon(Icons.Default.QrCode2, null, Modifier.size(16.dp)) },
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        Text(
            text = stringResource(Res.string.files_expiry_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp),
        ) {
            ExpiryOptionChip(stringResource(Res.string.files_expiry_1h), form.expiryPreset == ExpiryPreset.ONE_HOUR) {
                onExpiryPresetChanged(ExpiryPreset.ONE_HOUR)
            }
            ExpiryOptionChip(stringResource(Res.string.files_expiry_24h), form.expiryPreset == ExpiryPreset.ONE_DAY) {
                onExpiryPresetChanged(ExpiryPreset.ONE_DAY)
            }
            ExpiryOptionChip(stringResource(Res.string.files_expiry_7d), form.expiryPreset == ExpiryPreset.SEVEN_DAYS) {
                onExpiryPresetChanged(ExpiryPreset.SEVEN_DAYS)
            }
            ExpiryOptionChip(stringResource(Res.string.files_expiry_30d), form.expiryPreset == ExpiryPreset.THIRTY_DAYS) {
                onExpiryPresetChanged(ExpiryPreset.THIRTY_DAYS)
            }
            ExpiryOptionChip(stringResource(Res.string.files_expiry_never), form.expiryPreset == ExpiryPreset.NEVER) {
                onExpiryPresetChanged(ExpiryPreset.NEVER)
            }
        }

        Text(
            text = stringResource(Res.string.files_quota_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp),
        ) {
            val useCustom = form.customQuotaText.isNotEmpty()
            ExpiryOptionChip(stringResource(Res.string.files_quota_1), !useCustom && form.quotaPreset == QuotaPreset.ONE) {
                onQuotaPresetChanged(QuotaPreset.ONE)
            }
            ExpiryOptionChip(stringResource(Res.string.files_quota_5), !useCustom && form.quotaPreset == QuotaPreset.FIVE) {
                onQuotaPresetChanged(QuotaPreset.FIVE)
            }
            ExpiryOptionChip(stringResource(Res.string.files_quota_10), !useCustom && form.quotaPreset == QuotaPreset.TEN) {
                onQuotaPresetChanged(QuotaPreset.TEN)
            }
            ExpiryOptionChip(stringResource(Res.string.files_quota_unlimited), !useCustom && form.quotaPreset == QuotaPreset.UNLIMITED) {
                onQuotaPresetChanged(QuotaPreset.UNLIMITED)
            }
        }

        OutlinedTextField(
            value = form.customQuotaText,
            onValueChange = onCustomQuotaChanged,
            label = { Text(stringResource(Res.string.files_quota_custom)) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = form.password,
            onValueChange = onSharePasswordChanged,
            label = { Text(stringResource(Res.string.files_password_optional)) },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = form.customSlug,
            onValueChange = onCustomSlugChanged,
            label = { Text(stringResource(Res.string.files_custom_slug_optional)) },
            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        )

        form.error?.let { uiError ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = uiError.resolveMessage(),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(Spacing.sm),
                )
            }
        }

        Button(
            onClick = onCreateShare,
            enabled = !form.isSubmitting,
            shape = CircleShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .pointerHoverIcon(if (form.isSubmitting) PointerIcon.Default else PointerIcon.Hand),
        ) {
            if (form.isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(stringResource(Res.string.files_create_share_button))
            }
        }

        if (isLoadingShares) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else if (existingShares.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Text(
                text = stringResource(Res.string.files_existing_shares, existingShares.size),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            existingShares.forEach { share ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.sm + 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "/${share.slug}",
                                style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = share.publicUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TooltipIconButton(
                            tooltipText = stringResource(Res.string.action_copy_link),
                            icon = Icons.Default.ContentCopy,
                            onClick = { onCopyLink(share.publicUrl) },
                        )
                        TooltipIconButton(
                            tooltipText = stringResource(Res.string.action_qr_code),
                            icon = Icons.Default.QrCode2,
                            onClick = { onOpenQr(share) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpiryOptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    PasserelleFilterPill(
        label = label,
        selected = selected,
        onClick = onClick,
    )
}
