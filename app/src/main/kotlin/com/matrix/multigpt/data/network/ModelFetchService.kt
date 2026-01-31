package com.matrix.multigpt.data.network

import android.content.Context
import com.matrix.multigpt.data.dto.GoogleModelsResponse
import com.matrix.multigpt.data.dto.ModelFetchResult
import com.matrix.multigpt.data.dto.ModelInfo
import com.matrix.multigpt.data.dto.OpenAIModelsResponse
import com.matrix.multigpt.data.model.ApiType
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ModelFetchService {
    suspend fun fetchModels(
        apiType: ApiType,
        apiUrl: String,
        apiKey: String?
    ): ModelFetchResult
}

@Singleton
class ModelFetchServiceImpl @Inject constructor(
    private val networkClient: NetworkClient,
    @ApplicationContext private val context: Context
) : ModelFetchService {

    override suspend fun fetchModels(
        apiType: ApiType,
        apiUrl: String,
        apiKey: String?
    ): ModelFetchResult = withContext(Dispatchers.IO) {
        try {
            when (apiType) {
                ApiType.OPENAI -> fetchOpenAIModels(apiUrl, apiKey)
                ApiType.GROQ -> fetchGroqModels(apiUrl, apiKey)
                ApiType.GOOGLE -> fetchGoogleModels(apiUrl, apiKey)
                ApiType.ANTHROPIC -> fetchAnthropicModels()
                ApiType.OLLAMA -> fetchOllamaModels(apiUrl)
                ApiType.BEDROCK -> fetchBedrockModels()
                ApiType.LOCAL -> fetchLocalModels()
            }
        } catch (e: Exception) {
            ModelFetchResult.Error("Failed to fetch models: ${e.message}")
        }
    }

    private suspend fun fetchOpenAIModels(apiUrl: String, apiKey: String?): ModelFetchResult {
        if (apiKey.isNullOrBlank()) {
            return ModelFetchResult.Error("API key is required for OpenAI. Using fallback models.")
        }

        return try {
            val response = networkClient().get("${apiUrl.trimEnd('/')}/models") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
            }.body<OpenAIModelsResponse>()

            val models = response.data
                .filter { it.id.contains("gpt", ignoreCase = true) }
                .map { model ->
                    ModelInfo(
                        id = model.id,
                        name = model.id,
                        description = "Owned by: ${model.ownedBy ?: "OpenAI"}",
                        createdAt = model.created
                    )
                }
                .sortedByDescending { it.createdAt ?: 0 }

            if (models.isEmpty()) {
                ModelFetchResult.Error("No models found. Using fallback models.")
            } else {
                ModelFetchResult.Success(models)
            }
        } catch (e: Exception) {
            ModelFetchResult.Error("Failed to fetch OpenAI models: ${e.message ?: "Unknown error"}. Using fallback models.")
        }
    }

    private suspend fun fetchGroqModels(apiUrl: String, apiKey: String?): ModelFetchResult {
        if (apiKey.isNullOrBlank()) {
            // Return error but allow fallback to hardcoded models
            return ModelFetchResult.Error("API key is required for Groq. Using fallback models.")
        }

        return try {
            val url = "${apiUrl.trimEnd('/')}/models"
            val response = networkClient().get(url) {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
            }.body<OpenAIModelsResponse>()

            val models = response.data.map { model ->
                ModelInfo(
                    id = model.id,
                    name = model.id,
                    description = "Owned by: ${model.ownedBy ?: "Groq"}",
                    createdAt = model.created
                )
            }.sortedByDescending { it.createdAt ?: 0 }

            if (models.isEmpty()) {
                ModelFetchResult.Error("No models found. Using fallback models.")
            } else {
                ModelFetchResult.Success(models)
            }
        } catch (e: Exception) {
            // Log error and return error result, which will trigger fallback
            ModelFetchResult.Error("Failed to fetch Groq models: ${e.message ?: "Unknown error"}. Using fallback models.")
        }
    }

    private suspend fun fetchGoogleModels(apiUrl: String, apiKey: String?): ModelFetchResult {
        if (apiKey.isNullOrBlank()) {
            return ModelFetchResult.Error("API key is required for Google AI. Using fallback models.")
        }

        return try {
            val response = networkClient().get("${apiUrl.trimEnd('/')}/v1beta/models?key=$apiKey")
                .body<GoogleModelsResponse>()

            val models = response.models
                .filter { model ->
                    model.supportedGenerationMethods.contains("generateContent")
                }
                .map { model ->
                    val modelId = model.name.substringAfterLast("/")
                    ModelInfo(
                        id = modelId,
                        name = model.displayName ?: modelId,
                        description = model.description
                    )
                }

            if (models.isEmpty()) {
                ModelFetchResult.Error("No models found. Using fallback models.")
            } else {
                ModelFetchResult.Success(models)
            }
        } catch (e: Exception) {
            ModelFetchResult.Error("Failed to fetch Google models: ${e.message ?: "Unknown error"}. Using fallback models.")
        }
    }

    private suspend fun fetchAnthropicModels(): ModelFetchResult {
        // Anthropic doesn't provide a public models endpoint
        // Return hardcoded list of known models
        val models = listOf(
            ModelInfo(
                id = "claude-3-5-sonnet-20241022",
                name = "Claude 3.5 Sonnet",
                description = "Most intelligent model"
            ),
            ModelInfo(
                id = "claude-3-5-sonnet-20240620",
                name = "Claude 3.5 Sonnet (Legacy)",
                description = "Previous version of Claude 3.5 Sonnet"
            ),
            ModelInfo(
                id = "claude-3-5-haiku-20241022",
                name = "Claude 3.5 Haiku",
                description = "Fastest model"
            ),
            ModelInfo(
                id = "claude-3-opus-20240229",
                name = "Claude 3 Opus",
                description = "Powerful model for complex tasks"
            ),
            ModelInfo(
                id = "claude-3-sonnet-20240229",
                name = "Claude 3 Sonnet",
                description = "Balanced performance and speed"
            ),
            ModelInfo(
                id = "claude-3-haiku-20240307",
                name = "Claude 3 Haiku",
                description = "Fast and compact model"
            )
        )
        return ModelFetchResult.Success(models)
    }

    private suspend fun fetchOllamaModels(apiUrl: String): ModelFetchResult {
        if (apiUrl.isBlank()) {
            return ModelFetchResult.Error("API URL is required for Ollama. Using fallback models.")
        }

        return try {
            // Ollama uses /api/tags endpoint to list models
            val response = networkClient().get("${apiUrl.trimEnd('/')}/api/tags")
                .body<OllamaModelsResponse>()

            val models = response.models.map { model ->
                ModelInfo(
                    id = model.name,
                    name = model.name,
                    description = "Size: ${formatSize(model.size)}"
                )
            }

            if (models.isEmpty()) {
                ModelFetchResult.Error("No models found. Using fallback models.")
            } else {
                ModelFetchResult.Success(models)
            }
        } catch (e: Exception) {
            ModelFetchResult.Error("Failed to fetch Ollama models: ${e.message ?: "Unknown error"}. Using fallback models.")
        }
    }

    private suspend fun fetchBedrockModels(): ModelFetchResult {
        // AWS Bedrock doesn't provide a public models endpoint
        // Return hardcoded list of available foundation models
        val models = listOf(
            ModelInfo(
                id = "anthropic.claude-3-5-sonnet-20240620-v1:0",
                name = "Claude 3.5 Sonnet",
                description = "Anthropic's most intelligent model"
            ),
            ModelInfo(
                id = "anthropic.claude-3-sonnet-20240229-v1:0",
                name = "Claude 3 Sonnet",
                description = "Balance of intelligence and speed"
            ),
            ModelInfo(
                id = "anthropic.claude-3-haiku-20240307-v1:0",
                name = "Claude 3 Haiku",
                description = "Fast and lightweight model"
            ),
            ModelInfo(
                id = "anthropic.claude-instant-v1",
                name = "Claude Instant",
                description = "Fast, affordable model"
            ),
            ModelInfo(
                id = "amazon.titan-text-express-v1",
                name = "Titan Text G1 - Express",
                description = "Amazon's flagship text generation model"
            ),
            ModelInfo(
                id = "amazon.titan-text-lite-v1",
                name = "Titan Text G1 - Lite",
                description = "Lightweight text model"
            ),
            ModelInfo(
                id = "ai21.j2-ultra-v1",
                name = "Jurassic-2 Ultra",
                description = "AI21 Labs' most powerful model"
            ),
            ModelInfo(
                id = "ai21.j2-mid-v1",
                name = "Jurassic-2 Mid",
                description = "Balanced performance model"
            ),
            ModelInfo(
                id = "cohere.command-text-v14",
                name = "Command",
                description = "Cohere's instruction-following model"
            ),
            ModelInfo(
                id = "cohere.command-light-text-v14",
                name = "Command Light",
                description = "Fast and efficient model"
            ),
            ModelInfo(
                id = "meta.llama2-13b-chat-v1",
                name = "Llama 2 Chat 13B",
                description = "Meta's fine-tuned chat model"
            ),
            ModelInfo(
                id = "meta.llama2-70b-chat-v1",
                name = "Llama 2 Chat 70B",
                description = "Large parameter chat model"
            )
        )
        return ModelFetchResult.Success(models)
    }

    private suspend fun fetchLocalModels(): ModelFetchResult {
        // Check which models are downloaded by looking in the models directory
        val modelsDir = File(context.filesDir, "models")
        val downloadedFiles = if (modelsDir.exists()) {
            modelsDir.listFiles()?.map { it.name.removeSuffix(".gguf") }?.toSet() ?: emptySet()
        } else {
            emptySet()
        }
        
        // Define available models with their file names
        data class LocalModelDef(
            val id: String,
            val name: String,
            val description: String,
            val fileName: String
        )
        
        val availableModels = listOf(
            LocalModelDef(
                id = "llama-3.2-1b-q4",
                name = "Llama 3.2 1B (Q4)",
                description = "Fast, lightweight model (~700MB) - Best for basic tasks",
                fileName = "llama-3.2-1b-q4"
            ),
            LocalModelDef(
                id = "llama-3.2-3b-q4",
                name = "Llama 3.2 3B (Q4)",
                description = "Balanced model (~1.8GB) - Good quality and speed",
                fileName = "llama-3.2-3b-q4"
            ),
            LocalModelDef(
                id = "phi-3-mini-q4",
                name = "Phi 3 Mini (Q4)",
                description = "Microsoft's efficient model (~2.3GB) - Great for coding",
                fileName = "phi-3-mini-q4"
            ),
            LocalModelDef(
                id = "gemma-2b-q4",
                name = "Gemma 2B (Q4)",
                description = "Google's compact model (~1.5GB) - General purpose",
                fileName = "gemma-2b-q4"
            ),
            LocalModelDef(
                id = "qwen2-1.5b-q4",
                name = "Qwen2 1.5B (Q4)",
                description = "Alibaba's multilingual model (~1GB) - Good for multiple languages",
                fileName = "qwen2-1.5b-q4"
            ),
            LocalModelDef(
                id = "tinyllama-1.1b-q4",
                name = "TinyLlama 1.1B (Q4)",
                description = "Smallest model (~640MB) - Fastest inference",
                fileName = "tinyllama-1.1b-q4"
            )
        )
        
        // Map to ModelInfo with isAvailable based on download status
        val models = availableModels.map { model ->
            val isDownloaded = downloadedFiles.contains(model.fileName) || 
                               downloadedFiles.contains(model.id)
            ModelInfo(
                id = model.id,
                name = model.name,
                description = if (isDownloaded) model.description else "${model.description} [NOT DOWNLOADED]",
                isAvailable = isDownloaded
            )
        }
        
        // Sort: downloaded first, then by name
        val sortedModels = models.sortedByDescending { it.isAvailable }
        
        return ModelFetchResult.Success(sortedModels)
    }

    private fun formatSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0

        return when {
            gb >= 1 -> String.format("%.2f GB", gb)
            mb >= 1 -> String.format("%.2f MB", mb)
            else -> String.format("%.2f KB", kb)
        }
    }
}

@kotlinx.serialization.Serializable
data class OllamaModelsResponse(
    @kotlinx.serialization.SerialName("models") val models: List<OllamaModel>
)

@kotlinx.serialization.Serializable
data class OllamaModel(
    @kotlinx.serialization.SerialName("name") val name: String,
    @kotlinx.serialization.SerialName("size") val size: Long,
    @kotlinx.serialization.SerialName("digest") val digest: String? = null,
    @kotlinx.serialization.SerialName("modified_at") val modifiedAt: String? = null
)
