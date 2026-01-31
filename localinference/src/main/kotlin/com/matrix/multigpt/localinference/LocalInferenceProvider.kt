package com.matrix.multigpt.localinference

import android.content.Context
import androidx.compose.runtime.Composable
import com.matrix.multigpt.localinference.data.model.LocalModel
import com.matrix.multigpt.localinference.data.model.LocalModelState
import com.matrix.multigpt.localinference.data.network.ModelCatalogServiceImpl
import com.matrix.multigpt.localinference.data.repository.LocalModelRepository
import com.matrix.multigpt.localinference.data.repository.LocalModelRepositoryImpl
import com.matrix.multigpt.localinference.data.source.LocalModelCacheDataSource
import com.matrix.multigpt.localinference.data.source.ModelDownloadManager
import com.matrix.multigpt.localinference.presentation.ui.compose.ModelListScreen
import com.matrix.multigpt.localinference.service.ChatMessage
import com.matrix.multigpt.localinference.service.ChatRole
import com.matrix.multigpt.localinference.service.LocalInferenceService
import com.matrix.multigpt.localinference.service.LocalInferenceServiceImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/**
 * Main entry point for the Local Inference dynamic feature module.
 * 
 * This class provides access to all the module's capabilities:
 * - Model management (download, delete, list)
 * - Inference service (load model, generate responses)
 * - UI components (ModelListScreen composable)
 * 
 * Usage:
 * ```kotlin
 * val provider = LocalInferenceProvider.getInstance(context)
 * 
 * // Access model repository
 * val models = provider.modelRepository.getModels()
 * 
 * // Access inference service
 * provider.inferenceService.loadModel(modelPath)
 * provider.inferenceService.generateChatCompletion(messages).collect { token ->
 *     // Handle streaming tokens
 * }
 * ```
 */
class LocalInferenceProvider private constructor(context: Context) {
    
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.IO)
    
    // Lazy initialization of components
    private val modelCatalogService: ModelCatalogServiceImpl by lazy {
        ModelCatalogServiceImpl()
    }
    
    private val localModelCache: LocalModelCacheDataSource by lazy {
        LocalModelCacheDataSource(appContext)
    }
    
    /**
     * Model download manager - handles downloading model files with progress tracking.
     */
    val downloadManager: ModelDownloadManager by lazy {
        ModelDownloadManager(appContext)
    }
    
    /**
     * Model repository - provides access to available models and their states.
     */
    val modelRepository: LocalModelRepository by lazy {
        LocalModelRepositoryImpl(
            catalogService = modelCatalogService,
            downloadManager = downloadManager,
            cacheDataSource = localModelCache
        )
    }
    
    /**
     * Inference service - handles loading models and generating responses.
     */
    val inferenceService: LocalInferenceService by lazy {
        LocalInferenceServiceImpl(appContext)
    }
    
    /**
     * Get the list of available models.
     */
    suspend fun getAvailableModels(): List<LocalModel> {
        return modelRepository.getModels()
    }
    
    /**
     * Get download states for all models.
     */
    fun getModelStates(): StateFlow<Map<String, LocalModelState>> {
        return downloadManager.modelStates
    }
    
    /**
     * Download a model.
     */
    suspend fun downloadModel(modelId: String) {
        modelRepository.downloadModel(modelId)
    }
    
    /**
     * Delete a downloaded model.
     */
    fun deleteModel(modelId: String) {
        scope.launch {
            modelRepository.deleteModel(modelId)
        }
    }
    
    /**
     * Get the path to a downloaded model.
     */
    fun getModelPath(modelId: String): String? {
        return downloadManager.getModelPath(modelId)
    }
    
    /**
     * Check if a model is downloaded.
     */
    fun isModelDownloaded(modelId: String): Boolean {
        return downloadManager.isModelDownloaded(modelId)
    }
    
    /**
     * Cleanup resources when done.
     */
    fun cleanup() {
        downloadManager.cleanup()
        inferenceService.unloadModel()
    }
    
    /**
     * Generate a chat response with automatic model loading.
     * This is the main entry point for chat completion that handles all complexity internally.
     * 
     * @param modelId The ID of the model to use
     * @param messages List of (role, content) pairs. role can be "user" or "assistant"
     * @param temperature Sampling temperature (default 0.7)
     * @param maxTokens Maximum tokens to generate (default 512)
     * @return Flow of generated text chunks (streaming)
     */
    fun generateChatResponse(
        modelId: String,
        messages: List<Pair<String, String>>,
        temperature: Float = 0.7f,
        maxTokens: Int = 512
    ): Flow<String> = flow {
        // Get model path
        val modelPath = getModelPath(modelId)
            ?: throw IllegalStateException("Model $modelId not found. Please download it first.")
        
        // Load model if not already loaded or if different model
        val loadedModelId = inferenceService.getLoadedModelId()
        if (loadedModelId != modelId) {
            val result = inferenceService.loadModel(modelPath)
            if (result.isFailure) {
                throw result.exceptionOrNull() ?: Exception("Failed to load model")
            }
        }
        
        // Convert messages to ChatMessage format
        val chatMessages = messages.map { (role, content) ->
            val chatRole = when (role.lowercase()) {
                "user" -> ChatRole.USER
                "assistant" -> ChatRole.ASSISTANT
                "system" -> ChatRole.SYSTEM
                else -> ChatRole.USER
            }
            ChatMessage(chatRole, content)
        }
        
        // Generate response
        inferenceService.generateChatCompletion(chatMessages, temperature, maxTokens).collect { token ->
            emit(token)
        }
    }
    
    /**
     * Get the currently loaded model ID.
     */
    fun getLoadedModelId(): String? {
        return inferenceService.getLoadedModelId()
    }
    
    companion object {
        @Volatile
        private var INSTANCE: LocalInferenceProvider? = null
        
        /**
         * Get or create the LocalInferenceProvider instance.
         */
        fun getInstance(context: Context): LocalInferenceProvider {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LocalInferenceProvider(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
        
        /**
         * Check if the module is available (for use before dynamic feature is installed).
         */
        fun isAvailable(): Boolean {
            return try {
                Class.forName("com.matrix.multigpt.localinference.LocalInferenceProvider")
                true
            } catch (e: ClassNotFoundException) {
                false
            }
        }
        
        /**
         * Get the ModelListScreen composable function.
         * Returns a composable that can be invoked with navigation callbacks.
         */
        fun getModelListScreen(): (@Composable (
            onNavigateBack: () -> Unit,
            onNavigateToChat: (modelId: String, modelPath: String) -> Unit
        ) -> Unit)? {
            return { onNavigateBack, onNavigateToChat ->
                ModelListScreen(
                    onNavigateBack = onNavigateBack,
                    onNavigateToChat = onNavigateToChat
                )
            }
        }
    }
}
