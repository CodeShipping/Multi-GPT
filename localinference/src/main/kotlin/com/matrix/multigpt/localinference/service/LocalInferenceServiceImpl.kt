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
                
                // Load the model with optimized settings
                val model = LlamaModel.load(modelPath) {
                    this.contextSize = contextSize
                    this.threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 8) // Use multiple CPU cores
                    this.temperature = 0.7f
                    this.topP = 0.9f
                    this.topK = 40
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
            
            // Emit a "thinking" indicator after 5 seconds of waiting
            val thinkingJob = kotlinx.coroutines.GlobalScope.launch {
                kotlinx.coroutines.delay(5000)
                if (tokenCount == 0 && !shouldCancel.get()) {
                    android.util.Log.d("LocalInference", "Still processing (prefill phase)...")
                }
            }
            
            try {
                android.util.Log.d("LocalInference", "Starting generateStream")
                
                // Start generation with monitoring
                kotlinx.coroutines.withTimeoutOrNull(prefillTimeout) {
                    model.generateStream(prompt).collect { token ->
                        thinkingJob.cancel() // Cancel thinking indicator once we get a token
                        
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
                
                thinkingJob.cancel()
                android.util.Log.d("LocalInference", "Stream completed. Total tokens: $tokenCount")
                
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                thinkingJob.cancel()
                android.util.Log.e("LocalInference", "Generation timed out waiting for first token")
                if (tokenCount == 0) {
                    throw IllegalStateException("Model timed out during prefill. The model may be incompatible or too large.")
                }
            } catch (e: Exception) {
                thinkingJob.cancel()
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
     * Uses Llama 3.x Instruct format for best compatibility with Llama models.
     * Reference: https://llama.meta.com/docs/model-cards-and-prompt-formats/llama3_2
     */
    private fun formatChatPrompt(messages: List<ChatMessage>): String {
        return buildString {
            append("<|begin_of_text|>")
            
            // Check if there's a system message, otherwise add default
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
            
            // Add the assistant prefix to prompt the model to respond
            append("<|start_header_id|>assistant<|end_header_id|>\n\n")
        }
    }
}
