package com.matrix.multigpt.localinference.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matrix.multigpt.localinference.data.model.ModelStatus
import com.matrix.multigpt.localinference.data.repository.LocalModelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for model list screen.
 * Framework-agnostic - exposes StateFlow and Channel for any UI framework.
 */
@HiltViewModel
class ModelListViewModel @Inject constructor(
    private val repository: LocalModelRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelListUiState())
    val uiState: StateFlow<ModelListUiState> = _uiState.asStateFlow()

    private val _effects = Channel<ModelListEffect>(Channel.BUFFERED)
    val effects: Flow<ModelListEffect> = _effects.receiveAsFlow()

    init {
        loadModels()
        observeModelStates()
    }

    /**
     * Handle UI events.
     */
    fun onEvent(event: ModelListEvent) {
        when (event) {
            ModelListEvent.LoadModels -> loadModels()
            ModelListEvent.Refresh -> refresh()
            is ModelListEvent.SetFilter -> setFilter(event.filter)
            is ModelListEvent.SetSearchQuery -> setSearchQuery(event.query)
            is ModelListEvent.DownloadModel -> downloadModel(event.modelId)
            is ModelListEvent.PauseDownload -> pauseDownload(event.modelId)
            is ModelListEvent.ResumeDownload -> resumeDownload(event.modelId)
            is ModelListEvent.CancelDownload -> cancelDownload(event.modelId)
            is ModelListEvent.DeleteModel -> deleteModel(event.modelId)
            is ModelListEvent.SelectModel -> selectModel(event.modelId)
        }
    }

    private fun loadModels() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            try {
                val models = repository.getModels()
                val families = repository.getModelFamilies()
                
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        models = models,
                        families = families,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load models"
                    )
                }
                _effects.send(ModelListEffect.ShowError(e.message ?: "Failed to load models"))
            }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            
            try {
                repository.forceRefresh()
                val models = repository.getModels()
                val families = repository.getModelFamilies()
                
                _uiState.update { 
                    it.copy(
                        isRefreshing = false,
                        models = models,
                        families = families,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isRefreshing = false) }
                _effects.send(ModelListEffect.ShowError(e.message ?: "Failed to refresh"))
            }
        }
    }

    private fun observeModelStates() {
        viewModelScope.launch {
            repository.getModelStates().collect { states ->
                val previousStates = _uiState.value.modelStates
                _uiState.update { it.copy(modelStates = states) }
                
                // Check for download completion
                states.forEach { (modelId, state) ->
                    val previousState = previousStates[modelId]
                    if (previousState?.status == ModelStatus.DOWNLOADING && 
                        state.status == ModelStatus.DOWNLOADED) {
                        _effects.send(ModelListEffect.ShowDownloadComplete)
                    }
                }
            }
        }
    }

    private fun setFilter(filter: ModelFilter) {
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    private fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    private fun downloadModel(modelId: String) {
        viewModelScope.launch {
            try {
                repository.downloadModel(modelId)
                _effects.send(ModelListEffect.ShowDownloadStarted)
            } catch (e: Exception) {
                _effects.send(ModelListEffect.ShowError(e.message ?: "Failed to start download"))
            }
        }
    }

    private fun pauseDownload(modelId: String) {
        viewModelScope.launch {
            try {
                repository.pauseDownload(modelId)
            } catch (e: Exception) {
                _effects.send(ModelListEffect.ShowError(e.message ?: "Failed to pause download"))
            }
        }
    }

    private fun resumeDownload(modelId: String) {
        viewModelScope.launch {
            try {
                repository.resumeDownload(modelId)
            } catch (e: Exception) {
                _effects.send(ModelListEffect.ShowError(e.message ?: "Failed to resume download"))
            }
        }
    }

    private fun cancelDownload(modelId: String) {
        viewModelScope.launch {
            try {
                repository.cancelDownload(modelId)
            } catch (e: Exception) {
                _effects.send(ModelListEffect.ShowError(e.message ?: "Failed to cancel download"))
            }
        }
    }

    private fun deleteModel(modelId: String) {
        viewModelScope.launch {
            _effects.send(ModelListEffect.ShowDeleteConfirmation(modelId))
        }
    }

    /**
     * Confirm model deletion after user confirmation.
     */
    fun confirmDeleteModel(modelId: String) {
        viewModelScope.launch {
            try {
                repository.deleteModel(modelId)
            } catch (e: Exception) {
                _effects.send(ModelListEffect.ShowError(e.message ?: "Failed to delete model"))
            }
        }
    }

    private fun selectModel(modelId: String) {
        viewModelScope.launch {
            val state = _uiState.value.modelStates[modelId]
            
            when (state?.status) {
                ModelStatus.DOWNLOADED, ModelStatus.LOADED -> {
                    // Model is ready, navigate to chat
                    val path = repository.getModelPath(modelId)
                    if (path != null) {
                        _effects.send(ModelListEffect.NavigateToChat(modelId, path))
                    } else {
                        _effects.send(ModelListEffect.ShowError("Model file not found"))
                    }
                }
                else -> {
                    // Show model detail/download screen
                    _effects.send(ModelListEffect.NavigateToModelDetail(modelId))
                }
            }
        }
    }

    /**
     * Get formatted size string for display.
     */
    fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    /**
     * Get formatted download progress.
     */
    fun formatProgress(downloaded: Long, total: Long): String {
        val downloadedStr = formatSize(downloaded)
        val totalStr = formatSize(total)
        val percent = if (total > 0) (downloaded * 100 / total) else 0
        return "$downloadedStr / $totalStr ($percent%)"
    }
}
