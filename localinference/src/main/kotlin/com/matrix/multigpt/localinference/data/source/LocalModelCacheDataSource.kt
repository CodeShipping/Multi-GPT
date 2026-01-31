package com.matrix.multigpt.localinference.data.source

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.matrix.multigpt.localinference.data.dto.ModelCatalogResponse
import com.matrix.multigpt.localinference.data.model.LocalModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.modelCatalogDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "model_catalog_cache"
)

/**
 * Local cache data source for model catalog.
 * Stores the catalog in DataStore to minimize Firebase network calls.
 * 
 * Strategy:
 * 1. On first launch, fetch from Firebase and cache locally
 * 2. On subsequent launches, use cached data
 * 3. Periodically check version number (lightweight call)
 * 4. Only fetch full catalog if version has changed
 * 5. Allow manual refresh by user
 */
@Singleton
class LocalModelCacheDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }

    private object Keys {
        val CATALOG_JSON = stringPreferencesKey("catalog_json")
        val CACHED_VERSION = intPreferencesKey("cached_version")
        val LAST_SYNC_TIME = longPreferencesKey("last_sync_time")
        val LAST_VERSION_CHECK_TIME = longPreferencesKey("last_version_check_time")
        val IMPORTED_MODELS_JSON = stringPreferencesKey("imported_models_json")
    }

    companion object {
        // Minimum time between full syncs (24 hours)
        private const val MIN_SYNC_INTERVAL_MS = 24 * 60 * 60 * 1000L
        
        // Minimum time between version checks (1 hour)
        private const val MIN_VERSION_CHECK_INTERVAL_MS = 60 * 60 * 1000L
    }

    /**
     * Get cached catalog if available.
     */
    suspend fun getCachedCatalog(): ModelCatalogResponse? {
        return context.modelCatalogDataStore.data.map { preferences ->
            preferences[Keys.CATALOG_JSON]?.let { jsonString ->
                try {
                    json.decodeFromString<ModelCatalogResponse>(jsonString)
                } catch (e: Exception) {
                    null
                }
            }
        }.first()
    }

    /**
     * Save catalog to local cache.
     */
    suspend fun saveCatalog(catalog: ModelCatalogResponse) {
        context.modelCatalogDataStore.edit { preferences ->
            preferences[Keys.CATALOG_JSON] = json.encodeToString(catalog)
            preferences[Keys.CACHED_VERSION] = catalog.version
            preferences[Keys.LAST_SYNC_TIME] = System.currentTimeMillis()
        }
    }

    /**
     * Get the cached version number.
     */
    suspend fun getCachedVersion(): Int {
        return context.modelCatalogDataStore.data.map { preferences ->
            preferences[Keys.CACHED_VERSION] ?: 0
        }.first()
    }

    /**
     * Get last full sync time.
     */
    suspend fun getLastSyncTime(): Long {
        return context.modelCatalogDataStore.data.map { preferences ->
            preferences[Keys.LAST_SYNC_TIME] ?: 0L
        }.first()
    }

    /**
     * Update last version check time.
     */
    suspend fun updateVersionCheckTime() {
        context.modelCatalogDataStore.edit { preferences ->
            preferences[Keys.LAST_VERSION_CHECK_TIME] = System.currentTimeMillis()
        }
    }

    /**
     * Get last version check time.
     */
    suspend fun getLastVersionCheckTime(): Long {
        return context.modelCatalogDataStore.data.map { preferences ->
            preferences[Keys.LAST_VERSION_CHECK_TIME] ?: 0L
        }.first()
    }

    /**
     * Check if we have any cached data.
     */
    suspend fun hasCachedData(): Boolean {
        return getCachedCatalog() != null
    }

    /**
     * Check if enough time has passed to warrant a version check.
     */
    suspend fun shouldCheckVersion(): Boolean {
        val lastCheck = getLastVersionCheckTime()
        val timeSinceLastCheck = System.currentTimeMillis() - lastCheck
        return timeSinceLastCheck >= MIN_VERSION_CHECK_INTERVAL_MS
    }

    /**
     * Check if enough time has passed to warrant a full sync.
     */
    suspend fun shouldForceSync(): Boolean {
        val lastSync = getLastSyncTime()
        val timeSinceLastSync = System.currentTimeMillis() - lastSync
        return timeSinceLastSync >= MIN_SYNC_INTERVAL_MS
    }

    /**
     * Clear all cached data.
     */
    suspend fun clearCache() {
        context.modelCatalogDataStore.edit { preferences ->
            preferences.clear()
        }
    }

    /**
     * Save imported models list.
     */
    suspend fun saveImportedModels(models: List<LocalModel>) {
        context.modelCatalogDataStore.edit { preferences ->
            preferences[Keys.IMPORTED_MODELS_JSON] = json.encodeToString(models)
        }
    }

    /**
     * Get imported models list.
     */
    suspend fun getImportedModels(): List<LocalModel> {
        return context.modelCatalogDataStore.data.map { preferences ->
            preferences[Keys.IMPORTED_MODELS_JSON]?.let { jsonString ->
                try {
                    json.decodeFromString<List<LocalModel>>(jsonString)
                } catch (e: Exception) {
                    emptyList()
                }
            } ?: emptyList()
        }.first()
    }
}
