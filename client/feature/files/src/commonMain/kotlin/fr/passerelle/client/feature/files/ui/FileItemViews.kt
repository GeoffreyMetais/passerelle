package fr.passerelle.client.feature.files.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toDp
import fr.passerelle.client.core.designsystem.component.PasserelleIconBadge
import fr.passerelle.client.core.designsystem.component.PasserelleStatusPill
import fr.passerelle.client.core.designsystem.component.PasserelleStatusTone
import fr.passerelle.client.core.designsystem.component.PasserelleSurfaceCard
import fr.passerelle.client.core.designsystem.component.TooltipIconButton
import fr.passerelle.client.core.designsystem.format.formatFileSize
import fr.passerelle.client.core.designsystem.resources.Res
import fr.passerelle.client.core.designsystem.resources.action_share
import fr.passerelle.client.core.designsystem.resources.files_active_shares_badge
import fr.passerelle.client.core.designsystem.resources.files_folder_label
import fr.passerelle.client.core.designsystem.theme.Spacing
import fr.passerelle.client.core.domain.model.FileItem
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DetailPanelDivider(
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
internal fun FileListRow(
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
internal fun FileGridCard(
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
internal fun FileThumbnailOrIcon(
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
internal fun fileBadgeColors(item: FileItem): Pair<Color, Color> {
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

internal fun iconForFileItem(item: FileItem): ImageVector {
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
