package fr.passerelle.client.feature.auth.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.passerelle.client.core.designsystem.component.PasserelleBrandMark
import fr.passerelle.client.core.designsystem.component.PasserelleReadOnlyPill
import fr.passerelle.client.core.designsystem.error.resolveMessage
import fr.passerelle.client.core.designsystem.resources.Res
import fr.passerelle.client.core.designsystem.resources.app_readonly_pill
import fr.passerelle.client.core.designsystem.resources.app_tagline
import fr.passerelle.client.core.designsystem.resources.auth_connecting
import fr.passerelle.client.core.designsystem.resources.auth_hide_password
import fr.passerelle.client.core.designsystem.resources.auth_login_button
import fr.passerelle.client.core.designsystem.resources.auth_password_label
import fr.passerelle.client.core.designsystem.resources.auth_server_url_label
import fr.passerelle.client.core.designsystem.resources.auth_server_url_placeholder
import fr.passerelle.client.core.designsystem.resources.auth_show_password
import fr.passerelle.client.core.designsystem.resources.auth_subtitle
import fr.passerelle.client.core.designsystem.resources.auth_title
import fr.passerelle.client.core.designsystem.resources.auth_username_label
import fr.passerelle.client.core.designsystem.theme.Layout
import fr.passerelle.client.core.designsystem.theme.Spacing
import fr.passerelle.client.feature.auth.presentation.AuthEvent
import fr.passerelle.client.feature.auth.presentation.AuthUiState
import fr.passerelle.client.feature.auth.presentation.AuthViewModel
import org.jetbrains.compose.resources.stringResource

@Composable
fun AuthRoute(
    viewModel: AuthViewModel,
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                AuthEvent.LoginSucceeded -> onLoginSuccess()
            }
        }
    }

    AuthScreenContent(
        uiState = uiState,
        onServerUrlChanged = viewModel::onServerUrlChanged,
        onUsernameChanged = viewModel::onUsernameChanged,
        onPasswordChanged = viewModel::onPasswordChanged,
        onTogglePasswordVisibility = viewModel::onTogglePasswordVisibility,
        onSubmit = viewModel::submit,
        modifier = modifier,
    )
}

@Composable
fun AuthScreenContent(
    uiState: AuthUiState,
    onServerUrlChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = Layout.maxFormWidth)
                    .fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.lg)
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown &&
                                (event.key == Key.Enter || event.key == Key.NumPadEnter) &&
                                !uiState.isLoading
                            ) {
                                onSubmit()
                                true
                            } else {
                                false
                            }
                        },
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        PasserelleBrandMark(
                            title = stringResource(Res.string.auth_title),
                            subtitle = stringResource(Res.string.app_tagline),
                            iconSize = 42.dp,
                        )
                        PasserelleReadOnlyPill(
                            label = stringResource(Res.string.app_readonly_pill),
                        )
                        Text(
                            text = stringResource(Res.string.auth_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (uiState.isServerUrlEditable) {
                        OutlinedTextField(
                            value = uiState.serverUrl,
                            onValueChange = onServerUrlChanged,
                            label = { Text(stringResource(Res.string.auth_server_url_label)) },
                            placeholder = { Text(stringResource(Res.string.auth_server_url_placeholder)) },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.Cloud, contentDescription = null)
                            },
                            singleLine = true,
                            enabled = !uiState.isLoading,
                            shape = MaterialTheme.shapes.small,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Next,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    OutlinedTextField(
                        value = uiState.username,
                        onValueChange = onUsernameChanged,
                        label = { Text(stringResource(Res.string.auth_username_label)) },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Person, contentDescription = null)
                        },
                        singleLine = true,
                        enabled = !uiState.isLoading,
                        shape = MaterialTheme.shapes.small,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = uiState.password,
                        onValueChange = onPasswordChanged,
                        label = { Text(stringResource(Res.string.auth_password_label)) },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Lock, contentDescription = null)
                        },
                        trailingIcon = {
                            val description = if (uiState.isPasswordVisible) {
                                stringResource(Res.string.auth_hide_password)
                            } else {
                                stringResource(Res.string.auth_show_password)
                            }
                            IconButton(
                                onClick = onTogglePasswordVisibility,
                                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                            ) {
                                Icon(
                                    imageVector = if (uiState.isPasswordVisible) {
                                        Icons.Default.VisibilityOff
                                    } else {
                                        Icons.Default.Visibility
                                    },
                                    contentDescription = description,
                                )
                            }
                        },
                        visualTransformation = if (uiState.isPasswordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        singleLine = true,
                        enabled = !uiState.isLoading,
                        shape = MaterialTheme.shapes.small,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    uiState.error?.let { uiError ->
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = uiError.resolveMessage(),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(Spacing.sm + Spacing.xs),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.xs))

                    Button(
                        onClick = onSubmit,
                        enabled = !uiState.isLoading,
                        shape = CircleShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .pointerHoverIcon(if (uiState.isLoading) PointerIcon.Default else PointerIcon.Hand),
                    ) {
                        if (uiState.isLoading) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Text(text = stringResource(Res.string.auth_connecting))
                            }
                        } else {
                            Text(text = stringResource(Res.string.auth_login_button))
                        }
                    }
                }
            }
        }
    }
}
