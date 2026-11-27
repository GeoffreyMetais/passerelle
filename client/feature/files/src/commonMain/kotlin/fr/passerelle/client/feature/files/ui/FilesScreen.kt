package fr.passerelle.client.feature.files.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AudioFile
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import fr.passerelle.client.core.designsystem.resources.files_password_optional
import fr.passerelle.client.core.designsystem.resources.files_quota_1
import fr.passerelle.client.core.designsystem.resources.files_quota_10
import fr.passerelle.client.core.designsystem.resources.files_quota_5
import fr.passerelle.client.core.designsystem.resources.files_quota_custom
import fr.passerelle.client.core.designsystem.resources.files_quota_label
import fr.passerelle.client.core.designsystem.resources.files_quota_unlimited
import fr.passerelle.client.core.designsystem.resources.files_search_placeholder
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
        onRefresh = viewModel::refresh,
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
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val isExpanded = maxWidth >= 840.dp

            Row(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    FilesHeaderToolbar(
                        uiState = uiState,
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

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
                                    contentPadding = PaddingValues(bottom = Spacing.xl),
                                ) {
                                    items(uiState.entries, key = { it.path }) { item ->
                                        FileListRow(
                                            item = item,
                                            thumbnailBytes = uiState.thumbnails[item.path],
                                            isSelected = uiState.selectedItem?.path == item.path,
                                            onClick = { onItemClick(item, true) },
                                            onShareClick = {
                                                if (isExpanded) onInspectItem(item) else onInspectItem(item)
                                            },
                                            onQuickShareClick = { onQuickShare(item) },
                                        )
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                                    }
                                }
                            }
                            else -> {
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = 180.dp),
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(Spacing.md),
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
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
                    Surface(
                        modifier = Modifier
                            .width(380.dp)
                            .fillMaxHeight(),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 1.dp,
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
private fun FilesHeaderToolbar(
    uiState: FilesUiState,
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
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchQueryChanged,
                placeholder = { Text(stringResource(Res.string.files_search_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        TooltipIconButton(
                            tooltipText = stringResource(Res.string.files_clear_search),
                            icon = Icons.Default.Close,
                            onClick = onClearSearch,
                        )
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(Spacing.lg),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(searchFocusRequester),
            )

            TooltipIconButton(
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

            TooltipIconButton(
                tooltipText = stringResource(Res.string.action_refresh),
                icon = Icons.Default.Refresh,
                onClick = onRefresh,
            )
        }

        if (!uiState.isSearchActive) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                uiState.breadcrumbs.forEachIndexed { index, crumb ->
                    if (index > 0) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    TextButton(
                        onClick = { onBreadcrumbClick(crumb) },
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
            FilterChip(
                selected = uiState.showHidden,
                onClick = onToggleShowHidden,
                label = { Text(stringResource(Res.string.files_show_hidden)) },
                leadingIcon = {
                    Icon(
                        imageVector = if (uiState.showHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
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
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        trailingIcon = if (selected) {
            {
                Icon(
                    imageVector = if (direction == SortDirection.ASC) {
                        Icons.Default.ArrowUpward
                    } else {
                        Icons.Default.ArrowDownward
                    },
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
        } else {
            null
        },
        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
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
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand),
        colors = ListItemDefaults.colors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        leadingContent = {
            FileThumbnailOrIcon(item = item, thumbnailBytes = thumbnailBytes, sizeDp = 44)
        },
        headlineContent = {
            Text(
                text = item.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
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
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                if (item.activeShareCount > 0) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Text(
                            text = item.activeShareCount.toString(),
                            modifier = Modifier.padding(horizontal = Spacing.xs),
                        )
                    }
                }
                TooltipIconButton(
                    tooltipText = stringResource(Res.string.action_share),
                    icon = Icons.Default.Share,
                    onClick = if (item.isDirectory) onShareClick else onQuickShareClick,
                )
            }
        },
    )
}

@Composable
private fun FileGridCard(
    item: FileItem,
    thumbnailBytes: ByteArray?,
    isSelected: Boolean,
    onClick: () -> Unit,
    onShareClick: () -> Unit,
) {
    ElevatedCard(
        onClick = onClick,
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .pointerHoverIcon(PointerIcon.Hand),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.sm + Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) {
                FileThumbnailOrIcon(item = item, thumbnailBytes = thumbnailBytes, sizeDp = 96)
                if (item.activeShareCount > 0) {
                    Badge(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(Spacing.xs),
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Text(item.activeShareCount.toString())
                    }
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
                        imageVector = Icons.Default.Link,
                        contentDescription = stringResource(Res.string.action_share),
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
        Icon(
            imageVector = iconForFileItem(item),
            contentDescription = null,
            tint = if (item.isDirectory) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size((sizeDp * 0.62f).dp),
        )
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
        ) {
            Text(
                text = stringResource(Res.string.files_create_share_title, item.name),
                style = MaterialTheme.typography.titleMedium,
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
                modifier = Modifier
                    .weight(1f)
                    .pointerHoverIcon(PointerIcon.Hand),
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.xs))
                Text(stringResource(Res.string.files_share_action_quick))
            }

            if (!item.isDirectory) {
                OutlinedButton(
                    onClick = onDownloadDirect,
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
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.sm + Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text(
                        text = stringResource(Res.string.files_share_created_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = created.publicUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        AssistChip(
                            onClick = { onCopyLink(created.publicUrl) },
                            label = { Text(stringResource(Res.string.action_copy_link)) },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp)) },
                        )
                        AssistChip(
                            onClick = { onShareLink(created) },
                            label = { Text(stringResource(Res.string.action_share)) },
                            leadingIcon = { Icon(Icons.Default.Share, null, Modifier.size(16.dp)) },
                        )
                        AssistChip(
                            onClick = { onOpenQr(created) },
                            label = { Text(stringResource(Res.string.action_qr_code)) },
                            leadingIcon = { Icon(Icons.Default.QrCode2, null, Modifier.size(16.dp)) },
                        )
                    }
                }
            }
        }

        HorizontalDivider()

        Text(
            text = stringResource(Res.string.files_expiry_label),
            style = MaterialTheme.typography.labelLarge,
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
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
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
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
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = form.customSlug,
            onValueChange = onCustomSlugChanged,
            label = { Text(stringResource(Res.string.files_custom_slug_optional)) },
            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        form.error?.let { uiError ->
            Text(
                text = uiError.resolveMessage(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Button(
            onClick = onCreateShare,
            enabled = !form.isSubmitting,
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
            HorizontalDivider()
            Text(
                text = stringResource(Res.string.files_existing_shares, existingShares.size),
                style = MaterialTheme.typography.titleSmall,
            )
            existingShares.forEach { share ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "/${share.slug}",
                                style = MaterialTheme.typography.labelLarge,
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
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
    )
}
