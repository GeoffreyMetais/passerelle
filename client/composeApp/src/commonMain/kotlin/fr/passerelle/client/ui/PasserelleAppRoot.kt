package fr.passerelle.client.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.window.core.layout.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import fr.passerelle.client.core.designsystem.component.PasserelleBrandMark
import fr.passerelle.client.core.designsystem.component.PasserelleReadOnlyPill
import fr.passerelle.client.core.designsystem.component.TooltipIconButton
import fr.passerelle.client.core.designsystem.resources.Res
import fr.passerelle.client.core.designsystem.resources.action_logout
import fr.passerelle.client.core.designsystem.resources.action_toggle_theme
import fr.passerelle.client.core.designsystem.resources.app_badge_readonly
import fr.passerelle.client.core.designsystem.resources.app_name
import fr.passerelle.client.core.designsystem.resources.app_readonly_pill
import fr.passerelle.client.core.designsystem.resources.nav_files
import fr.passerelle.client.core.designsystem.resources.nav_shares
import fr.passerelle.client.core.designsystem.theme.Layout
import fr.passerelle.client.core.designsystem.theme.PasserelleTheme
import fr.passerelle.client.core.designsystem.theme.Spacing
import fr.passerelle.client.core.domain.usecase.LogoutUseCase
import fr.passerelle.client.core.domain.usecase.ObserveAuthStateUseCase
import fr.passerelle.client.core.platform.PlatformActions
import fr.passerelle.client.feature.auth.presentation.AuthViewModel
import fr.passerelle.client.feature.auth.ui.AuthRoute
import fr.passerelle.client.feature.files.presentation.FilesViewModel
import fr.passerelle.client.feature.files.ui.FilesRoute
import fr.passerelle.client.feature.shares.presentation.SharesViewModel
import fr.passerelle.client.feature.shares.ui.SharesRoute
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Serializable
sealed interface AppDestination {
    @Serializable
    data object Files : AppDestination

    @Serializable
    data object Shares : AppDestination
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun PasserelleAppRoot() {
    val systemDark = isSystemInDarkTheme()
    var darkThemeOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val useDarkTheme = darkThemeOverride ?: systemDark

    val observeAuthStateUseCase: ObserveAuthStateUseCase = koinInject()
    val logoutUseCase: LogoutUseCase = koinInject()
    val platformActions: PlatformActions = koinInject()
    val isAuthenticated by observeAuthStateUseCase().collectAsStateWithLifecycle(initialValue = false)
    val coroutineScope = rememberCoroutineScope()
    val isRailLayout = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass != WindowWidthSizeClass.COMPACT

    PasserelleTheme(darkTheme = useDarkTheme) {
        if (!isAuthenticated) {
            val authViewModel: AuthViewModel = koinViewModel()
            AuthRoute(
                viewModel = authViewModel,
                onLoginSuccess = {},
            )
        } else {
            val navController = rememberNavController()
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.route.orEmpty()
            val isFilesSelected = !currentRoute.contains("Shares")

            fun navigateTo(dest: AppDestination) {
                navController.navigate(dest) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }

            NavigationSuiteScaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && (event.isCtrlPressed || event.isMetaPressed)) {
                            when (event.key) {
                                Key.One -> {
                                    navigateTo(AppDestination.Files)
                                    true
                                }
                                Key.Two -> {
                                    navigateTo(AppDestination.Shares)
                                    true
                                }
                                else -> false
                            }
                        } else {
                            false
                        }
                    },
                navigationSuiteColors = NavigationSuiteDefaults.colors(
                    navigationBarContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    navigationRailContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    navigationDrawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                ),
                navigationSuiteItems = {
                    item(
                        selected = isFilesSelected,
                        onClick = { navigateTo(AppDestination.Files) },
                        icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                        label = {
                            Text(
                                text = stringResource(Res.string.nav_files),
                                modifier = Modifier.padding(horizontal = Spacing.xs),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        modifier = Modifier
                            .padding(horizontal = Spacing.xs)
                            .then(if (isRailLayout) Modifier.padding(top = Spacing.sm) else Modifier)
                            .pointerHoverIcon(PointerIcon.Hand),
                    )
                    item(
                        selected = !isFilesSelected,
                        onClick = { navigateTo(AppDestination.Shares) },
                        icon = { Icon(Icons.Default.Link, contentDescription = null) },
                        label = {
                            Text(
                                text = stringResource(Res.string.nav_shares),
                                modifier = Modifier.padding(horizontal = Spacing.xs),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        modifier = Modifier
                            .padding(horizontal = Spacing.xs)
                            .then(if (isRailLayout) Modifier.padding(top = Spacing.sm) else Modifier)
                            .pointerHoverIcon(PointerIcon.Hand),
                    )
                },
            ) {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.surface,
                    topBar = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            TopAppBar(
                                title = {
                                    val sectionTitle = if (isFilesSelected) {
                                        stringResource(Res.string.nav_files)
                                    } else {
                                        stringResource(Res.string.nav_shares)
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                                    ) {
                                        PasserelleBrandMark(
                                            title = stringResource(Res.string.app_name),
                                            subtitle = sectionTitle,
                                            badgeText = if (!isRailLayout) {
                                                stringResource(Res.string.app_badge_readonly)
                                            } else {
                                                null
                                            },
                                        )
                                        if (isRailLayout) {
                                            PasserelleReadOnlyPill(
                                                label = stringResource(Res.string.app_readonly_pill),
                                            )
                                        }
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                                ),
                                actions = {
                                    TooltipIconButton(
                                        tooltipText = stringResource(Res.string.action_toggle_theme),
                                        icon = if (useDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                        onClick = { darkThemeOverride = !useDarkTheme },
                                    )
                                    TooltipIconButton(
                                        tooltipText = stringResource(Res.string.action_logout),
                                        icon = Icons.AutoMirrored.Filled.Logout,
                                        onClick = {
                                            coroutineScope.launch { logoutUseCase() }
                                        },
                                    )
                                },
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                            )
                        }
                    },
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = Layout.maxContentWidth)
                                .fillMaxHeight()
                                .align(Alignment.Center),
                        ) {
                            NavHost(
                                navController = navController,
                                startDestination = AppDestination.Files,
                            ) {
                                composable<AppDestination.Files> {
                                    val filesViewModel: FilesViewModel = koinViewModel()
                                    FilesRoute(
                                        viewModel = filesViewModel,
                                        onOpenExternalUrl = platformActions::openExternalUrl,
                                    )
                                }
                                composable<AppDestination.Shares> {
                                    val sharesViewModel: SharesViewModel = koinViewModel()
                                    SharesRoute(viewModel = sharesViewModel)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
