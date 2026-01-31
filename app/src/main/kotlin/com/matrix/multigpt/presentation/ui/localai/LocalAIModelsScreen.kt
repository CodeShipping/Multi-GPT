package com.matrix.multigpt.presentation.ui.localai

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Screen that displays available Local AI Models.
 * Uses ViewModel for persistent state across navigation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalAIModelsScreen(
    viewModel: LocalAIModelsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToChat: (modelId: String, modelPath: String) -> Unit
) {
    val context = LocalContext.current
    
    // Collect state from ViewModel
    val models by viewModel.models.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val downloadingModels by viewModel.downloadingModels.collectAsStateWithLifecycle()
    val downloadProgressMap by viewModel.downloadProgressMap.collectAsStateWithLifecycle()
    val downloadedBytesMap by viewModel.downloadedBytesMap.collectAsStateWithLifecycle()
    val totalBytesMap by viewModel.totalBytesMap.collectAsStateWithLifecycle()
    val downloadedModels by viewModel.downloadedModels.collectAsStateWithLifecycle()
    val selectedModelId by viewModel.selectedModelId.collectAsStateWithLifecycle()
    val localEnabled by viewModel.localEnabled.collectAsStateWithLifecycle()
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()
    
    var showDeleteDialog by remember { mutableStateOf<ModelInfo?>(null) }

    // File picker launcher for importing GGUF files
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.importModels(uris)
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
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshModels() },
                        enabled = downloadingModels.isEmpty()
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Show error banner
            if (error != null && models.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = error!!,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            
            when {
                isLoading && models.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Loading models...")
                        }
                    }
                }
                models.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = error ?: "No models available",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Enable/Disable Local AI toggle at top
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Enable Local AI",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = "Run AI models offline on your device",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = localEnabled,
                                        onCheckedChange = { viewModel.setLocalEnabled(it) },
                                        enabled = downloadedModels.isNotEmpty() || !localEnabled
                                    )
                                }
                            }
                        }

                        // Import Model Card
                        item {
                            ImportModelCard(
                                onImportClick = {
                                    filePickerLauncher.launch(arrayOf("*/*"))
                                }
                            )
                        }

                        // Device Info Card
                        item {
                            DeviceInfoCard()
                        }

                        // Separate imported models from downloadable models
                        val importedModels = models.filter { it.isImported }
                        val downloadableModels = models.filter { !it.isImported }

                        // Imported Models Section (only show if there are imported models)
                        if (importedModels.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Imported Models",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            
                            items(importedModels, key = { it.id }) { model ->
                                val isDownloaded = downloadedModels.contains(model.id)
                                val isDownloading = downloadingModels.contains(model.id)
                                val isSelected = selectedModelId == model.id
                                
                                ModelCard(
                                    model = model,
                                    isDownloaded = isDownloaded,
                                    isDownloading = isDownloading,
                                    isSelected = isSelected,
                                    downloadProgress = downloadProgressMap[model.id] ?: 0f,
                                    downloadedBytes = downloadedBytesMap[model.id] ?: 0L,
                                    totalBytes = totalBytesMap[model.id] ?: 0L,
                                    onDownload = { viewModel.startDownload(model) },
                                    onDelete = { showDeleteDialog = model },
                                    onUseModel = {
                                        viewModel.selectModel(model)
                                        Toast.makeText(
                                            context, 
                                            "Selected ${model.name}. Create a new chat to use it.", 
                                            Toast.LENGTH_LONG
                                        ).show()
                                        onNavigateBack()
                                    },
                                    onClick = { }
                                )
                            }
                        }
                        
                        // Downloadable Models Section
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Downloadable Models",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        items(downloadableModels, key = { it.id }) { model ->
                            val isDownloaded = downloadedModels.contains(model.id)
                            val isDownloading = downloadingModels.contains(model.id)
                            val isSelected = selectedModelId == model.id
                            
                            ModelCard(
                                model = model,
                                isDownloaded = isDownloaded,
                                isDownloading = isDownloading,
                                isSelected = isSelected,
                                downloadProgress = downloadProgressMap[model.id] ?: 0f,
                                downloadedBytes = downloadedBytesMap[model.id] ?: 0L,
                                totalBytes = totalBytesMap[model.id] ?: 0L,
                                onDownload = { viewModel.startDownload(model) },
                                onDelete = { showDeleteDialog = model },
                                onUseModel = {
                                    viewModel.selectModel(model)
                                    Toast.makeText(
                                        context, 
                                        "Selected ${model.name}. Create a new chat to use it.", 
                                        Toast.LENGTH_LONG
                                    ).show()
                                    onNavigateBack()
                                },
                                onClick = { }
                            )
                        }
                    }
                }
            }
        }
        
        // Import Progress Dialog
        importProgress?.let { progress ->
            AlertDialog(
                onDismissRequest = { /* Non-dismissible */ },
                title = { Text("Importing Models") },
                text = {
                    Column {
                        Text("Importing ${progress.current} of ${progress.total}...")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = progress.fileName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { progress.current.toFloat() / progress.total },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = { }
            )
        }

        // Delete confirmation dialog
        if (showDeleteDialog != null) {
            val modelToDelete = showDeleteDialog!!
            AlertDialog(
                onDismissRequest = { showDeleteDialog = null },
                icon = {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                title = {
                    Text("Delete Model?")
                },
                text = {
                    Text("Are you sure you want to delete \"${modelToDelete.name}\"? This will remove the downloaded model file (${formatSize(modelToDelete.size)}) from your device.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteModel(modelToDelete)
                            Toast.makeText(context, "Deleted ${modelToDelete.name}", Toast.LENGTH_SHORT).show()
                            showDeleteDialog = null
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showDeleteDialog = null }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun ModelCard(
    model: ModelInfo,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    isSelected: Boolean,
    downloadProgress: Float,
    downloadedBytes: Long,
    totalBytes: Long,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onUseModel: () -> Unit,
    onClick: () -> Unit
) {
    // Imported models should show "Imported" tag, not "Downloaded"
    val isImported = model.isImported
    val showAsReady = isDownloaded || isImported
    
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (model.isRecommended) {
                    Spacer(modifier = Modifier.width(8.dp))
                    AssistChip(
                        onClick = {},
                        label = { Text("Recommended") }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = model.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Performance: ${model.performance}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            
            // Download progress
            if (isDownloading) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { downloadProgress },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Downloading... ${(downloadProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (downloadedBytes > 0 && totalBytes > 0) {
                        Text(
                            text = "${formatSize(downloadedBytes)} / ${formatSize(totalBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatSize(model.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                when {
                    showAsReady -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Show "Imported" for imported models, "Downloaded" for downloaded
                            AssistChip(
                                onClick = {},
                                label = { Text(if (isImported) "Imported" else "Downloaded") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            FilledTonalButton(
                                onClick = onDelete,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Delete")
                            }
                        }
                    }
                    isDownloading -> {
                        FilledTonalButton(
                            onClick = { },
                            enabled = false
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Downloading...")
                        }
                    }
                    else -> {
                        FilledTonalButton(onClick = onDownload) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Download")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Card for importing GGUF models from device storage.
 */
@Composable
private fun ImportModelCard(
    onImportClick: () -> Unit
) {
    var showSupportedModelsDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Import GGUF Model",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.tertiary,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "BETA",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "Import a .gguf model file from your device storage",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                FilledTonalButton(
                    onClick = onImportClick
                ) {
                    Icon(
                        Icons.Filled.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import")
                }
            }
            
            // Supported models info
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Supported Model Families",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "✓ Llama 3.x / 3.2 (recommended)\n✓ Qwen 2.5\n✓ Phi 3.x\n✓ Gemma 2\n✓ Mistral / Mixtral\n✓ SmolLM2",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Llama 2 and older models are NOT supported",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { showSupportedModelsDialog = true },
                        contentPadding = PaddingValues(horizontal = 0.dp)
                    ) {
                        Text(
                            text = "View full compatibility list",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
    
    // Supported Models Dialog
    if (showSupportedModelsDialog) {
        AlertDialog(
            onDismissRequest = { showSupportedModelsDialog = false },
            icon = {
                Icon(Icons.Filled.Info, contentDescription = null)
            },
            title = {
                Text("Model Compatibility")
            },
            text = {
                Column {
                    Text(
                        text = "Fully Supported (Recommended)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Llama 3.2 (1B, 3B) - Best performance\n• Llama 3.1 (8B with Q4)\n• Qwen 2.5 (0.5B, 1.5B, 3B, 7B)\n• Phi 3.5 Mini\n• Gemma 2 (2B, 9B)\n• Mistral 7B v0.3\n• SmolLM2 (135M, 360M)",
                        style = MaterialTheme.typography.bodySmall
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Experimental Support",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Yi 1.5 / Yi-Coder\n• DeepSeek Coder\n• StarCoder 2\n• CodeLlama",
                        style = MaterialTheme.typography.bodySmall
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "NOT Supported",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Llama 2 (all variants) ❌\n• Llama 1 ❌\n• Alpaca ❌\n• Vicuna ❌\n• Models without chat templates ❌",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "💡 Tip: Look for models with \"Instruct\" or \"Chat\" in the name. Use Q4_K_M quantization for best compatibility.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSupportedModelsDialog = false }) {
                    Text("Got it")
                }
            }
        )
    }
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}

/**
 * Card showing device RAM info and model size recommendations.
 */
@Composable
private fun DeviceInfoCard() {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    
    // Get device RAM
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memInfo)
    val totalRamGB = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
    val availableRamGB = memInfo.availMem / (1024.0 * 1024.0 * 1024.0)
    
    // Determine recommendations based on RAM
    val (maxModelSize, recommendation, recommendedModels) = when {
        totalRamGB >= 16 -> Triple(
            "~8GB",
            "Your device can run most models including 7B-8B parameter models",
            listOf("Llama 3.2 3B", "Mistral 7B (Q4)", "Qwen2.5 7B (Q4)")
        )
        totalRamGB >= 12 -> Triple(
            "~5-6GB", 
            "Your device can run 7B models with Q4 quantization",
            listOf("Llama 3.2 3B", "Phi 3.5 Mini", "Qwen2.5 7B (Q4)")
        )
        totalRamGB >= 8 -> Triple(
            "~3-4GB",
            "Your device works well with 1B-3B parameter models",
            listOf("Llama 3.2 1B", "Llama 3.2 3B (Q4)", "Qwen2.5 1.5B")
        )
        totalRamGB >= 6 -> Triple(
            "~2-2.5GB",
            "Your device is best suited for smaller models",
            listOf("Llama 3.2 1B (Q4)", "Qwen2.5 0.5B", "Qwen2.5 1.5B (Q4)")
        )
        else -> Triple(
            "~1-1.5GB",
            "Your device works best with compact models",
            listOf("Qwen2.5 0.5B", "Llama 3.2 1B (Q4)")
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Memory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Device: ${String.format("%.1f", totalRamGB)}GB RAM",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Available: ${String.format("%.1f", availableRamGB)}GB • Max model: $maxModelSize",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = recommendation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "Recommended for your device:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    recommendedModels.forEach { model ->
                        Text(
                            text = "• $model",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.9f)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Model Size Guide
                    Text(
                        text = "Model Size Guide",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    ModelSizeGuideRow("4GB RAM", "~1-1.5GB models", "0.5B-1B (Q4)")
                    ModelSizeGuideRow("6GB RAM", "~2-2.5GB models", "1B-3B (Q4)")
                    ModelSizeGuideRow("8GB RAM", "~3-4GB models", "3B (Q8), 7B (Q4)")
                    ModelSizeGuideRow("12GB+ RAM", "~5-8GB models", "7B-8B (Q4/Q8)")
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Q4 = smaller/faster, Q8 = larger/better quality. Model file size ≈ RAM needed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelSizeGuideRow(ramSize: String, modelSize: String, recommended: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = ramSize,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = modelSize,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = recommended,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
            modifier = Modifier.weight(1.2f)
        )
    }
}
