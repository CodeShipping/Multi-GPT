package com.matrix.multigpt.presentation.ui.localai

import android.app.Activity
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matrix.multigpt.util.FeatureInstallState

/**
 * Entry point screen for Local AI feature.
 * Handles feature module installation before navigating to the actual feature.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalAIEntryScreen(
    onNavigateBack: () -> Unit,
    onFeatureReady: () -> Unit,
    viewModel: LocalAIEntryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current as Activity

    LaunchedEffect(uiState.isFeatureInstalled) {
        if (uiState.isFeatureInstalled) {
            onFeatureReady()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Local AI Models") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            when {
                uiState.isChecking -> {
                    CircularProgressIndicator()
                }
                uiState.isFeatureInstalled -> {
                    // Feature is installed, will navigate automatically
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text("Opening Local AI...")
                    }
                }
                else -> {
                    FeatureInstallContent(
                        installState = uiState.installState,
                        onInstall = { viewModel.installFeature() },
                        onCancel = { viewModel.cancelInstall() },
                        onRetry = { viewModel.installFeature() },
                        onConfirmationRequired = { state ->
                            viewModel.requestUserConfirmation(context, state)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureInstallContent(
    installState: FeatureInstallState?,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onConfirmationRequired: (com.google.android.play.core.splitinstall.SplitInstallSessionState) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .animateContentSize()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Filled.SmartToy,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Local AI Models",
                style = MaterialTheme.typography.headlineSmall
            )

            Text(
                text = "Run AI models locally on your device for private, offline conversations. This feature requires an additional download.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Features list
            FeatureItem(icon = Icons.Filled.WifiOff, text = "Works offline")
            FeatureItem(icon = Icons.Filled.Lock, text = "Complete privacy")
            FeatureItem(icon = Icons.Filled.Speed, text = "Fast local inference")
            FeatureItem(icon = Icons.Filled.Download, text = "Multiple model options")

            Spacer(modifier = Modifier.height(8.dp))

            when (installState) {
                null -> {
                    Button(
                        onClick = onInstall,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Install Feature")
                    }
                }

                is FeatureInstallState.Pending,
                is FeatureInstallState.Started -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Preparing download...")
                    }
                }

                is FeatureInstallState.Downloading -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LinearProgressIndicator(
                            progress = { installState.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Downloading... ${(installState.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = onCancel) {
                            Text("Cancel")
                        }
                    }
                }

                is FeatureInstallState.Downloaded,
                is FeatureInstallState.Installing -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Installing...")
                    }
                }

                is FeatureInstallState.Installed -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Feature installed!")
                    }
                }

                is FeatureInstallState.Failed -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Filled.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = installState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onRetry) {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Retry")
                        }
                    }
                }

                is FeatureInstallState.RequiresConfirmation -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "This download requires your confirmation",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { onConfirmationRequired(installState.state) }) {
                            Text("Confirm Download")
                        }
                    }
                }

                is FeatureInstallState.Canceling,
                is FeatureInstallState.Canceled -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Download cancelled")
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onInstall) {
                            Text("Try Again")
                        }
                    }
                }

                is FeatureInstallState.Unknown -> {
                    Text("Unknown state: ${installState.status}")
                }
            }
        }
    }
}

@Composable
private fun FeatureItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
