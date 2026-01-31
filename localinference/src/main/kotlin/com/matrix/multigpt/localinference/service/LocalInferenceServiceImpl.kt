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
                
                // Create config for the model using DSL syntax
                val config = LlamaConfig {
                    this.contextSize = contextSize
                    this.batchSize = 512
                    this.gpuLayers = 0 // CPU only for compatibility
                }
                
                // Load the model
                val model = LlamaModel.load(modelPath, config)
                
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
        
        isGenerating.set(true)
        shouldCancel.set(false)
        
        try {
            // Format messages into a prompt
            val prompt = formatChatPrompt(messages)
            
            // Create config override for this generation using DSL syntax
            val config = LlamaConfig {
                this.temperature = temperature
                this.maxTokens = maxTokens
            }
            
            // Generate the response
            val result = model.generate(prompt, config)
            
            // Emit the full result
            if (!shouldCancel.get()) {
                emit(result)
            }
            
        } finally {
            isGenerating.set(false)
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
    
    /**
     * Format chat messages into a prompt string.
     * Uses ChatML format compatible with most instruction-tuned models.
     */
    private fun formatChatPrompt(messages: List<ChatMessage>): String {
        val sb = StringBuilder()
        
        for (message in messages) {
            when (message.role) {
                ChatRole.SYSTEM -> {
                    sb.append("<|im_start|>system\n")
                    sb.append(message.content)
                    sb.append("<|im_end|>\n")
                }
                ChatRole.USER -> {
                    sb.append("<|im_start|>user\n")
                    sb.append(message.content)
                    sb.append("<|im_end|>\n")
                }
                ChatRole.ASSISTANT -> {
                    sb.append("<|im_start|>assistant\n")
                    sb.append(message.content)
                    sb.append("<|im_end|>\n")
                }
            }
        }
        
        // Add the assistant prefix to prompt the model to respond
        sb.append("<|im_start|>assistant\n")
        
        return sb.toString()
    }
}
