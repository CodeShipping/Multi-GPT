package com.matrix.multigpt.localinference.presentation

import com.matrix.multigpt.localinference.data.model.LocalModel
import com.matrix.multigpt.localinference.data.model.LocalModelState
import com.matrix.multigpt.localinference.data.model.ModelFamily
import com.matrix.multigpt.localinference.data.model.PerformanceRating
import com.matrix.multigpt.localinference.data.model.UseCase

/**
 * UI State for the model list screen.
 * Framework-agnostic - can be used with Compose, Views, or any other UI framework.
 */
data class ModelListUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val models: List<LocalModel> = emptyList(),
    val families: List<ModelFamily> = emptyList(),
    val modelStates: Map<String, LocalModelState> = emptyMap(),
    val selectedFilter: ModelFilter = ModelFilter.All,
    val searchQuery: String = "",
    val isRefreshing: Boolean = false
) {
    /**
     * Get filtered models based on current filter and search query.
     */
    val filteredModels: List<LocalModel>
        get() {
            var result = models
            
            // Apply filter
            result = when (selectedFilter) {
                ModelFilter.All -> result
                ModelFilter.Recommended -> result.filter { it.isRecommended }
                ModelFilter.Downloaded -> result.filter { 
                    modelStates[it.id]?.status?.isDownloaded == true 
                }
                is ModelFilter.ByFamily -> result.filter { it.familyId == selectedFilter.familyId }
                is ModelFilter.ByUseCase -> result.filter { selectedFilter.useCase in it.useCases }
                is ModelFilter.ByPerformance -> result.filter { 
                    it.performance.rating == selectedFilter.rating 
                }
            }
            
            // Apply search
            if (searchQuery.isNotBlank()) {
                val query = searchQuery.lowercase()
                result = result.filter {
                    it.name.lowercase().contains(query) ||
                    it.description.lowercase().contains(query) ||
                    it.parameters.lowercase().contains(query)
                }
            }
            
            return result
        }

    /**
     * Group filtered models by family.
     */
    val groupedByFamily: Map<ModelFamily?, List<LocalModel>>
        get() = filteredModels.groupBy { model ->
            families.find { it.id == model.familyId }
        }
}

/**
 * Filter options for model list.
 */
sealed class ModelFilter {
    data object All : ModelFilter()
    data object Recommended : ModelFilter()
    data object Downloaded : ModelFilter()
    data class ByFamily(val familyId: String) : ModelFilter()
    data class ByUseCase(val useCase: UseCase) : ModelFilter()
    data class ByPerformance(val rating: PerformanceRating) : ModelFilter()
}

/**
 * Extension to check if model status is downloaded.
 */
private val com.matrix.multigpt.localinference.data.model.ModelStatus.isDownloaded: Boolean
    get() = this == com.matrix.multigpt.localinference.data.model.ModelStatus.DOWNLOADED ||
            this == com.matrix.multigpt.localinference.data.model.ModelStatus.LOADED

/**
 * UI State for model detail/download screen.
 */
data class ModelDetailUiState(
    val model: LocalModel? = null,
    val family: ModelFamily? = null,
    val downloadState: LocalModelState? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

/**
 * Events that can be triggered from the UI.
 */
sealed class ModelListEvent {
    data object LoadModels : ModelListEvent()
    data object Refresh : ModelListEvent()
    data class SetFilter(val filter: ModelFilter) : ModelListEvent()
    data class SetSearchQuery(val query: String) : ModelListEvent()
    data class DownloadModel(val modelId: String) : ModelListEvent()
    data class PauseDownload(val modelId: String) : ModelListEvent()
    data class ResumeDownload(val modelId: String) : ModelListEvent()
    data class CancelDownload(val modelId: String) : ModelListEvent()
    data class DeleteModel(val modelId: String) : ModelListEvent()
    data class SelectModel(val modelId: String) : ModelListEvent()
    data class ImportModels(val uris: List<android.net.Uri>) : ModelListEvent()
}

/**
 * One-time effects/actions to be handled by UI.
 */
sealed class ModelListEffect {
    data class ShowError(val message: String) : ModelListEffect()
    data class NavigateToModelDetail(val modelId: String) : ModelListEffect()
    data class NavigateToChat(val modelId: String, val modelPath: String) : ModelListEffect()
    data object ShowDownloadStarted : ModelListEffect()
    data object ShowDownloadComplete : ModelListEffect()
    data class ShowDeleteConfirmation(val modelId: String) : ModelListEffect()
    data class ShowImportSuccess(val count: Int) : ModelListEffect()
    data class ShowImportProgress(val current: Int, val total: Int, val fileName: String) : ModelListEffect()
}
