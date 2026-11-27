package fr.passerelle.client.feature.shares.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.passerelle.client.core.designsystem.component.SvgQrCodeView
import fr.passerelle.client.core.designsystem.component.TooltipIconButton
import fr.passerelle.client.core.designsystem.error.resolveMessage
import fr.passerelle.client.core.designsystem.resources.Res
import fr.passerelle.client.core.designsystem.resources.action_cancel
import fr.passerelle.client.core.designsystem.resources.action_close
import fr.passerelle.client.core.designsystem.resources.action_copy_link
import fr.passerelle.client.core.designsystem.resources.action_delete
import fr.passerelle.client.core.designsystem.resources.action_edit
import fr.passerelle.client.core.designsystem.resources.action_qr_code
import fr.passerelle.client.core.designsystem.resources.action_refresh
import fr.passerelle.client.core.designsystem.resources.action_retry
import fr.passerelle.client.core.designsystem.resources.action_save
import fr.passerelle.client.core.designsystem.resources.action_share
import fr.passerelle.client.core.designsystem.resources.files_clear_search
import fr.passerelle.client.core.designsystem.resources.files_quota_custom
import fr.passerelle.client.core.designsystem.resources.shares_clear_expiration
import fr.passerelle.client.core.designsystem.resources.shares_clear_password
import fr.passerelle.client.core.designsystem.resources.shares_clear_quota
import fr.passerelle.client.core.designsystem.resources.shares_delete_confirm_body
import fr.passerelle.client.core.designsystem.resources.shares_delete_confirm_title
import fr.passerelle.client.core.designsystem.resources.shares_downloads_quota
import fr.passerelle.client.core.designsystem.resources.shares_downloads_unlimited
import fr.passerelle.client.core.designsystem.resources.shares_edit_title
import fr.passerelle.client.core.designsystem.resources.shares_empty
import fr.passerelle.client.core.designsystem.resources.shares_filter_active
import fr.passerelle.client.core.designsystem.resources.shares_filter_all
import fr.passerelle.client.core.designsystem.resources.shares_filter_inactive
import fr.passerelle.client.core.designsystem.resources.shares_new_password
import fr.passerelle.client.core.designsystem.resources.shares_protected_password
import fr.passerelle.client.core.designsystem.resources.shares_qr_title
import fr.passerelle.client.core.designsystem.resources.shares_revoke_toggle
import fr.passerelle.client.core.designsystem.resources.shares_search_placeholder
import fr.passerelle.client.core.designsystem.resources.shares_status_active
import fr.passerelle.client.core.designsystem.resources.shares_status_expired
import fr.passerelle.client.core.designsystem.resources.shares_status_revoked
import fr.passerelle.client.core.designsystem.resources.shares_zip_archive
import fr.passerelle.client.core.designsystem.resources.snackbar_link_copied
import fr.passerelle.client.core.designsystem.resources.snackbar_share_deleted
import fr.passerelle.client.core.designsystem.resources.snackbar_share_updated
import fr.passerelle.client.core.designsystem.theme.Spacing
import fr.passerelle.client.core.domain.model.ShareLink
import fr.passerelle.client.feature.shares.presentation.EditShareDialogState
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
    Scaffold(
        modifier = modifier.fillMaxSize(),
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
                        placeholder = { Text(stringResource(Res.string.shares_search_placeholder)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                TooltipIconButton(
                                    tooltipText = stringResource(Res.string.files_clear_search),
                                    icon = Icons.Default.Close,
                                    onClick = { onSearchQueryChanged("") },
                                )
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(Spacing.lg),
                        modifier = Modifier.weight(1f),
                    )
                    TooltipIconButton(
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
                    FilterChip(
                        selected = uiState.filter == ShareFilter.ALL,
                        onClick = { onFilterChanged(ShareFilter.ALL) },
                        label = { Text(stringResource(Res.string.shares_filter_all)) },
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    )
                    FilterChip(
                        selected = uiState.filter == ShareFilter.ACTIVE,
                        onClick = { onFilterChanged(ShareFilter.ACTIVE) },
                        label = { Text(stringResource(Res.string.shares_filter_active)) },
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    )
                    FilterChip(
                        selected = uiState.filter == ShareFilter.INACTIVE,
                        onClick = { onFilterChanged(ShareFilter.INACTIVE) },
                        label = { Text(stringResource(Res.string.shares_filter_inactive)) },
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    )
                }
            }

            if (uiState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
            title = { Text(stringResource(Res.string.shares_delete_confirm_title)) },
            text = { Text(stringResource(Res.string.shares_delete_confirm_body, deleting.slug)) },
            confirmButton = {
                Button(onClick = onConfirmDelete) {
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

@Composable
private fun ShareCard(
    share: ShareLink,
    onCopyLink: () -> Unit,
    onShareLink: () -> Unit,
    onOpenQr: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Icon(
                    imageVector = if (share.isDirectory) Icons.Default.FolderZip else Icons.AutoMirrored.Filled.InsertDriveFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = share.targetName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (share.isDirectory) {
                            "${share.relativePath} • ${stringResource(Res.string.shares_zip_archive)}"
                        } else {
                            share.relativePath
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (share.hasPassword) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = stringResource(Res.string.shares_protected_password),
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            text = when {
                                share.isActive -> stringResource(Res.string.shares_status_active)
                                share.isRevoked -> stringResource(Res.string.shares_status_revoked)
                                else -> stringResource(Res.string.shares_status_expired)
                            },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (share.isActive) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        },
                        labelColor = if (share.isActive) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        },
                    ),
                )
            }

            Text(
                text = share.publicUrl,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            val maxDl = share.maxDownloads
            if (maxDl != null && maxDl > 0) {
                Text(
                    text = stringResource(Res.string.shares_downloads_quota, share.downloadCount, maxDl),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { (share.downloadCount.toFloat() / maxDl.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                )
            } else {
                Text(
                    text = stringResource(Res.string.shares_downloads_unlimited, share.downloadCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            share.expiresAt?.let { exp ->
                Text(
                    text = exp.toString().replace('T', ' ').substringBefore('.'),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TooltipIconButton(
                    tooltipText = stringResource(Res.string.action_copy_link),
                    icon = Icons.Default.ContentCopy,
                    onClick = onCopyLink,
                )
                TooltipIconButton(
                    tooltipText = stringResource(Res.string.action_share),
                    icon = Icons.Default.Share,
                    onClick = onShareLink,
                )
                TooltipIconButton(
                    tooltipText = stringResource(Res.string.action_qr_code),
                    icon = Icons.Default.QrCode2,
                    onClick = onOpenQr,
                )
                TooltipIconButton(
                    tooltipText = stringResource(Res.string.action_edit),
                    icon = Icons.Default.Edit,
                    onClick = onEdit,
                )
                TooltipIconButton(
                    tooltipText = stringResource(Res.string.action_delete),
                    icon = Icons.Default.Delete,
                    onClick = onDelete,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun EditShareDialog(
    state: EditShareDialogState,
    onDismiss: () -> Unit,
    onRevokedChanged: (Boolean) -> Unit,
    onMaxDownloadsChanged: (String) -> Unit,
    onClearMaxDownloadsChanged: (Boolean) -> Unit,
    onClearExpirationChanged: (Boolean) -> Unit,
    onNewPasswordChanged: (String) -> Unit,
    onClearPasswordChanged: (Boolean) -> Unit,
    onSubmit: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.shares_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.shares_revoke_toggle),
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = state.isRevoked,
                        onCheckedChange = onRevokedChanged,
                    )
                }

                OutlinedTextField(
                    value = state.maxDownloadsText,
                    onValueChange = onMaxDownloadsChanged,
                    enabled = !state.clearMaxDownloads,
                    label = { Text(stringResource(Res.string.files_quota_custom)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = state.clearMaxDownloads,
                        onCheckedChange = onClearMaxDownloadsChanged,
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Text(stringResource(Res.string.shares_clear_quota))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = state.clearExpiration,
                        onCheckedChange = onClearExpirationChanged,
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Text(stringResource(Res.string.shares_clear_expiration))
                }

                OutlinedTextField(
                    value = state.newPassword,
                    onValueChange = onNewPasswordChanged,
                    enabled = !state.clearPassword,
                    label = { Text(stringResource(Res.string.shares_new_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = state.clearPassword,
                        onCheckedChange = onClearPasswordChanged,
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Text(stringResource(Res.string.shares_clear_password))
                }

                state.error?.let { err ->
                    Text(
                        text = err.resolveMessage(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onSubmit, enabled = !state.isSubmitting) {
                Text(stringResource(Res.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}
