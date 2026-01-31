package com.matrix.multigpt.localinference.data.network

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.matrix.multigpt.localinference.data.dto.LocalModelDto
import com.matrix.multigpt.localinference.data.dto.ModelCatalogResponse
import com.matrix.multigpt.localinference.data.dto.ModelFamilyDto
import com.matrix.multigpt.localinference.data.dto.ModelPerformanceDto
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Firebase Realtime Database implementation of ModelCatalogService.
 */
@Singleton
class ModelCatalogServiceImpl @Inject constructor() : ModelCatalogService {

    private val database = FirebaseDatabase.getInstance()
    private val catalogRef = database.getReference("localModels")

    override suspend fun fetchModelCatalog(): ModelCatalogResponse = 
        withTimeout(FETCH_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        try {
                            // Check if data exists
                            if (!snapshot.exists()) {
                                // Return default models if no Firebase data configured
                                continuation.resume(
                                    ModelCatalogResponse(
                                        families = getDefaultFamilies(),
                                        models = getDefaultModels(),
                                        version = 0,
                                        lastUpdated = System.currentTimeMillis()
                                    )
                                )
                                return
                            }
                            
                            val version = snapshot.child("version").getValue(Int::class.java) ?: 1
                            val lastUpdated = snapshot.child("lastUpdated").getValue(Long::class.java) 
                                ?: System.currentTimeMillis()
                            
                            val families = parseFamilies(snapshot.child("families"))
                            val models = parseModels(snapshot.child("models"))
                            
                            // If no models from Firebase, use defaults
                            val finalModels = if (models.isEmpty()) getDefaultModels() else models
                            val finalFamilies = if (families.isEmpty()) getDefaultFamilies() else families
                            
                            continuation.resume(
                                ModelCatalogResponse(
                                    families = finalFamilies,
                                    models = finalModels,
                                    version = version,
                                    lastUpdated = lastUpdated
                                )
                            )
                        } catch (e: Exception) {
                            // On any error, return defaults
                            continuation.resume(
                                ModelCatalogResponse(
                                    families = getDefaultFamilies(),
                                    models = getDefaultModels(),
                                    version = 0,
                                    lastUpdated = System.currentTimeMillis()
                                )
                            )
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        // On cancellation, return defaults instead of throwing
                        continuation.resume(
                            ModelCatalogResponse(
                                families = getDefaultFamilies(),
                                models = getDefaultModels(),
                                version = 0,
                                lastUpdated = System.currentTimeMillis()
                            )
                        )
                    }
                }
                
                catalogRef.addListenerForSingleValueEvent(listener)
                
                continuation.invokeOnCancellation {
                    catalogRef.removeEventListener(listener)
                }
            }
        }

    override suspend fun hasNewerVersion(currentVersion: Int): Boolean = 
        try {
            withTimeout(5000L) {
                suspendCancellableCoroutine { continuation ->
                    catalogRef.child("version").addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            val remoteVersion = snapshot.getValue(Int::class.java) ?: 0
                            continuation.resume(remoteVersion > currentVersion)
                        }

                        override fun onCancelled(error: DatabaseError) {
                            continuation.resume(false)
                        }
                    })
                }
            }
        } catch (e: Exception) {
            false
        }

    private fun parseFamilies(snapshot: DataSnapshot): List<ModelFamilyDto> {
        return snapshot.children.mapNotNull { familySnapshot ->
            try {
                ModelFamilyDto(
                    id = familySnapshot.child("id").getValue(String::class.java) ?: return@mapNotNull null,
                    name = familySnapshot.child("name").getValue(String::class.java) ?: "",
                    description = familySnapshot.child("description").getValue(String::class.java) ?: "",
                    developer = familySnapshot.child("developer").getValue(String::class.java) ?: "",
                    license = familySnapshot.child("license").getValue(String::class.java) ?: "",
                    websiteUrl = familySnapshot.child("websiteUrl").getValue(String::class.java)
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun parseModels(snapshot: DataSnapshot): List<LocalModelDto> {
        return snapshot.children.mapNotNull { modelSnapshot ->
            try {
                val performanceSnapshot = modelSnapshot.child("performance")
                val performance = ModelPerformanceDto(
                    tokensPerSecond = performanceSnapshot.child("tokensPerSecond").getValue(Float::class.java) ?: 0f,
                    memoryRequired = performanceSnapshot.child("memoryRequired").getValue(Long::class.java) ?: 0L,
                    cpuIntensive = performanceSnapshot.child("cpuIntensive").getValue(Boolean::class.java) ?: false,
                    gpuAccelerated = performanceSnapshot.child("gpuAccelerated").getValue(Boolean::class.java) ?: false,
                    rating = performanceSnapshot.child("rating").getValue(String::class.java) ?: "BALANCED"
                )

                val useCases = modelSnapshot.child("useCases").children.mapNotNull { 
                    it.getValue(String::class.java) 
                }

                LocalModelDto(
                    id = modelSnapshot.child("id").getValue(String::class.java) ?: return@mapNotNull null,
                    name = modelSnapshot.child("name").getValue(String::class.java) ?: "",
                    description = modelSnapshot.child("description").getValue(String::class.java) ?: "",
                    size = modelSnapshot.child("size").getValue(Long::class.java) ?: 0L,
                    downloadUrl = modelSnapshot.child("downloadUrl").getValue(String::class.java) ?: "",
                    fileName = modelSnapshot.child("fileName").getValue(String::class.java) ?: "",
                    performance = performance,
                    useCases = useCases,
                    quantization = modelSnapshot.child("quantization").getValue(String::class.java) ?: "",
                    parameters = modelSnapshot.child("parameters").getValue(String::class.java) ?: "",
                    contextLength = modelSnapshot.child("contextLength").getValue(Int::class.java) ?: 0,
                    familyId = modelSnapshot.child("familyId").getValue(String::class.java) ?: "",
                    isRecommended = modelSnapshot.child("isRecommended").getValue(Boolean::class.java) ?: false,
                    isEnabled = modelSnapshot.child("isEnabled").getValue(Boolean::class.java) ?: true
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Default model families when Firebase is not configured.
     */
    private fun getDefaultFamilies(): List<ModelFamilyDto> {
        return listOf(
            ModelFamilyDto(
                id = "llama3",
                name = "Llama 3.2",
                description = "Meta's latest open-source LLM family, optimized for mobile and edge devices.",
                developer = "Meta",
                license = "Llama 3.2 Community License",
                websiteUrl = "https://llama.meta.com"
            ),
            ModelFamilyDto(
                id = "qwen",
                name = "Qwen 2.5",
                description = "Alibaba's multilingual model family with excellent efficiency.",
                developer = "Alibaba",
                license = "Apache 2.0",
                websiteUrl = "https://qwenlm.github.io"
            ),
            ModelFamilyDto(
                id = "phi",
                name = "Phi 3.5",
                description = "Microsoft's compact model family with strong reasoning capabilities.",
                developer = "Microsoft",
                license = "MIT",
                websiteUrl = "https://azure.microsoft.com/en-us/products/phi-3"
            )
        )
    }

    /**
     * Default models when Firebase is not configured.
     * These are popular open-source models for local inference.
     */
    private fun getDefaultModels(): List<LocalModelDto> {
        return listOf(
            LocalModelDto(
                id = "llama-3.2-1b-q4",
                name = "Llama 3.2 1B (Q4)",
                description = "Compact and efficient model, perfect for mobile devices. Great for general chat and quick responses.",
                size = 750_000_000L,
                downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
                fileName = "llama-3.2-1b-instruct-q4.gguf",
                performance = ModelPerformanceDto(
                    tokensPerSecond = 25f,
                    memoryRequired = 1_500_000_000L,
                    cpuIntensive = false,
                    gpuAccelerated = true,
                    rating = "FAST"
                ),
                useCases = listOf("CHAT", "GENERAL"),
                quantization = "Q4_K_M",
                parameters = "1B",
                contextLength = 131072,
                familyId = "llama3",
                isRecommended = true,
                isEnabled = true
            ),
            LocalModelDto(
                id = "llama-3.2-3b-q4",
                name = "Llama 3.2 3B (Q4)",
                description = "Balanced model with good reasoning capabilities. Suitable for most tasks on modern phones.",
                size = 2_000_000_000L,
                downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
                fileName = "llama-3.2-3b-instruct-q4.gguf",
                performance = ModelPerformanceDto(
                    tokensPerSecond = 15f,
                    memoryRequired = 3_000_000_000L,
                    cpuIntensive = true,
                    gpuAccelerated = true,
                    rating = "BALANCED"
                ),
                useCases = listOf("CHAT", "REASONING", "CODING"),
                quantization = "Q4_K_M",
                parameters = "3B",
                contextLength = 131072,
                familyId = "llama3",
                isRecommended = false,
                isEnabled = true
            ),
            LocalModelDto(
                id = "qwen-2.5-0.5b-q8",
                name = "Qwen 2.5 0.5B (Q8)",
                description = "Ultra-lightweight model for basic tasks. Very fast responses with minimal memory usage.",
                size = 500_000_000L,
                downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q8_0.gguf",
                fileName = "qwen2.5-0.5b-instruct-q8.gguf",
                performance = ModelPerformanceDto(
                    tokensPerSecond = 40f,
                    memoryRequired = 800_000_000L,
                    cpuIntensive = false,
                    gpuAccelerated = false,
                    rating = "FAST"
                ),
                useCases = listOf("CHAT", "QUICK_ANSWERS"),
                quantization = "Q8_0",
                parameters = "0.5B",
                contextLength = 32768,
                familyId = "qwen",
                isRecommended = true,
                isEnabled = true
            ),
            LocalModelDto(
                id = "phi-3.5-mini-q4",
                name = "Phi 3.5 Mini (Q4)",
                description = "Microsoft's compact powerhouse. Excellent reasoning in a small package.",
                size = 2_300_000_000L,
                downloadUrl = "https://huggingface.co/bartowski/Phi-3.5-mini-instruct-GGUF/resolve/main/Phi-3.5-mini-instruct-Q4_K_M.gguf",
                fileName = "phi-3.5-mini-instruct-q4.gguf",
                performance = ModelPerformanceDto(
                    tokensPerSecond = 12f,
                    memoryRequired = 3_500_000_000L,
                    cpuIntensive = true,
                    gpuAccelerated = true,
                    rating = "QUALITY"
                ),
                useCases = listOf("REASONING", "CODING", "ANALYSIS"),
                quantization = "Q4_K_M",
                parameters = "3.8B",
                contextLength = 128000,
                familyId = "phi",
                isRecommended = false,
                isEnabled = true
            )
        )
    }
    
    companion object {
        private const val FETCH_TIMEOUT_MS = 10_000L // 10 second timeout
    }
}

/**
 * Exception thrown when model catalog operations fail.
 */
class ModelCatalogException(
    message: String, 
    cause: Throwable? = null
) : Exception(message, cause)
