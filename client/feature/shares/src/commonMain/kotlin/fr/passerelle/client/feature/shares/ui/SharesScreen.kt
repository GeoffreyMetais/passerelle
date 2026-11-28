package fr.passerelle.client.feature.shares.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.passerelle.client.core.designsystem.component.PasserelleFilterPill
import fr.passerelle.client.core.designsystem.component.PasserelleSearchBar
import fr.passerelle.client.core.designsystem.component.PasserelleStatusPill
import fr.passerelle.client.core.designsystem.component.PasserelleStatusTone
import fr.passerelle.client.core.designsystem.component.PasserelleToolbarButton
import fr.passerelle.client.core.designsystem.component.SvgQrCodeView
import fr.passerelle.client.core.designsystem.error.resolveMessage
import fr.passerelle.client.core.designsystem.resources.Res
import fr.passerelle.client.core.designsystem.resources.action_cancel
import fr.passerelle.client.core.designsystem.resources.action_close
import fr.passerelle.client.core.designsystem.resources.action_delete
import fr.passerelle.client.core.designsystem.resources.action_refresh
import fr.passerelle.client.core.designsystem.resources.action_retry
import fr.passerelle.client.core.designsystem.resources.files_clear_search
import fr.passerelle.client.core.designsystem.resources.shares_active_count
import fr.passerelle.client.core.designsystem.resources.shares_active_count_single
import fr.passerelle.client.core.designsystem.resources.shares_delete_confirm_body
import fr.passerelle.client.core.designsystem.resources.shares_delete_confirm_title
import fr.passerelle.client.core.designsystem.resources.shares_empty
import fr.passerelle.client.core.designsystem.resources.shares_filter_active
import fr.passerelle.client.core.designsystem.resources.shares_filter_all
import fr.passerelle.client.core.designsystem.resources.shares_filter_inactive
import fr.passerelle.client.core.designsystem.resources.shares_qr_title
import fr.passerelle.client.core.designsystem.resources.shares_search_placeholder
import fr.passerelle.client.core.designsystem.resources.shares_subtitle
import fr.passerelle.client.core.designsystem.resources.shares_title
import fr.passerelle.client.core.designsystem.resources.snackbar_link_copied
import fr.passerelle.client.core.designsystem.resources.snackbar_share_deleted
import fr.passerelle.client.core.designsystem.resources.snackbar_share_updated
import fr.passerelle.client.core.designsystem.theme.Spacing
import fr.passerelle.client.core.domain.model.ShareLink
import fr.passerelle.client.feature.shares.presentation.ShareFilter
import fr.passerelle.client.feature.shares.presentation.SharesEvent
import fr.passerelle.client.feature.shares.presentation.SharesUiState
import fr.passerelle.client.feature.shares.presentation.SharesViewModel
import org.jetbrains.compose.resources.stringResource

@Composable
fun SharesRoute(
    viewModel: SharesViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val copiedMsg = stringResource(Res.string.snackbar_link_copied)
    val updatedMsg = stringResource(Res.string.snackbar_share_updated)
    val deletedMsg = stringResource(Res.string.snackbar_share_deleted)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SharesEvent.ShowMessage -> {
                    val msg = when (event.type) {
                        SharesEvent.ShowMessage.MessageType.LINK_COPIED -> copiedMsg
                        SharesEvent.ShowMessage.MessageType.SHARE_UPDATED -> updatedMsg
                        SharesEvent.ShowMessage.MessageType.SHARE_DELETED -> deletedMsg
                    }
                    snackbarHostState.showSnackbar(msg)
                }
            }
        }
    }

    SharesScreenContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onRefresh = viewModel::loadShares,
        onFilterChanged = viewModel::onFilterChanged,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onCopyLink = viewModel::copyLink,
        onShareLink = viewModel::shareLink,
        onOpenQr = viewModel::openQrModal,
        onDismissQr = viewModel::dismissQrModal,
        onOpenEdit = viewModel::openEditDialog,
        onDismissEdit = viewModel::dismissEditDialog,
        onEditRevokedChanged = viewModel::onEditRevokedChanged,
        onEditMaxDownloadsChanged = viewModel::onEditMaxDownloadsChanged,
        onEditClearMaxDownloadsChanged = viewModel::onEditClearMaxDownloadsChanged,
        onEditClearExpirationChanged = viewModel::onEditClearExpirationChanged,
        onEditNewPasswordChanged = viewModel::onEditNewPasswordChanged,
        onEditClearPasswordChanged = viewModel::onEditClearPasswordChanged,
        onSubmitEdit = viewModel::submitEdit,
        onRequestDelete = viewModel::requestDelete,
        onDismissDelete = viewModel::dismissDelete,
        onConfirmDelete = viewModel::confirmDelete,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharesScreenContent(
    uiState: SharesUiState,
    snackbarHostState: SnackbarHostState,
    onRefresh: () -> Unit,
    onFilterChanged: (ShareFilter) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onCopyLink: (ShareLink) -> Unit,
    onShareLink: (ShareLink) -> Unit,
    onOpenQr: (ShareLink) -> Unit,
    onDismissQr: () -> Unit,
    onOpenEdit: (ShareLink) -> Unit,
    onDismissEdit: () -> Unit,
    onEditRevokedChanged: (Boolean) -> Unit,
    onEditMaxDownloadsChanged: (String) -> Unit,
    onEditClearMaxDownloadsChanged: (Boolean) -> Unit,
    onEditClearExpirationChanged: (Boolean) -> Unit,
    onEditNewPasswordChanged: (String) -> Unit,
    onEditClearPasswordChanged: (Boolean) -> Unit,
    onSubmitEdit: () -> Unit,
    onRequestDelete: (ShareLink) -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalCount = uiState.allShares.size
    val activeCount = remember(uiState.allShares) { uiState.allShares.count { it.isActive } }
    val inactiveCount = totalCount - activeCount

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm + Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text(
                            text = stringResource(Res.string.shares_title),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        val activeBadgeLabel = if (activeCount == 1) {
                            stringResource(Res.string.shares_active_count_single)
                        } else {
                            stringResource(Res.string.shares_active_count, activeCount)
                        }
                        PasserelleStatusPill(
                            label = activeBadgeLabel,
                            tone = PasserelleStatusTone.PRIMARY,
                        )
                    }
                    Text(
                        text = stringResource(Res.string.shares_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    PasserelleSearchBar(
                        query = uiState.searchQuery,
                        onQueryChange = onSearchQueryChanged,
                        placeholder = stringResource(Res.string.shares_search_placeholder),
                        clearContentDescription = stringResource(Res.string.files_clear_search),
                        modifier = Modifier.weight(1f),
                    )
                    PasserelleToolbarButton(
                        tooltipText = stringResource(Res.string.action_refresh),
                        icon = Icons.Default.Refresh,
                        onClick = onRefresh,
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    PasserelleFilterPill(
                        label = stringResource(Res.string.shares_filter_all),
                        selected = uiState.filter == ShareFilter.ALL,
                        count = totalCount,
                        showCheckOnSelected = true,
                        onClick = { onFilterChanged(ShareFilter.ALL) },
                    )
                    PasserelleFilterPill(
                        label = stringResource(Res.string.shares_filter_active),
                        selected = uiState.filter == ShareFilter.ACTIVE,
                        count = activeCount,
                        showCheckOnSelected = true,
                        onClick = { onFilterChanged(ShareFilter.ACTIVE) },
                    )
                    PasserelleFilterPill(
                        label = stringResource(Res.string.shares_filter_inactive),
                        selected = uiState.filter == ShareFilter.INACTIVE,
                        count = inactiveCount,
                        showCheckOnSelected = true,
                        onClick = { onFilterChanged(ShareFilter.INACTIVE) },
                    )
                }
            }

            if (uiState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            PullToRefreshBox(
                isRefreshing = uiState.isLoading,
                onRefresh = onRefresh,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                when {
                    uiState.error != null && uiState.filteredShares.isEmpty() -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center).padding(Spacing.lg),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Spacing.md),
                        ) {
                            Text(
                                text = uiState.error.resolveMessage(),
                                color = MaterialTheme.colorScheme.error,
                            )
                            FilledTonalButton(onClick = onRefresh) {
                                Text(stringResource(Res.string.action_retry))
                            }
                        }
                    }
                    !uiState.isLoading && uiState.filteredShares.isEmpty() -> {
                        Text(
                            text = stringResource(Res.string.shares_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center).padding(Spacing.lg),
                        )
                    }
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 340.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(Spacing.md),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                            verticalArrangement = Arrangement.spacedBy(Spacing.md),
                        ) {
                            items(uiState.filteredShares, key = { it.id }) { share ->
                                ShareCard(
                                    share = share,
                                    onCopyLink = { onCopyLink(share) },
                                    onShareLink = { onShareLink(share) },
                                    onOpenQr = { onOpenQr(share) },
                                    onEdit = { onOpenEdit(share) },
                                    onDelete = { onRequestDelete(share) },
                                )
                            }
                        }
                    }
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
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
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

    uiState.editingShare?.let { edit ->
        EditShareDialog(
            state = edit,
            onDismiss = onDismissEdit,
            onRevokedChanged = onEditRevokedChanged,
            onMaxDownloadsChanged = onEditMaxDownloadsChanged,
            onClearMaxDownloadsChanged = onEditClearMaxDownloadsChanged,
            onClearExpirationChanged = onEditClearExpirationChanged,
            onNewPasswordChanged = onEditNewPasswordChanged,
            onClearPasswordChanged = onEditClearPasswordChanged,
            onSubmit = onSubmitEdit,
        )
    }

    uiState.deletingShare?.let { deleting ->
        AlertDialog(
            onDismissRequest = onDismissDelete,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            title = { Text(stringResource(Res.string.shares_delete_confirm_title)) },
            text = { Text(stringResource(Res.string.shares_delete_confirm_body, deleting.slug)) },
            confirmButton = {
                Button(
                    onClick = onConfirmDelete,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text(stringResource(Res.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDelete) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }
}
