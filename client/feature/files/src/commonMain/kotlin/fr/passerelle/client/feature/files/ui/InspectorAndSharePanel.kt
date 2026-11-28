package fr.passerelle.client.feature.files.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.passerelle.client.core.designsystem.component.PasserelleFilterPill
import fr.passerelle.client.core.designsystem.component.PasserelleIconBadge
import fr.passerelle.client.core.designsystem.component.TooltipIconButton
import fr.passerelle.client.core.designsystem.error.resolveMessage
import fr.passerelle.client.core.designsystem.resources.Res
import fr.passerelle.client.core.designsystem.resources.action_close
import fr.passerelle.client.core.designsystem.resources.action_copy_link
import fr.passerelle.client.core.designsystem.resources.action_download
import fr.passerelle.client.core.designsystem.resources.action_qr_code
import fr.passerelle.client.core.designsystem.resources.action_share
import fr.passerelle.client.core.designsystem.resources.files_create_share_button
import fr.passerelle.client.core.designsystem.resources.files_create_share_title
import fr.passerelle.client.core.designsystem.resources.files_custom_slug_optional
import fr.passerelle.client.core.designsystem.resources.files_existing_shares
import fr.passerelle.client.core.designsystem.resources.files_expiry_1h
import fr.passerelle.client.core.designsystem.resources.files_expiry_24h
import fr.passerelle.client.core.designsystem.resources.files_expiry_30d
import fr.passerelle.client.core.designsystem.resources.files_expiry_7d
import fr.passerelle.client.core.designsystem.resources.files_expiry_label
import fr.passerelle.client.core.designsystem.resources.files_expiry_never
import fr.passerelle.client.core.designsystem.resources.files_password_optional
import fr.passerelle.client.core.designsystem.resources.files_quota_1
import fr.passerelle.client.core.designsystem.resources.files_quota_10
import fr.passerelle.client.core.designsystem.resources.files_quota_5
import fr.passerelle.client.core.designsystem.resources.files_quota_custom
import fr.passerelle.client.core.designsystem.resources.files_quota_label
import fr.passerelle.client.core.designsystem.resources.files_quota_unlimited
import fr.passerelle.client.core.designsystem.resources.files_share_action_quick
import fr.passerelle.client.core.designsystem.resources.files_share_created_title
import fr.passerelle.client.core.designsystem.theme.Spacing
import fr.passerelle.client.core.domain.model.FileItem
import fr.passerelle.client.core.domain.model.ShareLink
import fr.passerelle.client.feature.files.presentation.CreateShareFormState
import fr.passerelle.client.feature.files.presentation.ExpiryPreset
import fr.passerelle.client.feature.files.presentation.QuotaPreset
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalResourceApi::class)
@Composable
internal fun InspectorAndSharePanel(
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
internal fun ExpiryOptionChip(
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
