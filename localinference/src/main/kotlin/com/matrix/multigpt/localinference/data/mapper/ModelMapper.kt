package com.matrix.multigpt.localinference.data.mapper

import com.matrix.multigpt.localinference.data.dto.LocalModelDto
import com.matrix.multigpt.localinference.data.dto.ModelCatalogResponse
import com.matrix.multigpt.localinference.data.dto.ModelFamilyDto
import com.matrix.multigpt.localinference.data.dto.ModelPerformanceDto
import com.matrix.multigpt.localinference.data.model.*

/**
 * Mapper to convert DTOs from Firebase to domain models.
 */
object ModelMapper {

    /**
     * Convert ModelCatalogResponse DTO to domain models.
     */
    fun mapCatalogResponse(response: ModelCatalogResponse): Pair<List<ModelFamily>, List<LocalModel>> {
        val families = response.families.map { mapFamily(it) }
        val models = response.models
            .filter { it.isEnabled }
            .map { mapModel(it) }
        return Pair(families, models)
    }

    /**
     * Convert ModelFamilyDto to ModelFamily domain model.
     */
    fun mapFamily(dto: ModelFamilyDto): ModelFamily {
        return ModelFamily(
            id = dto.id,
            name = dto.name,
            description = dto.description,
            developer = dto.developer,
            license = dto.license,
            websiteUrl = dto.websiteUrl
        )
    }

    /**
     * Convert LocalModelDto to LocalModel domain model.
     */
    fun mapModel(dto: LocalModelDto): LocalModel {
        return LocalModel(
            id = dto.id,
            name = dto.name,
            description = dto.description,
            size = dto.size,
            downloadUrl = dto.downloadUrl,
            fileName = dto.fileName,
            performance = mapPerformance(dto.performance),
            useCases = dto.useCases.mapNotNull { mapUseCase(it) },
            quantization = dto.quantization,
            parameters = dto.parameters,
            contextLength = dto.contextLength,
            familyId = dto.familyId,
            isRecommended = dto.isRecommended
        )
    }

    /**
     * Convert ModelPerformanceDto to ModelPerformance domain model.
     */
    private fun mapPerformance(dto: ModelPerformanceDto): ModelPerformance {
        return ModelPerformance(
            tokensPerSecond = dto.tokensPerSecond,
            memoryRequired = dto.memoryRequired,
            cpuIntensive = dto.cpuIntensive,
            gpuAccelerated = dto.gpuAccelerated,
            rating = mapPerformanceRating(dto.rating)
        )
    }

    /**
     * Map performance rating string to enum.
     */
    private fun mapPerformanceRating(rating: String): PerformanceRating {
        return when (rating.uppercase()) {
            "FAST" -> PerformanceRating.FAST
            "BALANCED" -> PerformanceRating.BALANCED
            "QUALITY" -> PerformanceRating.QUALITY
            "DEMANDING" -> PerformanceRating.DEMANDING
            else -> PerformanceRating.BALANCED
        }
    }

    /**
     * Map use case string to enum.
     */
    private fun mapUseCase(useCase: String): UseCase? {
        return when (useCase.uppercase()) {
            "CHAT" -> UseCase.CHAT
            "CODING" -> UseCase.CODING
            "CREATIVE" -> UseCase.CREATIVE
            "SUMMARIZATION" -> UseCase.SUMMARIZATION
            "TRANSLATION" -> UseCase.TRANSLATION
            "QUESTION_ANSWERING" -> UseCase.QUESTION_ANSWERING
            "MATH" -> UseCase.MATH
            "GENERAL" -> UseCase.GENERAL
            else -> null
        }
    }
}
