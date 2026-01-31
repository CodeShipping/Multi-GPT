package com.matrix.multigpt.data.datastore

import com.matrix.multigpt.data.model.ApiType
import com.matrix.multigpt.data.model.DynamicTheme
import com.matrix.multigpt.data.model.ThemeMode

interface SettingDataSource {
    suspend fun updateDynamicTheme(theme: DynamicTheme)
    suspend fun updateThemeMode(themeMode: ThemeMode)
    suspend fun updateStatus(apiType: ApiType, status: Boolean)
    suspend fun updateAPIUrl(apiType: ApiType, url: String)
    suspend fun updateToken(apiType: ApiType, token: String)
    suspend fun updateModel(apiType: ApiType, model: String)
    suspend fun updateTemperature(apiType: ApiType, temperature: Float)
    suspend fun updateTopP(apiType: ApiType, topP: Float)
    suspend fun updateSystemPrompt(apiType: ApiType, prompt: String)
    suspend fun getDynamicTheme(): DynamicTheme?
    suspend fun getThemeMode(): ThemeMode?
    suspend fun getStatus(apiType: ApiType): Boolean?
    suspend fun getAPIUrl(apiType: ApiType): String?
    suspend fun getToken(apiType: ApiType): String?
    suspend fun getModel(apiType: ApiType): String?
    suspend fun getTemperature(apiType: ApiType): Float?
    suspend fun getTopP(apiType: ApiType): Float?
    suspend fun getSystemPrompt(apiType: ApiType): String?
    
    // Local AI specific settings
    suspend fun updateLocalTopK(topK: Int)
    suspend fun updateLocalBatchSize(batchSize: Int)
    suspend fun updateLocalContextSize(contextSize: Int)
    suspend fun getLocalTopK(): Int?
    suspend fun getLocalBatchSize(): Int?
    suspend fun getLocalContextSize(): Int?
}
