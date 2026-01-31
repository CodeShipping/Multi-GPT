package com.matrix.multigpt.presentation.ui.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.matrix.multigpt.data.ModelConstants
import com.matrix.multigpt.data.dto.ModelFetchResult
import com.matrix.multigpt.data.dto.ModelInfo
import com.matrix.multigpt.data.dto.Platform
import com.matrix.multigpt.data.model.ApiType
import com.matrix.multigpt.data.network.ModelFetchService
import com.matrix.multigpt.data.repository.SettingRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SettingViewModel @Inject constructor(
    private val settingRepository: SettingRepository,
    private val modelFetchService: ModelFetchService
) : ViewModel() {

    private val _platformState = MutableStateFlow(listOf<Platform>())
    val platformState: StateFlow<List<Platform>> = _platformState.asStateFlow()

    private val _dialogState = MutableStateFlow(DialogState())
    val dialogState: StateFlow<DialogState> = _dialogState.asStateFlow()

    private val _modelFetchState = MutableStateFlow<Map<ApiType, ModelFetchResult>>(emptyMap())
    val modelFetchState: StateFlow<Map<ApiType, ModelFetchResult>> = _modelFetchState.asStateFlow()

    private val _fetchedModels = MutableStateFlow<Map<ApiType, List<ModelInfo>>>(emptyMap())
    val fetchedModels: StateFlow<Map<ApiType, List<ModelInfo>>> = _fetchedModels.asStateFlow()

    init {
        fetchPlatformStatus()
    }

    fun toggleAPI(apiType: ApiType) {
        val index = _platformState.value.indexOfFirst { it.name == apiType }

        if (index >= 0) {
            _platformState.update {
                it.mapIndexed { i, p ->
                    if (index == i) {
                        p.copy(enabled = p.enabled.not())
                    } else {
                        p
                    }
                }
            }
            viewModelScope.launch {
                settingRepository.updatePlatforms(_platformState.value)
            }
        }
    }

    fun savePlatformSettings() {
        viewModelScope.launch {
            settingRepository.updatePlatforms(_platformState.value)
        }
    }

    fun updateURL(apiType: ApiType, url: String) {
        val index = _platformState.value.indexOfFirst { it.name == apiType }

        if (index >= 0) {
            _platformState.update {
                it.mapIndexed { i, p ->
                    if (index == i && url.isNotBlank()) {
                        p.copy(apiUrl = url)
                    } else {
                        p
                    }
                }
            }
        }
    }

    fun updateToken(apiType: ApiType, token: String) {
        val index = _platformState.value.indexOfFirst { it.name == apiType }

        if (index >= 0) {
            _platformState.update {
                it.mapIndexed { i, p ->
                    if (index == i && token.isNotBlank()) {
                        p.copy(token = token)
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

    fun updateTemperature(apiType: ApiType, temperature: Float) {
        val index = _platformState.value.indexOfFirst { it.name == apiType }
        val modifiedTemperature = when (apiType) {
            ApiType.ANTHROPIC -> temperature.coerceIn(0F, 1F)
            else -> temperature.coerceIn(0F, 2F)
        }

        if (index >= 0) {
            _platformState.update {
                it.mapIndexed { i, p ->
                    if (index == i) {
                        p.copy(temperature = modifiedTemperature)
                    } else {
                        p
                    }
                }
            }
        }
    }

    fun updateTopP(apiType: ApiType, topP: Float) {
        val index = _platformState.value.indexOfFirst { it.name == apiType }
        val modifiedTopP = topP.coerceIn(0.1F, 1F)

        if (index >= 0) {
            _platformState.update {
                it.mapIndexed { i, p ->
                    if (index == i) {
                        p.copy(topP = modifiedTopP)
                    } else {
                        p
                    }
                }
            }
        }
    }

    fun updateSystemPrompt(apiType: ApiType, prompt: String) {
        val index = _platformState.value.indexOfFirst { it.name == apiType }

        if (index >= 0) {
            _platformState.update {
                it.mapIndexed { i, p ->
                    if (index == i && prompt.isNotBlank()) {
                        p.copy(systemPrompt = prompt)
                    } else {
                        p
                    }
                }
            }
        }
    }

    // Local AI specific settings
    fun updateTopK(topK: Int) {
        val index = _platformState.value.indexOfFirst { it.name == ApiType.LOCAL }
        val modifiedTopK = topK.coerceIn(1, 200)

        if (index >= 0) {
            _platformState.update {
                it.mapIndexed { i, p ->
                    if (index == i) {
                        p.copy(topK = modifiedTopK)
                    } else {
                        p
                    }
                }
            }
        }
    }

    fun updateBatchSize(batchSize: Int) {
        val index = _platformState.value.indexOfFirst { it.name == ApiType.LOCAL }
        // 0 = Auto, otherwise 256, 512, 1024
        val modifiedBatchSize = when {
            batchSize == 0 -> 0 // Auto
            batchSize < 384 -> 256
            batchSize < 768 -> 512
            else -> 1024
        }

        if (index >= 0) {
            _platformState.update {
                it.mapIndexed { i, p ->
                    if (index == i) {
                        p.copy(batchSize = modifiedBatchSize)
                    } else {
                        p
                    }
                }
            }
        }
    }

    fun updateContextSize(contextSize: Int) {
        val index = _platformState.value.indexOfFirst { it.name == ApiType.LOCAL }
        // Valid values: 1024, 2048, 4096, 8192
        val modifiedContextSize = when {
            contextSize < 1536 -> 1024
            contextSize < 3072 -> 2048
            contextSize < 6144 -> 4096
            else -> 8192
        }

        if (index >= 0) {
            _platformState.update {
                it.mapIndexed { i, p ->
                    if (index == i) {
                        p.copy(contextSize = modifiedContextSize)
                    } else {
                        p
                    }
                }
            }
        }
    }

    fun openThemeDialog() = _dialogState.update { it.copy(isThemeDialogOpen = true) }

    fun openApiUrlDialog() = _dialogState.update { it.copy(isApiUrlDialogOpen = true) }

    fun openApiTokenDialog() = _dialogState.update { it.copy(isApiTokenDialogOpen = true) }

    fun openApiModelDialog() = _dialogState.update { it.copy(isApiModelDialogOpen = true) }

    fun openTemperatureDialog() = _dialogState.update { it.copy(isTemperatureDialogOpen = true) }

    fun openTopPDialog() = _dialogState.update { it.copy(isTopPDialogOpen = true) }

    fun openSystemPromptDialog() = _dialogState.update { it.copy(isSystemPromptDialogOpen = true) }

    fun closeThemeDialog() = _dialogState.update { it.copy(isThemeDialogOpen = false) }

    fun closeApiUrlDialog() = _dialogState.update { it.copy(isApiUrlDialogOpen = false) }

    fun closeApiTokenDialog() = _dialogState.update { it.copy(isApiTokenDialogOpen = false) }

    fun closeApiModelDialog() = _dialogState.update { it.copy(isApiModelDialogOpen = false) }

    fun closeTemperatureDialog() = _dialogState.update { it.copy(isTemperatureDialogOpen = false) }

    fun closeTopPDialog() = _dialogState.update { it.copy(isTopPDialogOpen = false) }

    fun closeSystemPromptDialog() = _dialogState.update { it.copy(isSystemPromptDialogOpen = false) }

    // Local AI dialog functions
    fun openTopKDialog() = _dialogState.update { it.copy(isTopKDialogOpen = true) }
    fun openBatchSizeDialog() = _dialogState.update { it.copy(isBatchSizeDialogOpen = true) }
    fun openContextSizeDialog() = _dialogState.update { it.copy(isContextSizeDialogOpen = true) }
    fun closeTopKDialog() = _dialogState.update { it.copy(isTopKDialogOpen = false) }
    fun closeBatchSizeDialog() = _dialogState.update { it.copy(isBatchSizeDialogOpen = false) }
    fun closeContextSizeDialog() = _dialogState.update { it.copy(isContextSizeDialogOpen = false) }

    private fun fetchPlatformStatus() {
        viewModelScope.launch {
            val platforms = settingRepository.fetchPlatforms()
            _platformState.update { platforms }
        }
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

    data class DialogState(
        val isThemeDialogOpen: Boolean = false,
        val isApiUrlDialogOpen: Boolean = false,
        val isApiTokenDialogOpen: Boolean = false,
        val isApiModelDialogOpen: Boolean = false,
        val isTemperatureDialogOpen: Boolean = false,
        val isTopPDialogOpen: Boolean = false,
        val isSystemPromptDialogOpen: Boolean = false,
        // Local AI specific dialogs
        val isTopKDialogOpen: Boolean = false,
        val isBatchSizeDialogOpen: Boolean = false,
        val isContextSizeDialogOpen: Boolean = false
    )
}
