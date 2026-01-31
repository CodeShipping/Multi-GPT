package com.matrix.multigpt.localinference.service

import kotlinx.coroutines.flow.Flow

/**
 * Service interface for local AI inference.
 * This is the main API that the main app uses to communicate with local models.
 */
interface LocalInferenceService {
    
    /**
     * Check if a model is loaded and ready for inference.
     */
    fun isModelLoaded(): Boolean
    
    /**
     * Load a model from the given path.
     * @param modelPath Path to the GGUF model file
     * @param contextSize Context size for the model (default 2048)
     * @param batchSize Batch size for processing (0 = auto)
     * @param topK Top K sampling parameter
     * @param topP Top P (nucleus) sampling parameter
     * @return true if loaded successfully
     */
    suspend fun loadModel(
        modelPath: String,
        contextSize: Int = 2048,
        batchSize: Int = 0, // 0 = auto
        topK: Int = 40,
        topP: Float = 0.9f
    ): Result<Boolean>
    
    /**
     * Unload the current model from memory.
     */
    fun unloadModel()
    
    /**
     * Generate a chat completion response.
     * @param messages List of chat messages (role, content pairs)
     * @param temperature Sampling temperature (0.0-2.0)
     * @param maxTokens Maximum tokens to generate
     * @return Flow of generated text chunks (for streaming)
     */
    fun generateChatCompletion(
        messages: List<ChatMessage>,
        temperature: Float = 0.7f,
        maxTokens: Int = 512
    ): Flow<String>
    
    /**
     * Generate a completion (non-streaming).
     * @param messages List of chat messages
     * @param temperature Sampling temperature
     * @param maxTokens Maximum tokens to generate
     * @return The complete generated response
     */
    suspend fun generateChatCompletionSync(
        messages: List<ChatMessage>,
        temperature: Float = 0.7f,
        maxTokens: Int = 512
    ): Result<String>
    
    /**
     * Get the currently loaded model ID, or null if none loaded.
     */
    fun getLoadedModelId(): String?
    
    /**
     * Get information about the loaded model.
     */
    fun getModelInfo(): LoadedModelInfo?
    
    /**
     * Cancel any ongoing generation.
     */
    fun cancelGeneration()
    
    /**
     * Check if generation is currently in progress.
     */
    fun isGenerating(): Boolean
}

/**
 * Represents a chat message with role and content.
 */
data class ChatMessage(
    val role: ChatRole,
    val content: String
)

/**
 * Role in a chat conversation.
 */
enum class ChatRole {
    SYSTEM,
    USER,
    ASSISTANT
}

/**
 * Information about the currently loaded model.
 */
data class LoadedModelInfo(
    val modelId: String,
    val modelPath: String,
    val modelName: String,
    val contextSize: Int,
    val loadedAt: Long
)
