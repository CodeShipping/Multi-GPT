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
import com.matrix.multigpt.localinference.service.ConversationSummarizer
import com.matrix.multigpt.localinference.service.LocalInferenceService
import com.matrix.multigpt.localinference.service.LocalInferenceServiceImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

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
    
    // Dedicated single-threaded dispatcher for inference (better thread affinity for native code)
    private val inferenceDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "local-inference").apply { 
            priority = Thread.MAX_PRIORITY - 1 // High priority but not max
        }
    }.asCoroutineDispatcher()
    
    // Background scope for non-inference tasks (downloads, cleanup)
    private val backgroundScope = CoroutineScope(
        Dispatchers.IO + SupervisorJob() + CoroutineName("LocalProvider-Background")
    )
    
    // Inference scope with dedicated thread
    private val inferenceScope = CoroutineScope(
        inferenceDispatcher + SupervisorJob() + CoroutineName("LocalProvider-Inference")
    )
    
    // Backward compatibility alias
    private val scope get() = backgroundScope
    
    // Track if previous generation was truncated
    // Only show warning when truncation FIRST happens (transition from not-truncated to truncated)
    private var previousGenerationWasTruncated = false
    
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
            context = appContext,
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
     * Conversation summarizer - generates background summaries for context management.
     */
    val summarizer: ConversationSummarizer by lazy {
        ConversationSummarizer(appContext)
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
     * Call this when the feature is disabled or app is closing.
     */
    fun cleanup() {
        android.util.Log.d("LocalProvider", "Cleaning up resources...")
        
        // Cancel any ongoing generation first
        cancelGeneration()
        
        // Cleanup summarizer
        summarizer.cancel()
        
        // Cleanup download manager
        downloadManager.cleanup()
        
        // Unload model
        inferenceService.unloadModel()
        
        // Cancel scopes
        backgroundScope.cancel()
        inferenceScope.cancel()
        
        // Close dedicated dispatcher
        try {
            @Suppress("DEPRECATION")
            (inferenceDispatcher as? java.io.Closeable)?.close()
        } catch (e: Exception) {
            android.util.Log.w("LocalProvider", "Error closing dispatcher: ${e.message}")
        }
        
        android.util.Log.d("LocalProvider", "Cleanup complete")
    }
    
    /**
     * Cancel any ongoing generation and wait for it to complete.
     */
    fun cancelGeneration() {
        inferenceService.cancelGeneration()
        // Also cancel on the service level to properly stop native generation
        (inferenceService as? LocalInferenceServiceImpl)?.let { service ->
            try {
                // Access the model via reflection and cancel it
                val modelField = service.javaClass.getDeclaredField("llamaModel")
                modelField.isAccessible = true
                val model = modelField.get(service) as? org.codeshipping.llamakotlin.LlamaModel
                model?.cancelGeneration()
                
                // Wait for generation to actually stop (up to 2 seconds)
                var attempts = 0
                while (model?.isGenerating == true && attempts < 20) {
                    Thread.sleep(100)
                    attempts++
                }
            } catch (e: Exception) {
                // Ignore reflection errors
            }
        }
    }
    
    /**
     * Check if generation is currently in progress.
     */
    fun isGenerating(): Boolean {
        return inferenceService.isGenerating()
    }
    
    /**
     * Estimate token count from text.
     * Rule of thumb: 1 token ≈ 4 characters for English text.
     */
    private fun estimateTokens(text: String): Int {
        return (text.length / 4).coerceAtLeast(1)
    }
    
    /**
     * Check if device has enough memory for generation.
     * Returns null if OK, or an error message if memory is too low.
     */
    private fun checkMemoryBeforeGeneration(): String? {
        try {
            val activityManager = appContext.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)
            
            val availableMB = memInfo.availMem / (1024 * 1024)
            val isLowMemory = memInfo.lowMemory
            
            android.util.Log.d("LocalProvider", "Pre-generation memory check: ${availableMB}MB available, lowMemory=$isLowMemory")
            
            // Critical thresholds - if below these, don't even attempt generation
            return when {
                isLowMemory -> {
                    "Device is in low memory state. Please close other apps and try again."
                }
                availableMB < 300 -> {
                    "Not enough memory available (${availableMB}MB). Need at least 300MB free. Please close other apps."
                }
                availableMB < 500 -> {
                    // Borderline - warn but allow
                    android.util.Log.w("LocalProvider", "Memory borderline: ${availableMB}MB. Generation may fail.")
                    null // Allow attempt but may fail
                }
                else -> null // OK
            }
        } catch (e: Exception) {
            android.util.Log.w("LocalProvider", "Could not check memory: ${e.message}")
            return null // Allow attempt if we can't check
        }
    }
    
    /**
     * Smart context management: truncates or summarizes messages to fit context window.
     * 
     * @param messages Original messages
     * @param maxContextTokens Maximum tokens for the prompt (reserves space for response)
     * @return Processed messages that fit within context
     */
    private suspend fun manageContext(
        messages: List<Pair<String, String>>,
        maxContextTokens: Int = 1500 // Reserve ~500 tokens for response
    ): List<Pair<String, String>> {
        // Calculate total estimated tokens
        val totalTokens = messages.sumOf { estimateTokens(it.second) }
        
        android.util.Log.d("LocalProvider", "Context management: ${messages.size} messages, ~$totalTokens tokens (limit: $maxContextTokens)")
        
        // If within limit, return as-is
        if (totalTokens <= maxContextTokens) {
            return messages
        }
        
        android.util.Log.d("LocalProvider", "Context exceeds limit, applying smart truncation...")
        
        // Strategy 1: Always keep the last user message (current question)
        val lastUserMessage = messages.lastOrNull { it.first.lowercase() == "user" }
        val lastUserTokens = lastUserMessage?.let { estimateTokens(it.second) } ?: 0
        
        // Calculate remaining budget
        val remainingBudget = maxContextTokens - lastUserTokens - 100 // 100 token buffer
        
        if (remainingBudget <= 0 || maxContextTokens < 30) {
            // Even the current message is too long - truncate it to fit available context
            android.util.Log.w("LocalProvider", "Current message too long, truncating to fit context...")
            // Ensure we have at least some characters (min 50 chars for a meaningful prompt)
            val maxChars = maxOf(50, maxContextTokens * 4 - 50)
            val content = lastUserMessage?.second?.take(maxChars) ?: ""
            return listOf("user" to content)
        }
        
        // Strategy 2: Keep recent context, summarize or drop old messages
        val result = mutableListOf<Pair<String, String>>()
        var usedTokens = 0
        
        // Process messages from most recent to oldest (excluding last user message)
        val historyMessages = if (lastUserMessage != null) {
            messages.dropLast(1).reversed()
        } else {
            messages.reversed()
        }
        
        // Keep messages that fit
        for (msg in historyMessages) {
            val msgTokens = estimateTokens(msg.second)
            
            if (usedTokens + msgTokens <= remainingBudget) {
                result.add(0, msg)
                usedTokens += msgTokens
            } else if (usedTokens == 0) {
                // First message doesn't fit - truncate it
                val maxChars = remainingBudget * 4
                val truncatedContent = msg.second.take(maxChars) + "... [truncated]"
                result.add(0, msg.first to truncatedContent)
                usedTokens += estimateTokens(truncatedContent)
                break
            } else {
                // No more room - create a summary placeholder for dropped messages
                val droppedCount = historyMessages.size - result.size
                if (droppedCount > 0) {
                    android.util.Log.d("LocalProvider", "Dropped $droppedCount old messages")
                }
                break
            }
        }
        
        // Add back the last user message
        if (lastUserMessage != null) {
            result.add(lastUserMessage)
        }
        
        android.util.Log.d("LocalProvider", "Context reduced to ${result.size} messages, ~${usedTokens + lastUserTokens} tokens")
        
        return result
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
        android.util.Log.d("LocalProvider", "generateChatResponse called for model: $modelId")
        
        // CRITICAL: Pre-flight memory check to prevent native crash
        val memoryError = checkMemoryBeforeGeneration()
        if (memoryError != null) {
            android.util.Log.e("LocalProvider", "Memory check failed: $memoryError")
            throw IllegalStateException(memoryError)
        }
        
        // Get model path
        val modelPath = getModelPath(modelId)
            ?: throw IllegalStateException("Model $modelId not found. Please download it first.")
        
        android.util.Log.d("LocalProvider", "Model path: $modelPath")
        
        // Load model if not already loaded or if different model
        var loadedModelId = inferenceService.getLoadedModelId()
        android.util.Log.d("LocalProvider", "Currently loaded model: $loadedModelId")
        
        // Always try to ensure model is loaded - it may have been freed if app was backgrounded
        suspend fun ensureModelLoaded(): Boolean {
            loadedModelId = inferenceService.getLoadedModelId()
            if (loadedModelId != modelId || !inferenceService.isModelLoaded()) {
                android.util.Log.d("LocalProvider", "Loading/reloading model...")
                val result = inferenceService.loadModel(modelPath)
                if (result.isFailure) {
                    val error = result.exceptionOrNull() ?: Exception("Failed to load model")
                    android.util.Log.e("LocalProvider", "Model load failed", error)
                    throw error
                }
                android.util.Log.d("LocalProvider", "Model loaded successfully")
                return true
            }
            return false
        }
        
        ensureModelLoaded()
        
        // Get context size from loaded model info, fallback to conservative estimate
        val modelInfo = inferenceService.getModelInfo()
        val contextSize = modelInfo?.contextSize ?: 2048
        
        // Calculate available tokens for prompt, ensuring minimum viable size
        // Reserve: system prompt (~30), response (~min(maxTokens, contextSize/3)), overhead (20)
        val responseReserve = minOf(maxTokens, contextSize / 3)
        val maxPromptTokens = maxOf(50, contextSize - 30 - responseReserve - 20)  // Minimum 50 tokens for prompt
        
        android.util.Log.d("LocalProvider", "Context size: $contextSize, response reserve: $responseReserve, max prompt tokens: $maxPromptTokens")
        
        // If context is too small for meaningful conversation, throw error
        if (contextSize < 128) {
            throw IllegalStateException(
                "Context size too small ($contextSize tokens). Please close other apps to free memory, or try a smaller model."
            )
        }
        
        // Pass all messages through - LocalInferenceServiceImpl handles truncation at prompt level
        // This is more accurate since it truncates AFTER template formatting
        val managedMessages = messages
        
        android.util.Log.d("LocalProvider", "Passing ${managedMessages.size} messages to inference service")
        
        // Convert messages to ChatMessage format
        val chatMessages = managedMessages.map { (role, content) ->
            val chatRole = when (role.lowercase()) {
                "user" -> ChatRole.USER
                "assistant" -> ChatRole.ASSISTANT
                "system" -> ChatRole.SYSTEM
                else -> ChatRole.USER
            }
            ChatMessage(chatRole, content)
        }
        
        android.util.Log.d("LocalProvider", "Calling generateChatCompletion with ${chatMessages.size} messages")
        
        // Try synchronous generation first (more reliable), then fall back to streaming
        var hasEmittedToken = false
        
        // First, try synchronous generation (more reliable on some models)
        android.util.Log.d("LocalProvider", "Trying synchronous generation first...")
        var retried = false
        try {
            val syncResult = kotlinx.coroutines.withTimeoutOrNull(90_000L) {
                inferenceService.generateChatCompletionSync(chatMessages, temperature, maxTokens)
            }
            
            if (syncResult?.isSuccess == true) {
                val response = syncResult.getOrNull()
                if (!response.isNullOrBlank()) {
                    android.util.Log.d("LocalProvider", "Sync generation succeeded: ${response.take(50)}...")
                    
                    // Check if truncation happened - only warn when transitioning TO truncated state
                    val serviceImpl = inferenceService as? LocalInferenceServiceImpl
                    val currentlyTruncated = serviceImpl?.lastGenerationWasTruncated == true
                    
                    // Only emit warning when: current is truncated AND previous was NOT truncated
                    if (currentlyTruncated && !previousGenerationWasTruncated) {
                        android.util.Log.w("LocalProvider", "Context truncation started - emitting warning")
                        emit("⚠️ Context limit reached. Keeping only recent messages.\n\n")
                    }
                    
                    // Update state for next time
                    previousGenerationWasTruncated = currentlyTruncated
                    // Emit character by character to simulate streaming
                    response.chunked(1).forEach { char ->
                        emit(char)
                        kotlinx.coroutines.delay(1) // Small delay for UI responsiveness
                    }
                    hasEmittedToken = true
                }
            } else if (syncResult?.isFailure == true) {
                val ex = syncResult.exceptionOrNull()
                android.util.Log.e("LocalProvider", "Sync generation failed", ex)
                // Check if model was unloaded and retry
                if (ex?.message?.contains("unloaded", ignoreCase = true) == true || 
                    ex?.message?.contains("not loaded", ignoreCase = true) == true) {
                    android.util.Log.w("LocalProvider", "Model was unloaded, reloading and retrying...")
                    ensureModelLoaded()
                    retried = true
                    val retryResult = kotlinx.coroutines.withTimeoutOrNull(90_000L) {
                        inferenceService.generateChatCompletionSync(chatMessages, temperature, maxTokens)
                    }
                    if (retryResult?.isSuccess == true) {
                        val response = retryResult.getOrNull()
                        if (!response.isNullOrBlank()) {
                            android.util.Log.d("LocalProvider", "Retry sync generation succeeded")
                            
                            // Check if truncation happened
                            val serviceImpl = inferenceService as? LocalInferenceServiceImpl
                            if (serviceImpl?.lastGenerationWasTruncated == true) {
                                emit("⚠️ Context limit reached. Keeping only recent messages.\n\n")
                            }
                            response.chunked(1).forEach { char ->
                                emit(char)
                                kotlinx.coroutines.delay(1)
                            }
                            hasEmittedToken = true
                        }
                    }
                }
            } else {
                android.util.Log.w("LocalProvider", "Sync generation timed out")
            }
        } catch (e: IllegalStateException) {
            android.util.Log.e("LocalProvider", "Sync generation state exception", e)
            // Try to reload model and retry once
            if (!retried && (e.message?.contains("unloaded", ignoreCase = true) == true ||
                            e.message?.contains("not loaded", ignoreCase = true) == true)) {
                android.util.Log.w("LocalProvider", "Model was unloaded, reloading...")
                ensureModelLoaded()
            }
        } catch (e: Exception) {
            android.util.Log.e("LocalProvider", "Sync generation exception", e)
        }
        
        // If sync didn't work, try streaming
        if (!hasEmittedToken) {
            android.util.Log.d("LocalProvider", "Trying streaming generation...")
            var streamWarningEmitted = false
            try {
                kotlinx.coroutines.withTimeoutOrNull(60_000L) { // 60 second timeout for streaming
                    inferenceService.generateChatCompletion(chatMessages, temperature, maxTokens).collect { token ->
                        // Check for truncation warning on first token
                        if (!streamWarningEmitted) {
                            val serviceImpl = inferenceService as? LocalInferenceServiceImpl
                            if (serviceImpl?.lastGenerationWasTruncated == true) {
                                emit("⚠️ Context limit reached. Keeping only recent messages.\n\n")
                            }
                            streamWarningEmitted = true
                        }
                        hasEmittedToken = true
                        emit(token)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("LocalProvider", "Streaming generation error", e)
            }
        }
        
        if (!hasEmittedToken) {
            android.util.Log.e("LocalProvider", "No tokens generated by any method")
            throw IllegalStateException("Model failed to generate a response. Please try a different model or shorter input.")
        }
        
        android.util.Log.d("LocalProvider", "Generation complete")
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
