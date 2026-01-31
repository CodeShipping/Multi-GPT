package com.matrix.multigpt.presentation.ui.localai

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matrix.multigpt.data.source.LocalModelDownloadState
import com.matrix.multigpt.util.ModelDownloadNotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
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
    val performance: String
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

    init {
        loadModels()
    }

    fun loadModels() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val loadedModels = loadModelsFromModule()
                _models.value = loadedModels
                
                // Check which models are already downloaded
                _downloadedModels.value = loadedModels.filter { 
                    isModelDownloaded(it.id) 
                }.map { it.id }.toSet()
                
                // Check which model is currently selected
                val prefs = context.getSharedPreferences("local_ai_prefs", Context.MODE_PRIVATE)
                _selectedModelId.value = prefs.getString("selected_model_id", null)
                _localEnabled.value = prefs.getBoolean("local_enabled", false)
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Using offline models: ${e.message}"
                _models.value = getDefaultModelInfos()
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
                    _downloadedModels.value = _downloadedModels.value + model.id
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
                val prefs = context.getSharedPreferences("local_ai_prefs", Context.MODE_PRIVATE)
                prefs.edit()
                    .putString("selected_model_id", model.id)
                    .putString("selected_model_name", model.name)
                    .putBoolean("local_enabled", true)
                    .apply()
                _localEnabled.value = true
            }
        }
    }

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

    private fun getModelPath(modelId: String): String? {
        return try {
            val provider = getProvider()
            val getPathMethod = provider.javaClass.getMethod("getModelPath", String::class.java)
            getPathMethod.invoke(provider, modelId) as? String
        } catch (e: Exception) {
            null
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
