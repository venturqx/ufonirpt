package org.schabi.newpipe.util

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import org.schabi.newpipe.R
import org.schabi.newpipe.ui.theme.AppTheme

class TournesolLoginDialog : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val viewModel = ViewModelProvider(this)[TournesolLoginViewModel::class.java]
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                val state = viewModel.state
                val inProgress = state == TournesolLoginState.InProgress
                SideEffect { isCancelable = !inProgress }
                LaunchedEffect(state) {
                    if (state == TournesolLoginState.Succeeded) onLoginSucceeded()
                }
                AppTheme {
                    LoginDialogContent(
                        inProgress = inProgress,
                        errorMessage = (state as? TournesolLoginState.Failed)
                            ?.let { stringResource(it.failure.messageRes) },
                        onDismiss = { if (!inProgress) dismiss() },
                        onRegister = { openRegisterPage() },
                        onLogin = viewModel::login
                    )
                }
            }
        }
    }

    private fun openRegisterPage() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(REGISTER_URL))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        requireContext().startActivity(intent)
    }

    private fun onLoginSucceeded() {
        Toast.makeText(
            requireContext(),
            getString(R.string.tournesol_login_success),
            Toast.LENGTH_SHORT
        ).show()
        parentFragmentManager.setFragmentResult(REQUEST_KEY, Bundle.EMPTY)
        dismissAllowingStateLoss()
    }

    companion object {
        const val REQUEST_KEY = "tournesol_login_request"
        private const val REGISTER_URL = "https://tournesol.app/signup"
    }
}

@Composable
internal fun LoginDialogContent(
    inProgress: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onRegister: () -> Unit,
    onLogin: (String, String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    val statusText = when {
        localError != null -> localError
        inProgress -> stringResource(R.string.tournesol_login_in_progress)
        errorMessage != null -> errorMessage
        else -> null
    }
    val statusColor = if (localError != null || errorMessage != null) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val missingFieldsText = stringResource(R.string.tournesol_login_missing_fields)

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.tournesol_login_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.compare_login_required),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = username,
                onValueChange = {
                    username = it
                    if (localError != null) localError = null
                },
                label = { Text(stringResource(R.string.tournesol_username_hint)) },
                singleLine = true,
                enabled = !inProgress,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    if (localError != null) localError = null
                },
                label = { Text(stringResource(R.string.tournesol_password_hint)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                enabled = !inProgress,
                modifier = Modifier.fillMaxWidth()
            )
            if (statusText != null) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onRegister,
                    enabled = !inProgress
                ) {
                    Text(stringResource(R.string.tournesol_register_button))
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !inProgress
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                        onClick = {
                            if (username.isBlank() || password.isBlank()) {
                                localError = missingFieldsText
                            } else {
                                localError = null
                                onLogin(username.trim(), password)
                            }
                        },
                        enabled = !inProgress
                    ) {
                        if (inProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(stringResource(R.string.tournesol_login_button))
                        }
                    }
                }
            }
        }
    }
}
