package fr.passerelle.client.feature.files.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import fr.passerelle.client.core.designsystem.component.PasserelleFilterPill
import fr.passerelle.client.core.designsystem.component.PasserelleSearchBar
import fr.passerelle.client.core.designsystem.component.PasserelleToolbarButton
import fr.passerelle.client.core.designsystem.resources.Res
import fr.passerelle.client.core.designsystem.resources.action_refresh
import fr.passerelle.client.core.designsystem.resources.files_clear_search
import fr.passerelle.client.core.designsystem.resources.files_item_count
import fr.passerelle.client.core.designsystem.resources.files_item_count_single
import fr.passerelle.client.core.designsystem.resources.files_search_placeholder_short
import fr.passerelle.client.core.designsystem.resources.files_search_shortcut
import fr.passerelle.client.core.designsystem.resources.files_show_hidden
import fr.passerelle.client.core.designsystem.resources.files_sort_date
import fr.passerelle.client.core.designsystem.resources.files_sort_name
import fr.passerelle.client.core.designsystem.resources.files_sort_size
import fr.passerelle.client.core.designsystem.resources.files_view_grid
import fr.passerelle.client.core.designsystem.resources.files_view_list
import fr.passerelle.client.core.designsystem.theme.Spacing
import fr.passerelle.client.core.domain.model.Breadcrumb
import fr.passerelle.client.core.domain.model.SortDirection
import fr.passerelle.client.core.domain.model.SortOption
import fr.passerelle.client.feature.files.presentation.FilesUiState
import fr.passerelle.client.feature.files.presentation.FilesViewMode
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun FilesHeaderToolbar(
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
internal fun SortChip(
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
