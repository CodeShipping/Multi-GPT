package com.matrix.multigpt.localinference.data.repository

import android.util.Log
import com.matrix.multigpt.localinference.data.mapper.ModelMapper
import com.matrix.multigpt.localinference.data.model.LocalModel
import com.matrix.multigpt.localinference.data.model.LocalModelState
import com.matrix.multigpt.localinference.data.model.ModelFamily
import com.matrix.multigpt.localinference.data.network.ModelCatalogService
import com.matrix.multigpt.localinference.data.source.LocalModelCacheDataSource
import com.matrix.multigpt.localinference.data.source.ModelDownloadManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository implementation for local model management.
 * Implements smart caching to minimize Firebase network calls.
 * 
 * Caching Strategy:
 * 1. On first launch: fetch from Firebase, cache locally
 * 2. On subsequent launches: use cache immediately
 * 3. Background check: only fetch version number (lightweight)
 * 4. Full sync: only when version changed or forced refresh
 */
@Singleton
class LocalModelRepositoryImpl @Inject constructor(
    private val catalogService: ModelCatalogService,
    private val cacheDataSource: LocalModelCacheDataSource,
    private val downloadManager: ModelDownloadManager
) : LocalModelRepository {

    // Cached data in memory
    private var cachedModels: List<LocalModel> = emptyList()
    private var cachedFamilies: List<ModelFamily> = emptyList()

    // Scope for background operations
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun getModels(): List<LocalModel> {
        Log.d(TAG, "getModels() called, cachedModels.size=${cachedModels.size}")
        
        if (cachedModels.isNotEmpty()) {
            // Check for updates in background if needed
            try {
                checkAndUpdateIfNeeded()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to check for updates", e)
            }
            return cachedModels
        }

        // Try to load from local cache first
        try {
            val cachedCatalog = cacheDataSource.getCachedCatalog()
            if (cachedCatalog != null) {
                Log.d(TAG, "Found cached catalog with ${cachedCatalog.models.size} models")
                val (families, models) = ModelMapper.mapCatalogResponse(cachedCatalog)
                cachedFamilies = families
                cachedModels = models
                
                // Check for updates in background
                try {
                    checkAndUpdateIfNeeded()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to check for updates", e)
                }
                return cachedModels
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load from cache", e)
        }

        // No cache - fetch from Firebase (with fallback)
        return try {
            fetchFromFirebase()
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Firebase fetch timed out, using default models", e)
            getDefaultModels()
        } catch (e: CancellationException) {
            throw e // Don't catch regular cancellation
        } catch (e: Exception) {
            Log.e(TAG, "Firebase fetch failed, using default models", e)
            getDefaultModels()
        }
    }

    override suspend fun getModelFamilies(): List<ModelFamily> {
        if (cachedFamilies.isEmpty()) {
            getModels() // This will populate both caches
        }
        return cachedFamilies
    }

    override suspend fun getModelsByFamily(familyId: String): List<LocalModel> {
        return getModels().filter { it.familyId == familyId }
    }

    override suspend fun getRecommendedModels(): List<LocalModel> {
        return getModels().filter { it.isRecommended }
    }

    override suspend fun getModelById(id: String): LocalModel? {
        return getModels().find { it.id == id }
    }

    override suspend fun getFamilyById(id: String): ModelFamily? {
        return getModelFamilies().find { it.id == id }
    }

    override suspend fun forceRefresh() {
        fetchFromFirebase()
    }

    override suspend fun checkAndUpdateIfNeeded() {
        try {
            // Only check version if enough time has passed
            if (!cacheDataSource.shouldCheckVersion()) {
                return
            }

            cacheDataSource.updateVersionCheckTime()
            
            val currentVersion = cacheDataSource.getCachedVersion()
            val hasNewer = catalogService.hasNewerVersion(currentVersion)
            
            if (hasNewer) {
                fetchFromFirebase()
            }
        } catch (e: Exception) {
            // Silently fail - we have cached data
        }
    }

    /**
     * Fetch catalog from Firebase and update cache.
     */
    private suspend fun fetchFromFirebase(): List<LocalModel> {
        Log.d(TAG, "fetchFromFirebase() called")
        return try {
            val response = catalogService.fetchModelCatalog()
            Log.d(TAG, "Got response with ${response.models.size} models")
            
            try {
                cacheDataSource.saveCatalog(response)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to cache catalog", e)
            }
            
            val (families, models) = ModelMapper.mapCatalogResponse(response)
            cachedFamilies = families
            cachedModels = models
            
            Log.d(TAG, "Mapped ${models.size} models")
            cachedModels
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Firebase fetch timed out", e)
            // If fetch times out and we have cache, return cached data
            tryLoadFromCacheOrDefault()
        } catch (e: CancellationException) {
            throw e // Don't catch regular cancellation
        } catch (e: Exception) {
            Log.e(TAG, "Firebase fetch failed", e)
            // If fetch fails and we have cache, return cached data
            tryLoadFromCacheOrDefault()
        }
    }
    
    private suspend fun tryLoadFromCacheOrDefault(): List<LocalModel> {
        try {
            val cachedCatalog = cacheDataSource.getCachedCatalog()
            if (cachedCatalog != null && cachedCatalog.models.isNotEmpty()) {
                val (families, models) = ModelMapper.mapCatalogResponse(cachedCatalog)
                cachedFamilies = families
                cachedModels = models
                return cachedModels
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load from cache", e)
        }
        
        // Return default models as fallback
        return getDefaultModels()
    }
    
    /**
     * Default models when Firebase/cache both unavailable.
     */
    private fun getDefaultModels(): List<LocalModel> {
        Log.d(TAG, "Using default models")
        cachedFamilies = listOf(
            com.matrix.multigpt.localinference.data.model.ModelFamily(
                id = "llama3",
                name = "Llama 3.2",
                description = "Meta's latest open-source LLM family, optimized for mobile and edge devices.",
                developer = "Meta",
                license = "Llama 3.2 Community License",
                websiteUrl = "https://llama.meta.com"
            ),
            com.matrix.multigpt.localinference.data.model.ModelFamily(
                id = "qwen",
                name = "Qwen 2.5",
                description = "Alibaba's multilingual model family with excellent efficiency.",
                developer = "Alibaba",
                license = "Apache 2.0",
                websiteUrl = "https://qwenlm.github.io"
            ),
            com.matrix.multigpt.localinference.data.model.ModelFamily(
                id = "phi",
                name = "Phi 3.5",
                description = "Microsoft's compact model family with strong reasoning capabilities.",
                developer = "Microsoft",
                license = "MIT",
                websiteUrl = "https://azure.microsoft.com/en-us/products/phi-3"
            )
        )
        
        cachedModels = listOf(
            LocalModel(
                id = "llama-3.2-1b-q4",
                name = "Llama 3.2 1B (Q4)",
                description = "Compact and efficient model, perfect for mobile devices. Great for general chat and quick responses.",
                size = 750_000_000L,
                downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
                fileName = "llama-3.2-1b-instruct-q4.gguf",
                performance = com.matrix.multigpt.localinference.data.model.ModelPerformance(
                    tokensPerSecond = 25f,
                    memoryRequired = 1_500_000_000L,
                    cpuIntensive = false,
                    gpuAccelerated = true,
                    rating = com.matrix.multigpt.localinference.data.model.PerformanceRating.FAST
                ),
                useCases = listOf(
                    com.matrix.multigpt.localinference.data.model.UseCase.CHAT,
                    com.matrix.multigpt.localinference.data.model.UseCase.GENERAL
                ),
                quantization = "Q4_K_M",
                parameters = "1B",
                contextLength = 131072,
                familyId = "llama3",
                isRecommended = true
            ),
            LocalModel(
                id = "qwen-2.5-0.5b-q8",
                name = "Qwen 2.5 0.5B (Q8)",
                description = "Ultra-lightweight model for basic tasks. Very fast responses with minimal memory usage.",
                size = 500_000_000L,
                downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q8_0.gguf",
                fileName = "qwen2.5-0.5b-instruct-q8.gguf",
                performance = com.matrix.multigpt.localinference.data.model.ModelPerformance(
                    tokensPerSecond = 40f,
                    memoryRequired = 800_000_000L,
                    cpuIntensive = false,
                    gpuAccelerated = false,
                    rating = com.matrix.multigpt.localinference.data.model.PerformanceRating.FAST
                ),
                useCases = listOf(
                    com.matrix.multigpt.localinference.data.model.UseCase.CHAT,
                    com.matrix.multigpt.localinference.data.model.UseCase.GENERAL
                ),
                quantization = "Q8_0",
                parameters = "0.5B",
                contextLength = 32768,
                familyId = "qwen",
                isRecommended = true
            ),
            LocalModel(
                id = "llama-3.2-3b-q4",
                name = "Llama 3.2 3B (Q4)",
                description = "Balanced model with good reasoning capabilities. Suitable for most tasks on modern phones.",
                size = 2_000_000_000L,
                downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
                fileName = "llama-3.2-3b-instruct-q4.gguf",
                performance = com.matrix.multigpt.localinference.data.model.ModelPerformance(
                    tokensPerSecond = 15f,
                    memoryRequired = 3_000_000_000L,
                    cpuIntensive = true,
                    gpuAccelerated = true,
                    rating = com.matrix.multigpt.localinference.data.model.PerformanceRating.BALANCED
                ),
                useCases = listOf(
                    com.matrix.multigpt.localinference.data.model.UseCase.CHAT,
                    com.matrix.multigpt.localinference.data.model.UseCase.CODING,
                    com.matrix.multigpt.localinference.data.model.UseCase.GENERAL
                ),
                quantization = "Q4_K_M",
                parameters = "3B",
                contextLength = 131072,
                familyId = "llama3",
                isRecommended = false
            ),
            LocalModel(
                id = "phi-3.5-mini-q4",
                name = "Phi 3.5 Mini (Q4)",
                description = "Microsoft's compact powerhouse. Excellent reasoning in a small package.",
                size = 2_300_000_000L,
                downloadUrl = "https://huggingface.co/bartowski/Phi-3.5-mini-instruct-GGUF/resolve/main/Phi-3.5-mini-instruct-Q4_K_M.gguf",
                fileName = "phi-3.5-mini-instruct-q4.gguf",
                performance = com.matrix.multigpt.localinference.data.model.ModelPerformance(
                    tokensPerSecond = 12f,
                    memoryRequired = 3_500_000_000L,
                    cpuIntensive = true,
                    gpuAccelerated = true,
                    rating = com.matrix.multigpt.localinference.data.model.PerformanceRating.QUALITY
                ),
                useCases = listOf(
                    com.matrix.multigpt.localinference.data.model.UseCase.CODING,
                    com.matrix.multigpt.localinference.data.model.UseCase.GENERAL
                ),
                quantization = "Q4_K_M",
                parameters = "3.8B",
                contextLength = 128000,
                familyId = "phi",
                isRecommended = false
            )
        )
        
        return cachedModels
    }

    // Download management delegated to ModelDownloadManager

    override fun getModelStates(): Flow<Map<String, LocalModelState>> {
        return downloadManager.modelStates
    }

    override fun getModelState(modelId: String): Flow<LocalModelState?> {
        return downloadManager.modelStates.map { it[modelId] }
    }

    override suspend fun downloadModel(modelId: String) {
        val model = getModelById(modelId) ?: throw IllegalArgumentException("Model not found: $modelId")
        downloadManager.downloadModel(model, repositoryScope)
    }

    override suspend fun pauseDownload(modelId: String) {
        downloadManager.pauseDownload(modelId)
    }

    override suspend fun resumeDownload(modelId: String) {
        val model = getModelById(modelId) ?: throw IllegalArgumentException("Model not found: $modelId")
        downloadManager.resumeDownload(model, repositoryScope)
    }

    override suspend fun cancelDownload(modelId: String) {
        downloadManager.cancelDownload(modelId)
    }

    override suspend fun deleteModel(modelId: String) {
        downloadManager.deleteModel(modelId)
    }

    override suspend fun getModelPath(modelId: String): String? {
        return downloadManager.getModelPath(modelId)
    }

    override suspend fun isModelDownloaded(modelId: String): Boolean {
        return downloadManager.isModelDownloaded(modelId)
    }
    
    companion object {
        private const val TAG = "LocalModelRepository"
    }
}
