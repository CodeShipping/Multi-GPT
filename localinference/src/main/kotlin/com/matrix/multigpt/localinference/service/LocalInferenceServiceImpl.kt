package com.matrix.multigpt.localinference.service

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.codeshipping.llamakotlin.LlamaConfig
import org.codeshipping.llamakotlin.LlamaModel
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of LocalInferenceService using llama-kotlin-android.
 * Provides local AI inference capabilities using GGUF models.
 */
@Singleton
class LocalInferenceServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : LocalInferenceService {
    
    private var llamaModel: LlamaModel? = null
    private var loadedModelInfo: LoadedModelInfo? = null
    private val isGenerating = AtomicBoolean(false)
    private val shouldCancel = AtomicBoolean(false)
    
    override fun isModelLoaded(): Boolean {
        return llamaModel != null && loadedModelInfo != null
    }
    
    override suspend fun loadModel(modelPath: String, contextSize: Int): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                // Unload any existing model first
                unloadModel()
                
                val modelFile = File(modelPath)
                if (!modelFile.exists()) {
                    return@withContext Result.failure(IllegalArgumentException("Model file not found: $modelPath"))
                }
                
                // Load the model with performance-optimized settings
                val availableCores = Runtime.getRuntime().availableProcessors()
                // Use all available cores for best performance
                val threadCount = availableCores.coerceIn(2, 16)
                
                // Get available memory to determine optimal batch size
                val runtime = java.lang.Runtime.getRuntime()
                val maxMemory = runtime.maxMemory() / (1024 * 1024) // MB
                val freeMemory = runtime.freeMemory() / (1024 * 1024) // MB
                
                // Smaller batch for low memory devices, larger for high memory
                val optimalBatchSize = when {
                    maxMemory > 4096 -> 1024  // High memory: max performance
                    maxMemory > 2048 -> 512   // Medium memory
                    else -> 256               // Low memory: safe
                }
                
                android.util.Log.d("LocalInference", "Loading model with $threadCount threads, batch=$optimalBatchSize (cores: $availableCores, mem: ${maxMemory}MB)")
                
                val model = LlamaModel.load(modelPath) {
                    this.contextSize = contextSize
                    this.threads = threadCount
                    this.threadsBatch = threadCount
                    this.batchSize = optimalBatchSize
                    this.temperature = 0.7f
                    this.topP = 0.9f
                    this.topK = 40
                    this.useMmap = true   // Memory-mapped for faster loading
                    this.useMlock = false // Don't lock (uses too much memory)
                }
                
                llamaModel = model
                loadedModelInfo = LoadedModelInfo(
                    modelId = modelFile.nameWithoutExtension,
                    modelPath = modelPath,
                    modelName = modelFile.nameWithoutExtension,
                    contextSize = contextSize,
                    loadedAt = System.currentTimeMillis()
                )
                
                Result.success(true)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    override fun unloadModel() {
        try {
            llamaModel?.close()
        } catch (e: Exception) {
            // Ignore close errors
        }
        llamaModel = null
        loadedModelInfo = null
    }
    
    override fun generateChatCompletion(
        messages: List<ChatMessage>,
        temperature: Float,
        maxTokens: Int
    ): Flow<String> = flow<String> {
        val model = llamaModel
            ?: throw IllegalStateException("No model loaded. Call loadModel() first.")
        
        android.util.Log.d("LocalInference", "generateChatCompletion started")
        
        // Cancel any previous generation and wait for it to finish
        if (model.isGenerating) {
            android.util.Log.d("LocalInference", "Cancelling previous generation...")
            model.cancelGeneration()
            // Wait for generation to actually stop (up to 3 seconds)
            repeat(30) {
                if (!model.isGenerating) return@repeat
                kotlinx.coroutines.delay(100)
            }
            android.util.Log.d("LocalInference", "Previous generation cancelled")
        }
        
        isGenerating.set(true)
        shouldCancel.set(false)
        
        try {
            // Format messages into a prompt based on detected model type
            val prompt = formatChatPrompt(messages)
            android.util.Log.d("LocalInference", "Prompt formatted, length: ${prompt.length}")
            
            // Use streaming generation with timeout monitoring
            var tokenCount = 0
            var lastTokenTime = System.currentTimeMillis()
            val prefillTimeout = 60_000L // 60 seconds for prefill (first token)
            val tokenTimeout = 30_000L // 30 seconds between tokens
            
            // Log thinking status after delay (non-blocking)
            var lastLogTime = System.currentTimeMillis()
            
            try {
                android.util.Log.d("LocalInference", "Starting generateStream")
                
                // Start generation with monitoring
                kotlinx.coroutines.withTimeoutOrNull(prefillTimeout) {
                    model.generateStream(prompt).collect { token ->
                        // Log prefill completion on first token
                        if (tokenCount == 0) {
                            val prefillTime = System.currentTimeMillis() - lastLogTime
                            android.util.Log.d("LocalInference", "First token after ${prefillTime}ms")
                        }
                        
                        if (!shouldCancel.get()) {
                            tokenCount++
                            lastTokenTime = System.currentTimeMillis()
                            
                            if (tokenCount <= 3 || tokenCount % 50 == 0) {
                                android.util.Log.d("LocalInference", "Token #$tokenCount: '$token'")
                            }
                            emit(token)
                        }
                    }
                }
                
                android.util.Log.d("LocalInference", "Stream completed. Total tokens: $tokenCount")
                
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                android.util.Log.e("LocalInference", "Generation timed out waiting for first token")
                if (tokenCount == 0) {
                    throw IllegalStateException("Model timed out during prefill. The model may be incompatible or too large.")
                }
            } catch (e: Exception) {
                android.util.Log.e("LocalInference", "Generate error: ${e.message}", e)
                throw e
            }
            
            // If no tokens were emitted, provide guidance
            if (tokenCount == 0) {
                android.util.Log.w("LocalInference", "No tokens generated!")
                emit("(No response generated - this model may require a different prompt format. Try Llama 3.x compatible models.)")
            }
            
        } finally {
            isGenerating.set(false)
            android.util.Log.d("LocalInference", "generateChatCompletion finished")
        }
    }.flowOn(Dispatchers.IO)
    
    override suspend fun generateChatCompletionSync(
        messages: List<ChatMessage>,
        temperature: Float,
        maxTokens: Int
    ): Result<String> = withContext(Dispatchers.IO) {
        val model = llamaModel
            ?: return@withContext Result.failure(IllegalStateException("No model loaded. Call loadModel() first."))
        
        isGenerating.set(true)
        shouldCancel.set(false)
        
        try {
            val prompt = formatChatPrompt(messages)
            
            val config = LlamaConfig {
                this.temperature = temperature
                this.maxTokens = maxTokens
            }
            
            val result = model.generate(prompt, config)
            
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            isGenerating.set(false)
        }
    }
    
    override fun getLoadedModelId(): String? {
        return loadedModelInfo?.modelId
    }
    
    override fun getModelInfo(): LoadedModelInfo? {
        return loadedModelInfo
    }
    
    override fun cancelGeneration() {
        shouldCancel.set(true)
    }
    
    override fun isGenerating(): Boolean {
        return isGenerating.get()
    }
    
    /**
     * Format chat messages into a prompt string.
     * Uses the model's embedded chat template from GGUF metadata for automatic format detection.
     * Falls back to ChatML format if no template is available or if native call fails.
     */
    private fun formatChatPrompt(messages: List<ChatMessage>): String {
        val model = llamaModel
        
        // Try to use native chat template first (reads from GGUF metadata)
        // This can fail if the model was freed (e.g., app was backgrounded)
        if (model != null) {
            try {
                // First, verify the model is still valid by checking a simple property
                // If this throws, the native context is invalid
                val chatTemplate: String
                try {
                    chatTemplate = model.getChatTemplate()
                } catch (e: Error) {
                    // Native error - model context is invalid, clear reference
                    android.util.Log.e("LocalInference", "Model context invalid (freed?), clearing reference", e)
                    llamaModel = null
                    loadedModelInfo = null
                    throw IllegalStateException("Model was unloaded. Please reload the model.")
                }
                
                android.util.Log.d("LocalInference", "Chat template available: ${chatTemplate.isNotEmpty()}")
                
                if (chatTemplate.isNotEmpty()) {
                    // Convert messages to JSON format for native template application
                    val messagesJson = buildMessagesJson(messages)
                    android.util.Log.d("LocalInference", "Applying native chat template")
                    
                    val prompt: String
                    try {
                        prompt = model.applyChatTemplate(messagesJson, true)
                    } catch (e: Error) {
                        // Native error during template application
                        android.util.Log.e("LocalInference", "Native template application crashed", e)
                        llamaModel = null
                        loadedModelInfo = null
                        throw IllegalStateException("Model was unloaded. Please reload the model.")
                    }
                    
                    if (prompt.isNotEmpty()) {
                        // Validate the prompt - only reject clearly broken outputs
                        val firstUserContent = messages.firstOrNull { it.role == ChatRole.USER }?.content?.take(30)
                        
                        // Only reject if user content appears directly after header marker (missing role)
                        val isMalformed = when {
                            firstUserContent != null && prompt.contains("<|start_header_id|>$firstUserContent") -> {
                                android.util.Log.w("LocalInference", "Template missing role after header")
                                true
                            }
                            // Template output too short (less than any meaningful response)
                            prompt.length < 30 -> {
                                android.util.Log.w("LocalInference", "Template output too short: ${prompt.length}")
                                true
                            }
                            else -> false
                        }
                        
                        if (!isMalformed) {
                            android.util.Log.d("LocalInference", "Native template applied successfully, length: ${prompt.length}")
                            android.util.Log.d("LocalInference", "Prompt preview: ${prompt.take(200)}")
                            return prompt
                        } else {
                            android.util.Log.w("LocalInference", "Native template produced malformed output, falling back")
                        }
                    }
                }
            } catch (e: IllegalStateException) {
                // Re-throw model unloaded errors
                throw e
            } catch (e: Exception) {
                android.util.Log.w("LocalInference", "Failed to apply native chat template: ${e.message}")
                // Fall through to default format
            }
        }
        
        // Detect model type from filename and use appropriate fallback format
        val modelName = loadedModelInfo?.modelName?.lowercase() ?: ""
        val modelPath = loadedModelInfo?.modelPath?.lowercase() ?: ""
        val combined = "$modelName $modelPath"
        
        return when {
            combined.contains("llama-2") || combined.contains("llama2") || 
            combined.contains("llama_2") || combined.contains("llama 2") -> {
                android.util.Log.d("LocalInference", "Using fallback Llama 2 format")
                formatLlama2Prompt(messages)
            }
            combined.contains("mistral") || combined.contains("mixtral") -> {
                android.util.Log.d("LocalInference", "Using fallback Mistral format")
                formatMistralPrompt(messages)
            }
            combined.contains("qwen") || combined.contains("yi-") -> {
                android.util.Log.d("LocalInference", "Using fallback ChatML format")
                formatChatMLPrompt(messages)
            }
            combined.contains("gemma") -> {
                android.util.Log.d("LocalInference", "Using fallback Gemma format")
                formatGemmaPrompt(messages)
            }
            combined.contains("phi") -> {
                android.util.Log.d("LocalInference", "Using fallback Phi format")
                formatPhiPrompt(messages)
            }
            else -> {
                // ChatML is the most universal format - works with many models
                android.util.Log.d("LocalInference", "Using fallback ChatML format (universal)")
                formatChatMLPrompt(messages)
            }
        }
    }
    
    /**
     * Convert messages to JSON format for native template application.
     */
    private fun buildMessagesJson(messages: List<ChatMessage>): String {
        // Add default system message if not present
        val hasSystem = messages.any { it.role == ChatRole.SYSTEM }
        val allMessages = if (!hasSystem) {
            listOf(ChatMessage(ChatRole.SYSTEM, "You are a helpful AI assistant.")) + messages
        } else {
            messages
        }
        
        return buildString {
            append("[")
            allMessages.forEachIndexed { index, message ->
                if (index > 0) append(",")
                val role = when (message.role) {
                    ChatRole.SYSTEM -> "system"
                    ChatRole.USER -> "user"
                    ChatRole.ASSISTANT -> "assistant"
                }
                // Escape content for JSON
                val content = message.content
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t")
                append("{\"role\":\"$role\",\"content\":\"$content\"}")
            }
            append("]")
        }
    }
    
    /**
     * Fallback Llama 3.x Instruct format.
     */
    private fun formatLlama3Prompt(messages: List<ChatMessage>): String {
        return buildString {
            append("<|begin_of_text|>")
            
            val hasSystem = messages.any { it.role == ChatRole.SYSTEM }
            if (!hasSystem) {
                append("<|start_header_id|>system<|end_header_id|>\n\n")
                append("You are a helpful AI assistant.")
                append("<|eot_id|>")
            }
            
            for (message in messages) {
                when (message.role) {
                    ChatRole.SYSTEM -> {
                        append("<|start_header_id|>system<|end_header_id|>\n\n")
                        append(message.content)
                        append("<|eot_id|>")
                    }
                    ChatRole.USER -> {
                        append("<|start_header_id|>user<|end_header_id|>\n\n")
                        append(message.content)
                        append("<|eot_id|>")
                    }
                    ChatRole.ASSISTANT -> {
                        append("<|start_header_id|>assistant<|end_header_id|>\n\n")
                        append(message.content)
                        append("<|eot_id|>")
                    }
                }
            }
            
            append("<|start_header_id|>assistant<|end_header_id|>\n\n")
        }
    }
    
    /**
     * Llama 2 Chat format.
     */
    private fun formatLlama2Prompt(messages: List<ChatMessage>): String {
        return buildString {
            val systemMessage = messages.find { it.role == ChatRole.SYSTEM }?.content 
                ?: "You are a helpful AI assistant."
            
            var isFirst = true
            for (message in messages.filter { it.role != ChatRole.SYSTEM }) {
                when (message.role) {
                    ChatRole.USER -> {
                        append("[INST] ")
                        if (isFirst) {
                            append("<<SYS>>\n$systemMessage\n<</SYS>>\n\n")
                            isFirst = false
                        }
                        append(message.content)
                        append(" [/INST]")
                    }
                    ChatRole.ASSISTANT -> {
                        append(" ")
                        append(message.content)
                        append(" </s><s>")
                    }
                    else -> {}
                }
            }
        }
    }
    
    /**
     * Mistral Instruct format.
     */
    private fun formatMistralPrompt(messages: List<ChatMessage>): String {
        return buildString {
            append("<s>")
            for (message in messages) {
                when (message.role) {
                    ChatRole.SYSTEM, ChatRole.USER -> {
                        append("[INST] ")
                        append(message.content)
                        append(" [/INST]")
                    }
                    ChatRole.ASSISTANT -> {
                        append(message.content)
                        append("</s>")
                    }
                }
            }
        }
    }
    
    /**
     * ChatML format (OpenAI/Qwen style).
     */
    private fun formatChatMLPrompt(messages: List<ChatMessage>): String {
        return buildString {
            val hasSystem = messages.any { it.role == ChatRole.SYSTEM }
            if (!hasSystem) {
                append("<|im_start|>system\nYou are a helpful AI assistant.<|im_end|>\n")
            }
            for (message in messages) {
                val role = when (message.role) {
                    ChatRole.SYSTEM -> "system"
                    ChatRole.USER -> "user"
                    ChatRole.ASSISTANT -> "assistant"
                }
                append("<|im_start|>$role\n${message.content}<|im_end|>\n")
            }
            append("<|im_start|>assistant\n")
        }
    }
    
    /**
     * Gemma format.
     */
    private fun formatGemmaPrompt(messages: List<ChatMessage>): String {
        return buildString {
            append("<bos>")
            for (message in messages) {
                when (message.role) {
                    ChatRole.USER -> {
                        append("<start_of_turn>user\n${message.content}<end_of_turn>\n")
                    }
                    ChatRole.ASSISTANT -> {
                        append("<start_of_turn>model\n${message.content}<end_of_turn>\n")
                    }
                    ChatRole.SYSTEM -> {
                        // Gemma doesn't have explicit system
                    }
                }
            }
            append("<start_of_turn>model\n")
        }
    }
    
    /**
     * Phi format (Microsoft).
     */
    private fun formatPhiPrompt(messages: List<ChatMessage>): String {
        return buildString {
            val systemMessage = messages.find { it.role == ChatRole.SYSTEM }?.content 
                ?: "You are a helpful AI assistant."
            append("<|system|>\n$systemMessage<|end|>\n")
            for (message in messages.filter { it.role != ChatRole.SYSTEM }) {
                when (message.role) {
                    ChatRole.USER -> append("<|user|>\n${message.content}<|end|>\n")
                    ChatRole.ASSISTANT -> append("<|assistant|>\n${message.content}<|end|>\n")
                    else -> {}
                }
            }
            append("<|assistant|>\n")
        }
    }
}
