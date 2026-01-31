package com.matrix.multigpt.presentation.ui.localai

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
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
    
    // Load models from localinference module
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                models = loadModelsFromModule(context)
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
                    IconButton(onClick = { 
                        isLoading = true
                        error = null
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                try {
                                    // Force refresh from module
                                    forceRefreshFromModule(context)
                                    models = loadModelsFromModule(context)
                                    error = null
                                } catch (e: Exception) {
                                    error = "Refresh failed: ${e.message}"
                                }
                                isLoading = false
                            }
                        }
                    }) {
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
            // Show error banner if there's an error but models are still displayed
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
                            Text("Loading models from Firebase...")
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
                            ModelCard(
                                model = model,
                                onDownload = { 
                                    scope.launch {
                                        downloadModel(context, model.id)
                                    }
                                },
                                onClick = { /* TODO: Implement selection */ }
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
            // Get the LocalInferenceProvider class
            val providerClass = Class.forName("com.matrix.multigpt.localinference.LocalInferenceProvider")
            val companionField = providerClass.getDeclaredField("Companion")
            val companion = companionField.get(null)
            
            // Get getInstance method
            val getInstanceMethod = companion.javaClass.getMethod("getInstance", Context::class.java)
            val provider = getInstanceMethod.invoke(companion, context)
            
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
            // Module not loaded, use defaults
            getDefaultModelInfos()
        } catch (e: Exception) {
            throw e
        }
    }
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

/**
 * Force refresh models from Firebase.
 */
private suspend fun forceRefreshFromModule(context: Context) {
    withContext(Dispatchers.IO) {
        try {
            val providerClass = Class.forName("com.matrix.multigpt.localinference.LocalInferenceProvider")
            val companionField = providerClass.getDeclaredField("Companion")
            val companion = companionField.get(null)
            val getInstanceMethod = companion.javaClass.getMethod("getInstance", Context::class.java)
            val provider = getInstanceMethod.invoke(companion, context)
            
            val repoField = provider.javaClass.getDeclaredMethod("getModelRepository")
            val repository = repoField.invoke(provider)
            
            // Call forceRefresh() - suspend function
            val refreshMethod = repository.javaClass.getDeclaredMethod("forceRefresh", kotlin.coroutines.Continuation::class.java)
            invokeSuspend<Unit>(refreshMethod, repository)
        } catch (e: Exception) {
            // Ignore refresh errors
        }
    }
}

/**
 * Download a model via the localinference module.
 */
private suspend fun downloadModel(context: Context, modelId: String) {
    withContext(Dispatchers.IO) {
        try {
            val providerClass = Class.forName("com.matrix.multigpt.localinference.LocalInferenceProvider")
            val companionField = providerClass.getDeclaredField("Companion")
            val companion = companionField.get(null)
            val getInstanceMethod = companion.javaClass.getMethod("getInstance", Context::class.java)
            val provider = getInstanceMethod.invoke(companion, context)
            
            // Call downloadModel(modelId) - suspend function
            val downloadMethod = provider.javaClass.getDeclaredMethod("downloadModel", String::class.java, kotlin.coroutines.Continuation::class.java)
            invokeSuspend<Unit>(downloadMethod, provider, modelId)
        } catch (e: Exception) {
            // Log error
        }
    }
}

@Suppress("UNCHECKED_CAST")
private suspend fun <T> invokeSuspend(method: java.lang.reflect.Method, obj: Any, vararg args: Any?): T {
    return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        try {
            val allArgs = args.toMutableList().apply { add(cont) }.toTypedArray()
            val result = method.invoke(obj, *allArgs)
            // If result is not COROUTINE_SUSPENDED, it means immediate return
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
    onDownload: () -> Unit,
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
                FilledTonalButton(onClick = onDownload) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Download")
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
