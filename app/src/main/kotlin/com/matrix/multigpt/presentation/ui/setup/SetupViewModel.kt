package com.matrix.multigpt.presentation.ui.setup

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.matrix.multigpt.data.ModelConstants.anthropicModels
import com.matrix.multigpt.data.ModelConstants.bedrockModels
import com.matrix.multigpt.data.ModelConstants.googleModels
import com.matrix.multigpt.data.ModelConstants.groqModels
import com.matrix.multigpt.data.ModelConstants.ollamaModels
import com.matrix.multigpt.data.ModelConstants.openaiModels
import com.matrix.multigpt.data.ModelConstants
import com.matrix.multigpt.data.dto.ModelFetchResult
import com.matrix.multigpt.data.dto.ModelInfo
import com.matrix.multigpt.data.dto.Platform
import com.matrix.multigpt.data.model.ApiType
import com.matrix.multigpt.data.network.ModelFetchService
import com.matrix.multigpt.data.repository.SettingRepository
import com.matrix.multigpt.presentation.common.Route
import com.matrix.multigpt.util.FirebaseEvents
import com.matrix.multigpt.util.FirebaseManager
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val settingRepository: SettingRepository,
    private val modelFetchService: ModelFetchService,
    private val firebaseManager: FirebaseManager
) : ViewModel() {

    private val _platformState = MutableStateFlow(
        listOf(
            Platform(ApiType.OPENAI),
            Platform(ApiType.GROQ),
            Platform(ApiType.BEDROCK),
            Platform(ApiType.ANTHROPIC),
            Platform(ApiType.GOOGLE),
            Platform(ApiType.OLLAMA),
            Platform(ApiType.LOCAL)
        )
    )
    val platformState: StateFlow<List<Platform>> = _platformState.asStateFlow()

    private val _modelFetchState = MutableStateFlow<Map<ApiType, ModelFetchResult>>(emptyMap())
    val modelFetchState: StateFlow<Map<ApiType, ModelFetchResult>> = _modelFetchState.asStateFlow()

    private val _fetchedModels = MutableStateFlow<Map<ApiType, List<ModelInfo>>>(emptyMap())
    val fetchedModels: StateFlow<Map<ApiType, List<ModelInfo>>> = _fetchedModels.asStateFlow()

    fun updateAPIAddress(platform: Platform, address: String) {
        val index = _platformState.value.indexOf(platform)

        if (index >= 0) {
            _platformState.update {
                it.mapIndexed { i, p ->
                    if (index == i) {
                        p.copy(apiUrl = address.trim())
                    } else {
                        p
                    }
                }
            }
        }
    }

    fun updateCheckedState(platform: Platform) {
        val index = _platformState.value.indexOf(platform)

        if (index >= 0) {
            // Log platform selection to Firebase
            firebaseManager.logUserAction(FirebaseEvents.PLATFORM_SELECTED, platform.name.toString())
            
            _platformState.update {
                it.mapIndexed { i, p ->
                    if (index == i) {
                        p.copy(selected = p.selected.not())
                    } else {
                        p
                    }
                }
            }
        }
    }

    fun updateToken(platform: Platform, token: String) {
        val index = _platformState.value.indexOf(platform)

        if (index >= 0) {
            _platformState.update {
                it.mapIndexed { i, p ->
                    if (index == i) {
                        p.copy(token = token.ifBlank { null })
                    } else {
                        p
                    }
                }
            }
        }
    }

    fun updateModel(apiType: ApiType, model: String) {
        val index = _platformState.value.indexOfFirst { it.name == apiType }

        if (index >= 0) {
            // Log model selection to Firebase
            firebaseManager.logUserAction(FirebaseEvents.MODEL_SELECTED, "$apiType:$model")
            
            _platformState.update {
                it.mapIndexed { i, p ->
                    if (index == i) {
                        p.copy(model = model)
                    } else {
                        p
                    }
                }
            }
        }
    }

    fun savePlatformState(onComplete: (() -> Unit)? = null) {
        val updatedPlatforms = _platformState.value.map { p ->
            p.copy(enabled = p.selected, selected = false)
        }
        
        _platformState.update { updatedPlatforms }
        
        viewModelScope.launch {
            try {
                // Log setup completion to Firebase
                val enabledPlatforms = updatedPlatforms.filter { it.enabled }.map { it.name.toString() }
                firebaseManager.logUserAction(FirebaseEvents.SETUP_COMPLETE, enabledPlatforms.joinToString(","))

                Log.d("SetupViewModel", "Saving platforms: ${updatedPlatforms.filter { it.enabled }.map { "${it.name}:${it.token != null}" }}")

                // Wait for the repository update to complete
                settingRepository.updatePlatforms(updatedPlatforms)

                Log.d("SetupViewModel", "Platforms saved, verifying...")

                // Verify the save was successful by reading back
                val savedPlatforms = settingRepository.fetchPlatforms()
                Log.d("SetupViewModel", "Verified platforms: ${savedPlatforms.filter { it.enabled }.map { "${it.name}:${it.token != null}" }}")

                // Call completion callback after DataStore write completes
                onComplete?.invoke()
            } catch (e: Exception) {
                Log.e("SetupViewModel", "Error saving platforms", e)
                // Handle error case and still allow navigation
                onComplete?.invoke()
            }
        }
    }

    fun getNextSetupRoute(currentRoute: String?): String {
        val steps = listOf(
            Route.SELECT_PLATFORM,
            Route.TOKEN_INPUT,
            Route.OPENAI_MODEL_SELECT,
            Route.GROQ_MODEL_SELECT,
            Route.BEDROCK_MODEL_SELECT,
            Route.ANTHROPIC_MODEL_SELECT,
            Route.GOOGLE_MODEL_SELECT,
            Route.OLLAMA_MODEL_SELECT,
            Route.OLLAMA_API_ADDRESS,
            Route.SETUP_COMPLETE
        )
        val commonSteps = mutableSetOf(Route.SELECT_PLATFORM, Route.TOKEN_INPUT, Route.SETUP_COMPLETE)
        val platformStep = mapOf(
            Route.OPENAI_MODEL_SELECT to ApiType.OPENAI,
            Route.ANTHROPIC_MODEL_SELECT to ApiType.ANTHROPIC,
            Route.GOOGLE_MODEL_SELECT to ApiType.GOOGLE,
            Route.GROQ_MODEL_SELECT to ApiType.GROQ,
            Route.OLLAMA_MODEL_SELECT to ApiType.OLLAMA,
            Route.BEDROCK_MODEL_SELECT to ApiType.BEDROCK,
            Route.OLLAMA_API_ADDRESS to ApiType.OLLAMA
        )

        val currentIndex = steps.indexOfFirst { it == currentRoute }
        val enabledPlatform = platformState.value.filter { it.selected }.map { it.name }.toSet()

        if (enabledPlatform.size == 1 && ApiType.OLLAMA in enabledPlatform) {
            // Skip API Token input page
            commonSteps.remove(Route.TOKEN_INPUT)
        }

        val remainingSteps = steps.filterIndexed { index, setupStep ->
            index > currentIndex &&
                (setupStep in commonSteps || platformStep[setupStep] in enabledPlatform)
        }

        if (remainingSteps.isEmpty()) {
            // Setup Complete
            return Route.CHAT_LIST
        }

        return remainingSteps.first()
    }

    fun setDefaultModel(apiType: ApiType, defaultModelIndex: Int): String {
        val modelList = when (apiType) {
            ApiType.OPENAI -> openaiModels
            ApiType.ANTHROPIC -> anthropicModels
            ApiType.GOOGLE -> googleModels
            ApiType.GROQ -> groqModels
            ApiType.OLLAMA -> ollamaModels
            ApiType.BEDROCK -> bedrockModels
            ApiType.LOCAL -> linkedSetOf<String>() // Local models are managed via Local AI settings
        }.toList()

        if (modelList.size <= defaultModelIndex) {
            return ""
        }

        val model = modelList[defaultModelIndex]
        updateModel(apiType, model)

        return model
    }

    fun fetchModelsForPlatform(apiType: ApiType) {
        val platform = _platformState.value.find { it.name == apiType } ?: return
        val apiUrl = platform.apiUrl.ifBlank { ModelConstants.getDefaultAPIUrl(apiType) }
        val apiKey = platform.token

        viewModelScope.launch {
            _modelFetchState.update { it + (apiType to ModelFetchResult.Loading) }

            val result = modelFetchService.fetchModels(apiType, apiUrl, apiKey)

            _modelFetchState.update { it + (apiType to result) }

            if (result is ModelFetchResult.Success) {
                _fetchedModels.update { it + (apiType to result.models) }
            }
        }
    }

    fun getFallbackModels(apiType: ApiType): List<String> {
        return when (apiType) {
            ApiType.OPENAI -> openaiModels.toList()
            ApiType.ANTHROPIC -> anthropicModels.toList()
            ApiType.GOOGLE -> googleModels.toList()
            ApiType.GROQ -> groqModels.toList()
            ApiType.OLLAMA -> ollamaModels.toList()
            ApiType.BEDROCK -> bedrockModels.toList()
            ApiType.LOCAL -> emptyList() // Local models are managed via Local AI settings
        }
    }
}
