package com.matrix.multigpt.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.matrix.multigpt.data.model.ApiType
import com.matrix.multigpt.data.model.DynamicTheme
import com.matrix.multigpt.data.model.ThemeMode
import com.matrix.multigpt.util.SecureCredentialManager
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class SettingDataSourceImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val secureCredentialManager: SecureCredentialManager
) : SettingDataSource {
    private val apiStatusMap = mapOf(
        ApiType.OPENAI to booleanPreferencesKey("openai_status"),
        ApiType.ANTHROPIC to booleanPreferencesKey("anthropic_status"),
        ApiType.GOOGLE to booleanPreferencesKey("google_status"),
        ApiType.GROQ to booleanPreferencesKey("groq_status"),
        ApiType.OLLAMA to booleanPreferencesKey("ollama_status"),
        ApiType.BEDROCK to booleanPreferencesKey("bedrock_status"),
        ApiType.LOCAL to booleanPreferencesKey("local_status")
    )
    private val apiUrlMap = mapOf(
        ApiType.OPENAI to stringPreferencesKey("openai_url"),
        ApiType.ANTHROPIC to stringPreferencesKey("anthropic_url"),
        ApiType.GOOGLE to stringPreferencesKey("google_url"),
        ApiType.GROQ to stringPreferencesKey("groq_url"),
        ApiType.OLLAMA to stringPreferencesKey("ollama_url"),
        ApiType.BEDROCK to stringPreferencesKey("bedrock_url"),
        ApiType.LOCAL to stringPreferencesKey("local_url")
    )
    private val apiTokenMap = mapOf(
        ApiType.OPENAI to stringPreferencesKey("openai_token"),
        ApiType.ANTHROPIC to stringPreferencesKey("anthropic_token"),
        ApiType.GOOGLE to stringPreferencesKey("google_ai_platform_token"),
        ApiType.GROQ to stringPreferencesKey("groq_token"),
        ApiType.OLLAMA to stringPreferencesKey("ollama_token"),
        ApiType.LOCAL to stringPreferencesKey("local_token")
        // BEDROCK credentials now handled by SecureCredentialManager
    )
    private val apiModelMap = mapOf(
        ApiType.OPENAI to stringPreferencesKey("openai_model"),
        ApiType.ANTHROPIC to stringPreferencesKey("anthropic_model"),
        ApiType.GOOGLE to stringPreferencesKey("google_model"),
        ApiType.GROQ to stringPreferencesKey("groq_model"),
        ApiType.OLLAMA to stringPreferencesKey("ollama_model"),
        ApiType.BEDROCK to stringPreferencesKey("bedrock_model"),
        ApiType.LOCAL to stringPreferencesKey("local_model")
    )
    private val apiTemperatureMap = mapOf(
        ApiType.OPENAI to floatPreferencesKey("openai_temperature"),
        ApiType.ANTHROPIC to floatPreferencesKey("anthropic_temperature"),
        ApiType.GOOGLE to floatPreferencesKey("google_temperature"),
        ApiType.GROQ to floatPreferencesKey("groq_temperature"),
        ApiType.OLLAMA to floatPreferencesKey("ollama_temperature"),
        ApiType.BEDROCK to floatPreferencesKey("bedrock_temperature"),
        ApiType.LOCAL to floatPreferencesKey("local_temperature")
    )
    private val apiTopPMap = mapOf(
        ApiType.OPENAI to floatPreferencesKey("openai_top_p"),
        ApiType.ANTHROPIC to floatPreferencesKey("anthropic_top_p"),
        ApiType.GOOGLE to floatPreferencesKey("google_top_p"),
        ApiType.GROQ to floatPreferencesKey("groq_top_p"),
        ApiType.OLLAMA to floatPreferencesKey("ollama_top_p"),
        ApiType.BEDROCK to floatPreferencesKey("bedrock_top_p"),
        ApiType.LOCAL to floatPreferencesKey("local_top_p")
    )
    private val apiSystemPromptMap = mapOf(
        ApiType.OPENAI to stringPreferencesKey("openai_system_prompt"),
        ApiType.ANTHROPIC to stringPreferencesKey("anthropic_system_prompt"),
        ApiType.GOOGLE to stringPreferencesKey("google_system_prompt"),
        ApiType.GROQ to stringPreferencesKey("groq_system_prompt"),
        ApiType.OLLAMA to stringPreferencesKey("ollama_system_prompt"),
        ApiType.BEDROCK to stringPreferencesKey("bedrock_system_prompt"),
        ApiType.LOCAL to stringPreferencesKey("local_system_prompt")
    )
    private val dynamicThemeKey = intPreferencesKey("dynamic_mode")
    private val themeModeKey = intPreferencesKey("theme_mode")
    
    // Local AI specific keys
    private val localTopKKey = intPreferencesKey("local_top_k")
    private val localBatchSizeKey = intPreferencesKey("local_batch_size")
    private val localContextSizeKey = intPreferencesKey("local_context_size")

    override suspend fun updateDynamicTheme(theme: DynamicTheme) {
        dataStore.edit { pref ->
            pref[dynamicThemeKey] = theme.ordinal
        }
    }

    override suspend fun updateThemeMode(themeMode: ThemeMode) {
        dataStore.edit { pref ->
            pref[themeModeKey] = themeMode.ordinal
        }
    }

    override suspend fun updateStatus(apiType: ApiType, status: Boolean) {
        val key = apiStatusMap[apiType] ?: return
        dataStore.edit { pref ->
            pref[key] = status
        }
    }

    override suspend fun updateAPIUrl(apiType: ApiType, url: String) {
        val key = apiUrlMap[apiType] ?: return
        dataStore.edit { pref ->
            pref[key] = url
        }
    }

    override suspend fun updateToken(apiType: ApiType, token: String) {
        if (apiType == ApiType.BEDROCK) {
            // Use secure storage for sensitive AWS credentials
            secureCredentialManager.storeCredentials("bedrock_credentials", token)
        } else {
            val key = apiTokenMap[apiType] ?: return
            dataStore.edit { pref ->
                pref[key] = token
            }
        }
    }

    override suspend fun updateModel(apiType: ApiType, model: String) {
        val key = apiModelMap[apiType] ?: return
        dataStore.edit { pref ->
            pref[key] = model
        }
    }

    override suspend fun updateTemperature(apiType: ApiType, temperature: Float) {
        val key = apiTemperatureMap[apiType] ?: return
        dataStore.edit { pref ->
            pref[key] = temperature
        }
    }

    override suspend fun updateTopP(apiType: ApiType, topP: Float) {
        val key = apiTopPMap[apiType] ?: return
        dataStore.edit { pref ->
            pref[key] = topP
        }
    }

    override suspend fun updateSystemPrompt(apiType: ApiType, prompt: String) {
        val key = apiSystemPromptMap[apiType] ?: return
        dataStore.edit { pref ->
            pref[key] = prompt
        }
    }

    override suspend fun getDynamicTheme(): DynamicTheme? {
        val mode = dataStore.data.map { pref ->
            pref[dynamicThemeKey]
        }.first() ?: return null

        return DynamicTheme.getByValue(mode)
    }

    override suspend fun getThemeMode(): ThemeMode? {
        val mode = dataStore.data.map { pref ->
            pref[themeModeKey]
        }.first() ?: return null

        return ThemeMode.getByValue(mode)
    }

    override suspend fun getStatus(apiType: ApiType): Boolean? {
        val key = apiStatusMap[apiType] ?: return null
        return dataStore.data.map { pref ->
            pref[key]
        }.first()
    }

    override suspend fun getAPIUrl(apiType: ApiType): String? {
        val key = apiUrlMap[apiType] ?: return null
        return dataStore.data.map { pref ->
            pref[key]
        }.first()
    }

    override suspend fun getToken(apiType: ApiType): String? {
        return if (apiType == ApiType.BEDROCK) {
            // Retrieve AWS credentials from secure storage
            secureCredentialManager.retrieveCredentials("bedrock_credentials")
        } else {
            val key = apiTokenMap[apiType] ?: return null
            dataStore.data.map { pref ->
                pref[key]
            }.first()
        }
    }

    override suspend fun getModel(apiType: ApiType): String? {
        val key = apiModelMap[apiType] ?: return null
        return dataStore.data.map { pref ->
            pref[key]
        }.first()
    }

    override suspend fun getTemperature(apiType: ApiType): Float? {
        val key = apiTemperatureMap[apiType] ?: return null
        return dataStore.data.map { pref ->
            pref[key]
        }.first()
    }

    override suspend fun getTopP(apiType: ApiType): Float? {
        val key = apiTopPMap[apiType] ?: return null
        return dataStore.data.map { pref ->
            pref[key]
        }.first()
    }

    override suspend fun getSystemPrompt(apiType: ApiType): String? {
        val key = apiSystemPromptMap[apiType] ?: return null
        return dataStore.data.map { pref ->
            pref[key]
        }.first()
    }
    
    // Local AI specific settings
    override suspend fun updateLocalTopK(topK: Int) {
        dataStore.edit { pref ->
            pref[localTopKKey] = topK
        }
    }
    
    override suspend fun updateLocalBatchSize(batchSize: Int) {
        dataStore.edit { pref ->
            pref[localBatchSizeKey] = batchSize
        }
    }
    
    override suspend fun updateLocalContextSize(contextSize: Int) {
        dataStore.edit { pref ->
            pref[localContextSizeKey] = contextSize
        }
    }
    
    override suspend fun getLocalTopK(): Int? {
        return dataStore.data.map { pref ->
            pref[localTopKKey]
        }.first()
    }
    
    override suspend fun getLocalBatchSize(): Int? {
        return dataStore.data.map { pref ->
            pref[localBatchSizeKey]
        }.first()
    }
    
    override suspend fun getLocalContextSize(): Int? {
        return dataStore.data.map { pref ->
            pref[localContextSizeKey]
        }.first()
    }
}
