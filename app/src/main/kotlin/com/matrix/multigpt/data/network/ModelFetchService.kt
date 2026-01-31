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
        val downloadedFilesMap = if (modelsDir.exists()) {
            modelsDir.listFiles()
                ?.filter { it.isFile && it.extension.lowercase() == "gguf" }
                ?.associate { it.nameWithoutExtension to it.absolutePath }
                ?: emptyMap()
        } else {
            emptyMap()
        }
        val downloadedFiles = downloadedFilesMap.keys
        
        android.util.Log.d("ModelFetchService", "Downloaded GGUF files: $downloadedFiles")
        
        // Load imported models from SharedPreferences AND also detect any unregistered imported models
        val registeredImported = loadImportedModels(modelsDir)
        
        // Also detect any GGUF files that start with "imported-" but aren't in SharedPreferences
        val unregisteredImported = downloadedFilesMap.filter { (name, _) -> 
            name.startsWith("imported-") && registeredImported.none { it.id == name }
        }.map { (name, path) ->
            val file = File(path)
            ModelInfo(
                id = name,
                name = "Imported Model ($name)",
                description = "[Imported] Size: ${formatSize(file.length())}",
                isAvailable = true
            )
        }
        
        val importedModels = registeredImported + unregisteredImported
        android.util.Log.d("ModelFetchService", "All imported models: ${importedModels.map { it.id }}")
        
        // Try to fetch catalog from localinference module (Firebase-sourced)
        val catalogModels = try {
            fetchCatalogFromLocalInferenceModule(downloadedFiles)
        } catch (e: Exception) {
            android.util.Log.w("ModelFetchService", "Failed to fetch from localinference module: ${e.message}")
            emptyList()
        }
        
        android.util.Log.d("ModelFetchService", "Catalog models from Firebase: ${catalogModels.map { it.id }}")
        
        // Combine: imported models first (they're always available), then catalog models
        val allModels = importedModels + catalogModels.filter { catalog ->
            importedModels.none { it.id == catalog.id }
        }
        
        // Sort: available (downloaded/imported) first, then by name
        val sortedModels = allModels.sortedByDescending { it.isAvailable }
        
        return ModelFetchResult.Success(sortedModels)
    }
    
    /**
     * Fetch model catalog from localinference module using reflection.
     * This ensures we use the same Firebase-sourced catalog as the Local AI settings screen.
     */
    private suspend fun fetchCatalogFromLocalInferenceModule(downloadedFiles: Set<String>): List<ModelInfo> {
        return try {
            // Get LocalInferenceProvider instance via reflection
            val providerClass = Class.forName("com.matrix.multigpt.localinference.LocalInferenceProvider")
            val companionField = providerClass.getDeclaredField("Companion")
            val companion = companionField.get(null)
            val getInstanceMethod = companion.javaClass.getMethod("getInstance", Context::class.java)
            val provider = getInstanceMethod.invoke(companion, context)!!
            
            // Get modelRepository
            val repoMethod = provider.javaClass.getDeclaredMethod("getModelRepository")
            val repository = repoMethod.invoke(provider)
            
            // Call getModels() - suspend function via continuation
            val getModelsMethod = repository.javaClass.getDeclaredMethod("getModels", kotlin.coroutines.Continuation::class.java)
            
            @Suppress("UNCHECKED_CAST")
            val models = invokeSuspend<List<Any>>(getModelsMethod, repository)
            
            // Convert to ModelInfo
            models.mapNotNull { model ->
                try {
                    val modelClass = model.javaClass
                    val id = modelClass.getMethod("getId").invoke(model) as String
                    val name = modelClass.getMethod("getName").invoke(model) as String
                    val description = modelClass.getMethod("getDescription").invoke(model) as String
                    val isDownloaded = downloadedFiles.contains(id)
                    
                    ModelInfo(
                        id = id,
                        name = name,
                        description = if (isDownloaded) description else "$description [NOT DOWNLOADED]",
                        isAvailable = isDownloaded
                    )
                } catch (e: Exception) {
                    android.util.Log.w("ModelFetchService", "Failed to parse model: ${e.message}")
                    null
                }
            }
        } catch (e: ClassNotFoundException) {
            android.util.Log.w("ModelFetchService", "localinference module not available")
            emptyList()
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
    
    /**
     * Load imported models from SharedPreferences.
     */
    private fun loadImportedModels(modelsDir: File): List<ModelInfo> {
        val prefs = context.getSharedPreferences("local_ai_imported_models", Context.MODE_PRIVATE)
        val json = prefs.getString("imported_models", "[]") ?: "[]"
        
        if (json == "[]") return emptyList()
        
        val models = mutableListOf<ModelInfo>()
        
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
                    val description = Regex(""""description"\s*:\s*"([^"]+)"""").find(cleanEntry)?.groupValues?.get(1) ?: "Imported model"
                    
                    // Check if model file still exists
                    val modelFile = File(modelsDir, "$id.gguf")
                    if (modelFile.exists()) {
                        models.add(
                            ModelInfo(
                                id = id,
                                name = name,
                                description = "[Imported] $description",
                                isAvailable = true
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
