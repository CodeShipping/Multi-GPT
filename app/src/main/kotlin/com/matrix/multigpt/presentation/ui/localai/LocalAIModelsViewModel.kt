package com.matrix.multigpt.presentation.ui.localai

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matrix.multigpt.data.source.LocalModelDownloadState
import com.matrix.multigpt.util.ModelDownloadNotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.UUID
import javax.inject.Inject

/**
 * Data class for local AI model info (used within this module).
 */
data class ModelInfo(
    val id: String,
    val name: String,
    val description: String,
    val size: Long,
    val isRecommended: Boolean,
    val downloadUrl: String,
    val performance: String,
    val isImported: Boolean = false,
    val filePath: String? = null
)

/**
 * Progress state for model import.
 */
data class ImportProgress(
    val current: Int,
    val total: Int,
    val fileName: String
)

/**
 * ViewModel for Local AI Models screen.
 * Persists download state across navigation to prevent progress loss.
 */
@HiltViewModel
class LocalAIModelsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadState: LocalModelDownloadState // Singleton!
) : ViewModel() {

    private val _models = MutableStateFlow<List<ModelInfo>>(emptyList())
    val models: StateFlow<List<ModelInfo>> = _models.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Expose download state from singleton
    val downloadingModels: StateFlow<Set<String>> = downloadState.downloadingModels
    val downloadProgressMap: StateFlow<Map<String, Float>> = downloadState.downloadProgressMap
    val downloadedBytesMap: StateFlow<Map<String, Long>> = downloadState.downloadedBytesMap
    val totalBytesMap: StateFlow<Map<String, Long>> = downloadState.totalBytesMap

    private val _downloadedModels = MutableStateFlow<Set<String>>(emptySet())
    val downloadedModels: StateFlow<Set<String>> = _downloadedModels.asStateFlow()

    private val _selectedModelId = MutableStateFlow<String?>(null)
    val selectedModelId: StateFlow<String?> = _selectedModelId.asStateFlow()

    private val _localEnabled = MutableStateFlow(false)
    val localEnabled: StateFlow<Boolean> = _localEnabled.asStateFlow()

    private val _importProgress = MutableStateFlow<ImportProgress?>(null)
    val importProgress: StateFlow<ImportProgress?> = _importProgress.asStateFlow()

    init {
        loadModels()
        observeCompletedDownloads()
    }

    /**
     * Observe completed downloads from the singleton state.
     * This ensures UI updates even when downloads complete while the ViewModel exists.
     */
    private fun observeCompletedDownloads() {
        viewModelScope.launch {
            downloadState.completedDownloads.collect { completedIds ->
                if (completedIds.isNotEmpty()) {
                    // Add completed downloads to our downloaded models set
                    _downloadedModels.value = _downloadedModels.value + completedIds
                    // Acknowledge each completion
                    completedIds.forEach { modelId ->
                        downloadState.acknowledgeCompletion(modelId)
                    }
                }
            }
        }
    }

    fun loadModels() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val loadedModels = loadModelsFromModule()
                
                // Load imported models from preferences
                val importedModels = loadImportedModels()
                
                // Combine models, avoiding duplicates
                val allModels = loadedModels + importedModels.filter { imported -> 
                    loadedModels.none { it.id == imported.id } 
                }
                
                _models.value = allModels
                
                // Check which models are already downloaded (including imported ones)
                val downloadedSet = mutableSetOf<String>()
                allModels.forEach { model ->
                    if (model.isImported || isModelDownloaded(model.id)) {
                        downloadedSet.add(model.id)
                    }
                }
                _downloadedModels.value = downloadedSet
                
                // Check which model is currently selected
                val prefs = context.getSharedPreferences("local_ai_prefs", Context.MODE_PRIVATE)
                _selectedModelId.value = prefs.getString("selected_model_id", null)
                _localEnabled.value = prefs.getBoolean("local_enabled", false)
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Using offline models: ${e.message}"
                _models.value = getDefaultModelInfos() + loadImportedModels()
            }
            _isLoading.value = false
        }
    }

    fun refreshModels() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null
            try {
                forceRefreshFromModule()
                val loadedModels = loadModelsFromModule()
                _models.value = loadedModels
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Refresh failed: ${e.message}"
            }
            _isLoading.value = false
        }
    }

    fun setLocalEnabled(enabled: Boolean) {
        _localEnabled.value = enabled
        val prefs = context.getSharedPreferences("local_ai_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("local_enabled", enabled).apply()
        if (!enabled) {
            prefs.edit().remove("selected_model_id").apply()
            _selectedModelId.value = null
        }
    }

    fun startDownload(model: ModelInfo) {
        // Don't start if already downloading
        if (downloadState.isDownloading(model.id)) return

        // Add to singleton download state (persists across navigation!)
        downloadState.startDownload(model.id, model.size)

        // Create notification channel
        ModelDownloadNotificationHelper.createNotificationChannel(context)
        
        // Show initial notification
        ModelDownloadNotificationHelper.showProgressNotification(
            context, model.id, model.name, 0f, 0L, model.size
        )

        // Launch download in GlobalScope so it survives ViewModel destruction
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val success = downloadModelWithProgress(
                    modelId = model.id,
                    onProgress = { progress, downloaded, total ->
                        // Update singleton state
                        downloadState.updateProgress(model.id, progress, downloaded, total)
                        // Update notification
                        ModelDownloadNotificationHelper.showProgressNotification(
                            context, model.id, model.name, progress, downloaded, total
                        )
                    }
                )
                
                if (success) {
                    // Mark as successfully completed in singleton (triggers observer)
                    downloadState.markDownloadSuccess(model.id)
                    // Show completion notification
                    ModelDownloadNotificationHelper.showCompleteNotification(
                        context, model.id, model.name
                    )
                }
            } catch (e: Exception) {
                // Show failure notification
                ModelDownloadNotificationHelper.showFailedNotification(
                    context, model.id, model.name, e.message ?: "Unknown error"
                )
            } finally {
                // Remove from singleton download state
                downloadState.completeDownload(model.id)
            }
        }
    }

    fun deleteModel(model: ModelInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                deleteModelFromDisk(model.id)
                _downloadedModels.value = _downloadedModels.value - model.id
                // Clear selection if deleting selected model
                if (_selectedModelId.value == model.id) {
                    _selectedModelId.value = null
                    val prefs = context.getSharedPreferences("local_ai_prefs", Context.MODE_PRIVATE)
                    prefs.edit().remove("selected_model_id").apply()
                }
            } catch (e: Exception) {
                _error.value = "Delete failed: ${e.message}"
            }
        }
    }

    fun selectModel(model: ModelInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            val path = getModelPath(model.id)
            if (path != null) {
                _selectedModelId.value = model.id
                
                // Update local_ai_prefs
                val localPrefs = context.getSharedPreferences("local_ai_prefs", Context.MODE_PRIVATE)
                localPrefs.edit()
                    .putString("selected_model_id", model.id)
                    .putString("selected_model_name", model.name)
                    .putBoolean("local_enabled", true)
                    .apply()
                _localEnabled.value = true
                
                // IMPORTANT: Also set app_prefs so ChatViewModel knows to use LOCAL provider
                // This is what ChatViewModel checks to determine active provider/model
                val appPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                appPrefs.edit()
                    .putString("active_provider", "LOCAL")
                    .putString("active_model", model.id)
                    .apply()
            }
        }
    }

    /**
     * Import GGUF models from external storage.
     */
    fun importModels(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            val ggufUris = uris.filter { uri ->
                val path = uri.path ?: uri.toString()
                path.endsWith(".gguf", ignoreCase = true) ||
                context.contentResolver.getType(uri)?.contains("octet-stream") == true
            }
            
            if (ggufUris.isEmpty()) {
                _error.value = "No valid GGUF files found"
                return@launch
            }

            val importedModels = mutableListOf<ModelInfo>()
            val modelsDir = File(context.filesDir, "models").also { it.mkdirs() }

            ggufUris.forEachIndexed { index, uri ->
                try {
                    // Get file name
                    val fileName = getFileNameFromUri(uri)
                    _importProgress.value = ImportProgress(index + 1, ggufUris.size, fileName)

                    // Generate unique ID
                    val modelId = "imported-${UUID.randomUUID().toString().take(8)}"
                    val destFile = File(modelsDir, "$modelId.gguf")

                    // Copy file
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    // Extract model info from filename
                    val fileSize = destFile.length()
                    val (name, description, quantization, params) = extractModelInfoFromFileName(fileName)

                    // Create model info
                    val modelInfo = ModelInfo(
                        id = modelId,
                        name = name,
                        description = description,
                        size = fileSize,
                        isRecommended = false,
                        downloadUrl = "", // No URL for imported models
                        performance = estimatePerformance(fileSize),
                        isImported = true
                    )

                    importedModels.add(modelInfo)
                    _downloadedModels.value = _downloadedModels.value + modelId

                } catch (e: Exception) {
                    // Continue with other files
                }
            }

            _importProgress.value = null

            if (importedModels.isNotEmpty()) {
                // Add imported models to the list
                _models.value = _models.value + importedModels
                
                // Save imported models to preferences
                saveImportedModels(importedModels)
            }
        }
    }

    private fun getFileNameFromUri(uri: Uri): String {
        var fileName = "unknown.gguf"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                fileName = cursor.getString(nameIndex) ?: fileName
            }
        }
        return fileName
    }

    private fun extractModelInfoFromFileName(fileName: String): ModelInfoComponents {
        val baseName = fileName.removeSuffix(".gguf").removeSuffix(".GGUF")
        
        // Try to extract quantization (Q4_K_M, Q8_0, etc.)
        val quantRegex = Regex("[-_](Q\\d+(?:_[A-Z](?:_[A-Z])?)?)", RegexOption.IGNORE_CASE)
        val quantMatch = quantRegex.find(baseName)
        val quantization = quantMatch?.groupValues?.get(1)?.uppercase() ?: "Unknown"
        
        // Try to extract parameter count (0.5B, 1B, 3B, 7B, etc.)
        val paramRegex = Regex("[-_]?(\\d+(?:\\.\\d+)?)[Bb][-_]?")
        val paramMatch = paramRegex.find(baseName)
        val params = paramMatch?.groupValues?.get(1)?.let { "${it}B" } ?: "Unknown"
        
        // Clean up name
        var name = baseName
            .replace(quantRegex, "")
            .replace(paramRegex, " ")
            .replace(Regex("[-_]+"), " ")
            .trim()
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        
        if (name.isBlank()) name = "Imported Model"
        if (quantization != "Unknown") name = "$name ($quantization)"
        
        val description = buildString {
            append("Imported from device. ")
            if (params != "Unknown") append("$params parameters. ")
            if (quantization != "Unknown") append("Quantization: $quantization")
        }.trim()
        
        return ModelInfoComponents(name, description, quantization, params)
    }

    private fun estimatePerformance(fileSize: Long): String {
        val sizeGB = fileSize / (1024.0 * 1024.0 * 1024.0)
        return when {
            sizeGB < 0.5 -> "Very Fast • ~40+ tokens/sec"
            sizeGB < 1.0 -> "Fast • ~25 tokens/sec"
            sizeGB < 2.0 -> "Balanced • ~15 tokens/sec"
            sizeGB < 4.0 -> "Quality • ~10 tokens/sec"
            else -> "Demanding • ~5 tokens/sec"
        }
    }

    private fun saveImportedModels(models: List<ModelInfo>) {
        val prefs = context.getSharedPreferences("local_ai_imported_models", Context.MODE_PRIVATE)
        val existingJson = prefs.getString("imported_models", "[]") ?: "[]"
        
        // Simple JSON serialization
        val newEntries = models.joinToString(",") { model ->
            """{"id":"${model.id}","name":"${model.name}","description":"${model.description}","size":${model.size},"performance":"${model.performance}"}"""
        }
        
        val updatedJson = if (existingJson == "[]") {
            "[$newEntries]"
        } else {
            existingJson.dropLast(1) + ",$newEntries]"
        }
        
        prefs.edit().putString("imported_models", updatedJson).apply()
    }

    /**
     * Load previously imported models from SharedPreferences.
     */
    private fun loadImportedModels(): List<ModelInfo> {
        val prefs = context.getSharedPreferences("local_ai_imported_models", Context.MODE_PRIVATE)
        val json = prefs.getString("imported_models", "[]") ?: "[]"
        
        if (json == "[]") return emptyList()
        
        val models = mutableListOf<ModelInfo>()
        val modelsDir = File(context.filesDir, "models")
        
        try {
            // Simple JSON parsing
            val entriesStr = json.removePrefix("[").removeSuffix("]")
            if (entriesStr.isBlank()) return emptyList()
            
            // Split by },{ pattern to get individual objects
            val entries = entriesStr.split(Regex("\\},\\s*\\{"))
            
            entries.forEachIndexed { index, entry ->
                try {
                    // Clean up entry
                    var cleanEntry = entry
                    if (index == 0) cleanEntry = cleanEntry.removePrefix("{")
                    if (index == entries.size - 1) cleanEntry = cleanEntry.removeSuffix("}")
                    if (!cleanEntry.startsWith("{")) cleanEntry = "{$cleanEntry"
                    if (!cleanEntry.endsWith("}")) cleanEntry = "$cleanEntry}"
                    
                    // Parse fields
                    val id = Regex(""""id"\s*:\s*"([^"]+)"""").find(cleanEntry)?.groupValues?.get(1) ?: return@forEachIndexed
                    val name = Regex(""""name"\s*:\s*"([^"]+)"""").find(cleanEntry)?.groupValues?.get(1) ?: "Unknown"
                    val description = Regex(""""description"\s*:\s*"([^"]+)"""").find(cleanEntry)?.groupValues?.get(1) ?: ""
                    val size = Regex(""""size"\s*:\s*(\d+)""").find(cleanEntry)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                    val performance = Regex(""""performance"\s*:\s*"([^"]+)"""").find(cleanEntry)?.groupValues?.get(1) ?: "Unknown"
                    
                    // Check if model file still exists
                    val modelFile = File(modelsDir, "$id.gguf")
                    if (modelFile.exists()) {
                        models.add(
                            ModelInfo(
                                id = id,
                                name = name,
                                description = description,
                                size = size,
                                isRecommended = false,
                                downloadUrl = "",
                                performance = performance,
                                isImported = true
                            )
                        )
                    }
                } catch (e: Exception) {
                    // Skip malformed entry
                }
            }
        } catch (e: Exception) {
            // Return empty list on parse error
        }
        
        return models
    }

    private data class ModelInfoComponents(
        val name: String,
        val description: String,
        val quantization: String,
        val params: String
    )

    // --- Private helpers using reflection ---

    private fun getProvider(): Any {
        val providerClass = Class.forName("com.matrix.multigpt.localinference.LocalInferenceProvider")
        val companionField = providerClass.getDeclaredField("Companion")
        val companion = companionField.get(null)
        val getInstanceMethod = companion.javaClass.getMethod("getInstance", Context::class.java)
        return getInstanceMethod.invoke(companion, context)!!
    }

    private suspend fun loadModelsFromModule(): List<ModelInfo> {
        return withContext(Dispatchers.IO) {
            try {
                val provider = getProvider()
                
                // Get modelRepository field
                val repoField = provider.javaClass.getDeclaredMethod("getModelRepository")
                val repository = repoField.invoke(provider)
                
                // Call getModels()
                val getModelsMethod = repository.javaClass.getDeclaredMethod("getModels", kotlin.coroutines.Continuation::class.java)
                
                @Suppress("UNCHECKED_CAST")
                val result = invokeSuspend<List<Any>>(getModelsMethod, repository)
                
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

    private suspend fun forceRefreshFromModule() {
        withContext(Dispatchers.IO) {
            try {
                val provider = getProvider()
                val repoField = provider.javaClass.getDeclaredMethod("getModelRepository")
                val repository = repoField.invoke(provider)
                
                val refreshMethod = repository.javaClass.getDeclaredMethod("forceRefresh", kotlin.coroutines.Continuation::class.java)
                invokeSuspend<Unit>(refreshMethod, repository)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private suspend fun downloadModelWithProgress(
        modelId: String,
        onProgress: (Float, Long, Long) -> Unit
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val provider = getProvider()
                
                // Get download manager
                val downloadManagerField = provider.javaClass.getDeclaredMethod("getDownloadManager")
                val downloadManager = downloadManagerField.invoke(provider)
                val getModelPathMethod = downloadManager.javaClass.getMethod("getModelPath", String::class.java)
                
                // Start download
                val downloadMethod = provider.javaClass.getDeclaredMethod(
                    "downloadModel", 
                    String::class.java, 
                    kotlin.coroutines.Continuation::class.java
                )
                
                try {
                    invokeSuspend<Unit>(downloadMethod, provider, modelId)
                } catch (e: Exception) {
                    val path = getModelPathMethod.invoke(downloadManager, modelId) as? String
                    if (path != null) {
                        onProgress(1.0f, 0L, 0L)
                        return@withContext true
                    }
                    throw e
                }
                
                // Poll for completion
                var lastProgress = 0f
                repeat(3600) { iteration ->
                    delay(500)
                    
                    val path = getModelPathMethod.invoke(downloadManager, modelId) as? String
                    if (path != null) {
                        onProgress(1.0f, 0L, 0L)
                        return@withContext true
                    }
                    
                    try {
                        val modelStatesMethod = downloadManager.javaClass.getDeclaredMethod("getModelStates")
                        val statesFlow = modelStatesMethod.invoke(downloadManager)
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
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore
                    }
                    
                    if (iteration % 4 == 0 && lastProgress < 0.95f) {
                        lastProgress = minOf(lastProgress + 0.01f, 0.95f)
                        onProgress(lastProgress, 0L, 0L)
                    }
                }
                
                false
            } catch (e: Exception) {
                throw e
            }
        }
    }

    private fun isModelDownloaded(modelId: String): Boolean {
        return try {
            val provider = getProvider()
            val isDownloadedMethod = provider.javaClass.getMethod("isModelDownloaded", String::class.java)
            isDownloadedMethod.invoke(provider, modelId) as Boolean
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get the local file path for a downloaded model.
     * Public to allow UI to retrieve path when navigating.
     */
    fun getModelPath(modelId: String): String? {
        return try {
            val provider = getProvider()
            val getPathMethod = provider.javaClass.getMethod("getModelPath", String::class.java)
            getPathMethod.invoke(provider, modelId) as? String
        } catch (e: Exception) {
            // For imported models, check the models directory
            val modelsDir = File(context.filesDir, "models")
            val modelFile = File(modelsDir, "$modelId.gguf")
            if (modelFile.exists()) modelFile.absolutePath else null
        }
    }

    private fun deleteModelFromDisk(modelId: String) {
        try {
            val provider = getProvider()
            val deleteMethod = provider.javaClass.getMethod("deleteModel", String::class.java)
            deleteMethod.invoke(provider, modelId)
        } catch (e: Exception) {
            throw e
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
}
