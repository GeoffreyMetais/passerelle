package fr.passerelle.client.feature.shares.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.shape.CircleShape
import fr.passerelle.client.core.designsystem.error.resolveMessage
import fr.passerelle.client.core.designsystem.resources.Res
import fr.passerelle.client.core.designsystem.resources.action_cancel
import fr.passerelle.client.core.designsystem.resources.action_save
import fr.passerelle.client.core.designsystem.resources.files_quota_custom
import fr.passerelle.client.core.designsystem.resources.shares_clear_expiration
import fr.passerelle.client.core.designsystem.resources.shares_clear_password
import fr.passerelle.client.core.designsystem.resources.shares_clear_quota
import fr.passerelle.client.core.designsystem.resources.shares_edit_title
import fr.passerelle.client.core.designsystem.resources.shares_new_password
import fr.passerelle.client.core.designsystem.resources.shares_revoke_toggle
import fr.passerelle.client.core.designsystem.theme.Spacing
import fr.passerelle.client.feature.shares.presentation.EditShareDialogState
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun EditShareDialog(
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
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
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
                    shape = MaterialTheme.shapes.small,
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
                    shape = MaterialTheme.shapes.small,
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
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = err.resolveMessage(),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(Spacing.sm),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSubmit,
                enabled = !state.isSubmitting,
                shape = CircleShape,
            ) {
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
