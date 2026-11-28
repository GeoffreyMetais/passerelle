package fr.passerelle.client.feature.shares.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.passerelle.client.core.designsystem.component.PasserelleActionStrip
import fr.passerelle.client.core.designsystem.component.PasserelleIconBadge
import fr.passerelle.client.core.designsystem.component.PasserelleStatusPill
import fr.passerelle.client.core.designsystem.component.PasserelleStatusTone
import fr.passerelle.client.core.designsystem.component.PasserelleSurfaceCard
import fr.passerelle.client.core.designsystem.component.TooltipIconButton
import fr.passerelle.client.core.designsystem.resources.Res
import fr.passerelle.client.core.designsystem.resources.action_copy_link
import fr.passerelle.client.core.designsystem.resources.action_delete
import fr.passerelle.client.core.designsystem.resources.action_edit
import fr.passerelle.client.core.designsystem.resources.action_qr_code
import fr.passerelle.client.core.designsystem.resources.action_share
import fr.passerelle.client.core.designsystem.resources.shares_downloads_quota
import fr.passerelle.client.core.designsystem.resources.shares_downloads_unlimited
import fr.passerelle.client.core.designsystem.resources.shares_expires_prefix
import fr.passerelle.client.core.designsystem.resources.shares_protected_password
import fr.passerelle.client.core.designsystem.resources.shares_status_active
import fr.passerelle.client.core.designsystem.resources.shares_status_expired
import fr.passerelle.client.core.designsystem.resources.shares_status_revoked
import fr.passerelle.client.core.designsystem.resources.shares_zip_archive
import fr.passerelle.client.core.designsystem.theme.Spacing
import fr.passerelle.client.core.domain.model.ShareLink
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ShareCard(
    share: ShareLink,
    onCopyLink: () -> Unit,
    onShareLink: () -> Unit,
    onOpenQr: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    PasserelleSurfaceCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                PasserelleIconBadge(
                    icon = if (share.isDirectory) Icons.Default.FolderZip else Icons.AutoMirrored.Filled.InsertDriveFile,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary,
                    size = 40.dp,
                    iconSize = 20.dp,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = share.targetName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
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
                PasserelleStatusPill(
                    label = when {
                        share.isActive -> stringResource(Res.string.shares_status_active)
                        share.isRevoked -> stringResource(Res.string.shares_status_revoked)
                        else -> stringResource(Res.string.shares_status_expired)
                    },
                    tone = if (share.isActive) {
                        PasserelleStatusTone.ACTIVE
                    } else {
                        PasserelleStatusTone.DANGER
                    },
                    leadingDot = true,
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = Spacing.sm + 2.dp, end = Spacing.xs, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = share.publicUrl,
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
                        onClick = onCopyLink,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp)) {
                val maxDl = share.maxDownloads
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        text = if (maxDl != null && maxDl > 0) {
                            stringResource(Res.string.shares_downloads_quota, share.downloadCount, maxDl)
                        } else {
                            stringResource(Res.string.shares_downloads_unlimited, share.downloadCount)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (maxDl != null && maxDl > 0) {
                    LinearProgressIndicator(
                        progress = { (share.downloadCount.toFloat() / maxDl.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                }
                share.expiresAt?.let { exp ->
                    val formattedDate = exp.toString().replace('T', ' ').substringBefore('.')
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Event,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = stringResource(Res.string.shares_expires_prefix, formattedDate),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            PasserelleActionStrip {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                }
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
