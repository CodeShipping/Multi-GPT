package com.matrix.multigpt.localinference.data.repository

import android.net.Uri
import com.matrix.multigpt.localinference.data.model.LocalModel
import com.matrix.multigpt.localinference.data.model.LocalModelState
import com.matrix.multigpt.localinference.data.model.ModelFamily
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for local model management.
 */
interface LocalModelRepository {

    /**
     * Get all available models from catalog.
     * Uses cached data if available, fetches from Firebase only when necessary.
     */
    suspend fun getModels(): List<LocalModel>

    /**
     * Get all model families.
     */
    suspend fun getModelFamilies(): List<ModelFamily>

    /**
     * Get models by family ID.
     */
    suspend fun getModelsByFamily(familyId: String): List<LocalModel>

    /**
     * Get recommended models for first-time users.
     */
    suspend fun getRecommendedModels(): List<LocalModel>

    /**
     * Get a specific model by ID.
     */
    suspend fun getModelById(id: String): LocalModel?

    /**
     * Get family by ID.
     */
    suspend fun getFamilyById(id: String): ModelFamily?

    /**
     * Force refresh from Firebase (ignores cache).
     */
    suspend fun forceRefresh()

    /**
     * Check if catalog needs update and update if necessary.
     * This is a lightweight check that only fetches version number.
     */
    suspend fun checkAndUpdateIfNeeded()

    /**
     * Get the download state of all models.
     */
    fun getModelStates(): Flow<Map<String, LocalModelState>>

    /**
     * Get download state for a specific model.
     */
    fun getModelState(modelId: String): Flow<LocalModelState?>

    /**
     * Download a model.
     */
    suspend fun downloadModel(modelId: String)

    /**
     * Pause model download.
     */
    suspend fun pauseDownload(modelId: String)

    /**
     * Resume paused download.
     */
    suspend fun resumeDownload(modelId: String)

    /**
     * Cancel and delete partial download.
     */
    suspend fun cancelDownload(modelId: String)

    /**
     * Delete a downloaded model.
     */
    suspend fun deleteModel(modelId: String)

    /**
     * Get the local path of a downloaded model.
     */
    suspend fun getModelPath(modelId: String): String?

    /**
     * Check if a model is downloaded.
     */
    suspend fun isModelDownloaded(modelId: String): Boolean

    /**
     * Import GGUF model files from external storage.
     * @param uris List of content URIs pointing to GGUF files
     * @param onProgress Callback for import progress (current, total, fileName)
     * @return List of imported model IDs
     */
    suspend fun importModels(
        uris: List<Uri>,
        onProgress: (current: Int, total: Int, fileName: String) -> Unit = { _, _, _ -> }
    ): List<String>

    /**
     * Get all imported (user-added) models.
     */
    suspend fun getImportedModels(): List<LocalModel>
}
