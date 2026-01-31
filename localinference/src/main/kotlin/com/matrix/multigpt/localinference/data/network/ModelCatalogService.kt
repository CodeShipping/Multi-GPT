package com.matrix.multigpt.localinference.data.network

import com.matrix.multigpt.localinference.data.dto.ModelCatalogResponse

/**
 * Service interface for fetching model catalog from Firebase.
 */
interface ModelCatalogService {
    
    /**
     * Fetch the complete model catalog from Firebase Realtime Database.
     * @return ModelCatalogResponse containing all models and families
     */
    suspend fun fetchModelCatalog(): ModelCatalogResponse
    
    /**
     * Check if there's a newer version of the catalog available.
     * @param currentVersion The current cached version number
     * @return true if a newer version is available
     */
    suspend fun hasNewerVersion(currentVersion: Int): Boolean
}
