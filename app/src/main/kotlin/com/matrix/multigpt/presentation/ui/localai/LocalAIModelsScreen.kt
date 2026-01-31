package com.matrix.multigpt.presentation.ui.localai

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Screen that displays available Local AI Models.
 * Connects to the localinference module via reflection to get models from Firebase.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalAIModelsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToChat: (modelId: String, modelPath: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var models by remember { mutableStateOf<List<ModelInfo>>(emptyList()) }
    var downloadingModelId by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadedBytes by remember { mutableStateOf(0L) }
    var totalBytes by remember { mutableStateOf(0L) }
    var downloadedModels by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedModelId by remember { mutableStateOf<String?>(null) }
    
    // Load models from localinference module
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val loadedModels = loadModelsFromModule(context)
                models = loadedModels
                // Check which models are already downloaded
                downloadedModels = loadedModels.filter { 
                    isModelDownloaded(context, it.id) 
                }.map { it.id }.toSet()
                // Check which model is currently selected
                val prefs = context.getSharedPreferences("local_ai_prefs", android.content.Context.MODE_PRIVATE)
                selectedModelId = prefs.getString("selected_model_id", null)
                error = null
            } catch (e: Exception) {
                error = "Using offline models: ${e.message}"
                models = getDefaultModelInfos()
            }
            isLoading = false
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
                        onClick = { 
                            isLoading = true
                            error = null
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    try {
                                        forceRefreshFromModule(context)
                                        models = loadModelsFromModule(context)
                                        error = null
                                    } catch (e: Exception) {
                                        error = "Refresh failed: ${e.message}"
                                    }
                                    isLoading = false
                                }
                            }
                        },
                        enabled = downloadingModelId == null
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
                isLoading -> {
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
                        items(models) { model ->
                            val isDownloaded = downloadedModels.contains(model.id)
                            val isDownloading = downloadingModelId == model.id
                            
                            val isSelected = selectedModelId == model.id
                            
                            ModelCard(
                                model = model,
                                isDownloaded = isDownloaded,
                                isDownloading = isDownloading,
                                isSelected = isSelected,
                                downloadProgress = if (isDownloading) downloadProgress else 0f,
                                downloadedBytes = if (isDownloading) downloadedBytes else 0L,
                                totalBytes = if (isDownloading) totalBytes else 0L,
                                onDownload = { 
                                    scope.launch {
                                        downloadingModelId = model.id
                                        downloadProgress = 0f
                                        
                                        try {
                                            totalBytes = model.size
                                            downloadedBytes = 0L
                                            
                                            val success = downloadModelWithProgress(
                                                context = context, 
                                                modelId = model.id,
                                                onProgress = { progress, downloaded, total ->
                                                    downloadProgress = progress
                                                    downloadedBytes = downloaded
                                                    totalBytes = total
                                                }
                                            )
                                            
                                            if (success) {
                                                downloadedModels = downloadedModels + model.id
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, "Downloaded ${model.name}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                        
                                        downloadingModelId = null
                                    }
                                },
                                onDelete = {
                                    scope.launch {
                                        try {
                                            deleteModel(context, model.id)
                                            downloadedModels = downloadedModels - model.id
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "Deleted ${model.name}", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "Delete failed: ${e.message}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                },
                                onUseModel = {
                                    if (isDownloaded) {
                                        scope.launch {
                                            val path = getModelPath(context, model.id)
                                            if (path != null) {
                                                // Save model selection to settings
                                                saveSelectedLocalModel(context, model.id, model.name)
                                                selectedModelId = model.id
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(
                                                        context, 
                                                        "Selected ${model.name}. Create a new chat to use it.", 
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                                // Navigate back to settings
                                                onNavigateToChat(model.id, path)
                                            }
                                        }
                                    }
                                },
                                onClick = { 
                                    // Card click shows model details (future feature)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Load models from the localinference dynamic feature module via reflection.
 */
private suspend fun loadModelsFromModule(context: Context): List<ModelInfo> {
    return withContext(Dispatchers.IO) {
        try {
            val provider = getProvider(context)
            
            // Get modelRepository field
            val repoField = provider.javaClass.getDeclaredMethod("getModelRepository")
            val repository = repoField.invoke(provider)
            
            // Call getModels() - this is a suspend function
            val getModelsMethod = repository.javaClass.getDeclaredMethod("getModels", kotlin.coroutines.Continuation::class.java)
            
            @Suppress("UNCHECKED_CAST")
            val result = invokeSuspend<List<Any>>(getModelsMethod, repository)
            
            // Map the results to our ModelInfo class
            result.mapNotNull { model ->
                try {
                    val modelClass = model.javaClass
                    ModelInfo(
                        id = modelClass.getMethod("getId").invoke(model) as String,
                        name = modelClass.getMethod("getName").invoke(model) as String,
                        description = modelClass.getMethod("getDescription").invoke(model) as String,
                        size = modelClass.getMethod("getSize").invoke(model) as Long,
                        isRecommended = modelClass.getMethod("isRecommended").invoke(model) as Boolean,
                        downloadUrl = modelClass.getMethod("getDownloadUrl").invoke(model) as String,
                        performance = extractPerformance(model)
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: ClassNotFoundException) {
            getDefaultModelInfos()
        } catch (e: Exception) {
            throw e
        }
    }
}

private fun getProvider(context: Context): Any {
    val providerClass = Class.forName("com.matrix.multigpt.localinference.LocalInferenceProvider")
    val companionField = providerClass.getDeclaredField("Companion")
    val companion = companionField.get(null)
    val getInstanceMethod = companion.javaClass.getMethod("getInstance", Context::class.java)
    return getInstanceMethod.invoke(companion, context)!!
}

private fun extractPerformance(model: Any): String {
    return try {
        val performanceObj = model.javaClass.getMethod("getPerformance").invoke(model)
        val tokensPerSec = performanceObj.javaClass.getMethod("getTokensPerSecond").invoke(performanceObj) as Float
        val rating = performanceObj.javaClass.getMethod("getRating").invoke(performanceObj).toString()
        "$rating • ~${tokensPerSec.toInt()} tokens/sec"
    } catch (e: Exception) {
        "Unknown"
    }
}

private suspend fun forceRefreshFromModule(context: Context) {
    withContext(Dispatchers.IO) {
        try {
            val provider = getProvider(context)
            val repoField = provider.javaClass.getDeclaredMethod("getModelRepository")
            val repository = repoField.invoke(provider)
            
            val refreshMethod = repository.javaClass.getDeclaredMethod("forceRefresh", kotlin.coroutines.Continuation::class.java)
            invokeSuspend<Unit>(refreshMethod, repository)
        } catch (e: Exception) {
            // Ignore
        }
    }
}

/**
 * Download a model with progress tracking via the localinference module.
 */
private suspend fun downloadModelWithProgress(
    context: Context, 
    modelId: String,
    onProgress: (Float, Long, Long) -> Unit
): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val provider = getProvider(context)
            
            // Get download manager first to monitor progress
            val downloadManagerField = provider.javaClass.getDeclaredMethod("getDownloadManager")
            val downloadManager = downloadManagerField.invoke(provider)
            val getModelPathMethod = downloadManager.javaClass.getMethod("getModelPath", String::class.java)
            
            // Call downloadModel(modelId) - this launches the download asynchronously
            val downloadMethod = provider.javaClass.getDeclaredMethod(
                "downloadModel", 
                String::class.java, 
                kotlin.coroutines.Continuation::class.java
            )
            
            // Start download (launches in background)
            try {
                invokeSuspend<Unit>(downloadMethod, provider, modelId)
            } catch (e: Exception) {
                // Download might throw if already completed - check path
                val path = getModelPathMethod.invoke(downloadManager, modelId) as? String
                if (path != null) {
                    onProgress(1.0f, 0L, 0L)
                    return@withContext true
                }
                throw e
            }
            
            // Poll for completion with faster interval
            var lastProgress = 0f
            repeat(3600) { iteration -> // Max 30 minutes (poll every 500ms)
                delay(500)
                
                // Check if download completed
                val path = getModelPathMethod.invoke(downloadManager, modelId) as? String
                if (path != null) {
                    onProgress(1.0f, 0L, 0L)
                    return@withContext true
                }
                
                // Try to get actual progress from modelStates via reflection
                var progressRetrieved = false
                try {
                    val modelStatesMethod = downloadManager.javaClass.getDeclaredMethod("getModelStates")
                    val statesFlow = modelStatesMethod.invoke(downloadManager)
                    
                    // StateFlow.value
                    val valueMethod = statesFlow.javaClass.getMethod("getValue")
                    @Suppress("UNCHECKED_CAST")
                    val states = valueMethod.invoke(statesFlow) as? Map<String, Any>
                    
                    val state = states?.get(modelId)
                    if (state != null) {
                        val stateClass = state.javaClass
                        val progress = stateClass.getMethod("getDownloadProgress").invoke(state) as Float
                        val downloaded = stateClass.getMethod("getDownloadedBytes").invoke(state) as Long
                        val total = stateClass.getMethod("getTotalBytes").invoke(state) as Long
                        
                        if (progress > lastProgress || downloaded > 0) {
                            lastProgress = progress
                            onProgress(progress, downloaded, total)
                            progressRetrieved = true
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("LocalAIModelsScreen", "Progress retrieval failed: ${e.message}")
                }
                
                // If we couldn't get progress, show indeterminate-style progress
                if (!progressRetrieved && iteration % 4 == 0) {
                    // Slowly increment to show something is happening
                    lastProgress = minOf(lastProgress + 0.01f, 0.95f)
                    onProgress(lastProgress, 0L, 0L)
                }
            }
            
            false
        } catch (e: Exception) {
            android.util.Log.e("LocalAIModelsScreen", "Download failed", e)
            throw e
        }
    }
}

private suspend fun isModelDownloaded(context: Context, modelId: String): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val provider = getProvider(context)
            val isDownloadedMethod = provider.javaClass.getMethod("isModelDownloaded", String::class.java)
            isDownloadedMethod.invoke(provider, modelId) as Boolean
        } catch (e: Exception) {
            false
        }
    }
}

private suspend fun getModelPath(context: Context, modelId: String): String? {
    return withContext(Dispatchers.IO) {
        try {
            val provider = getProvider(context)
            val getPathMethod = provider.javaClass.getMethod("getModelPath", String::class.java)
            getPathMethod.invoke(provider, modelId) as? String
        } catch (e: Exception) {
            null
        }
    }
}

private suspend fun deleteModel(context: Context, modelId: String) {
    withContext(Dispatchers.IO) {
        try {
            val provider = getProvider(context)
            val deleteMethod = provider.javaClass.getMethod("deleteModel", String::class.java)
            deleteMethod.invoke(provider, modelId)
        } catch (e: Exception) {
            throw e
        }
    }
}

/**
 * Save the selected local model and enable LOCAL platform.
 */
private suspend fun saveSelectedLocalModel(context: Context, modelId: String, modelName: String) {
    withContext(Dispatchers.IO) {
        try {
            // Save to SharedPreferences for local model selection
            val prefs = context.getSharedPreferences("local_ai_prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit()
                .putString("selected_model_id", modelId)
                .putString("selected_model_name", modelName)
                .putBoolean("local_enabled", true)
                .apply()
            
            // Also update DataStore to enable LOCAL platform
            // This uses the same DataStore file as the app
            val dataStorePrefs = androidx.datastore.preferences.preferencesDataStore(
                name = "settings"
            )
            
            // Access via reflection to get the SettingRepository
            try {
                // Try to update DataStore directly using preferences file
                val prefsFile = java.io.File(context.filesDir, "datastore/settings.preferences_pb")
                if (!prefsFile.parentFile?.exists()!!) {
                    prefsFile.parentFile?.mkdirs()
                }
                
                // Update the main DataStore preferences via proto
                // For simplicity, we'll read from SharedPrefs in HomeViewModel
                
            } catch (e: Exception) {
                android.util.Log.w("LocalAIModelsScreen", "Could not update DataStore directly", e)
            }
            
        } catch (e: Exception) {
            android.util.Log.w("LocalAIModelsScreen", "Failed to save model selection", e)
        }
    }
}

@Suppress("UNCHECKED_CAST")
private suspend fun <T> invokeSuspend(method: java.lang.reflect.Method, obj: Any, vararg args: Any?): T {
    return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        try {
            val allArgs = args.toMutableList().apply { add(cont) }.toTypedArray()
            val result = method.invoke(obj, *allArgs)
            if (result != null && result.javaClass.name != "kotlin.coroutines.intrinsics.CoroutineSingletons") {
                cont.resumeWith(Result.success(result as T))
            }
        } catch (e: Exception) {
            cont.resumeWith(Result.failure(e))
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
                    isDownloaded -> {
                        Row {
                            if (isSelected) {
                                FilledTonalButton(
                                    onClick = { /* Already selected */ },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Selected")
                                }
                            } else {
                                FilledTonalButton(
                                    onClick = onUseModel,
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                ) {
                                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Select")
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = onDelete) {
                                Icon(
                                    Icons.Filled.Delete, 
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    isDownloading -> {
                        FilledTonalButton(
                            onClick = { /* Could add pause */ },
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

private fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}

private fun getDefaultModelInfos(): List<ModelInfo> {
    return listOf(
        ModelInfo(
            id = "llama-3.2-1b-q4",
            name = "Llama 3.2 1B (Q4)",
            description = "Compact and efficient model, perfect for mobile devices.",
            size = 750_000_000L,
            isRecommended = true,
            downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            performance = "Fast • ~25 tokens/sec"
        ),
        ModelInfo(
            id = "qwen-2.5-0.5b-q8",
            name = "Qwen 2.5 0.5B (Q8)",
            description = "Ultra-lightweight model for basic tasks.",
            size = 500_000_000L,
            isRecommended = true,
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q8_0.gguf",
            performance = "Very Fast • ~40 tokens/sec"
        ),
        ModelInfo(
            id = "llama-3.2-3b-q4",
            name = "Llama 3.2 3B (Q4)",
            description = "Balanced model with good reasoning capabilities.",
            size = 2_000_000_000L,
            isRecommended = false,
            downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            performance = "Balanced • ~15 tokens/sec"
        ),
        ModelInfo(
            id = "phi-3.5-mini-q4",
            name = "Phi 3.5 Mini (Q4)",
            description = "Microsoft's compact powerhouse.",
            size = 2_300_000_000L,
            isRecommended = false,
            downloadUrl = "https://huggingface.co/bartowski/Phi-3.5-mini-instruct-GGUF/resolve/main/Phi-3.5-mini-instruct-Q4_K_M.gguf",
            performance = "Quality • ~12 tokens/sec"
        )
    )
}

private data class ModelInfo(
    val id: String,
    val name: String,
    val description: String,
    val size: Long,
    val isRecommended: Boolean,
    val downloadUrl: String,
    val performance: String
)
