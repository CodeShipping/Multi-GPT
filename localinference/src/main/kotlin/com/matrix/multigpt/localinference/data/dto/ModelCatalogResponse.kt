package com.matrix.multigpt.localinference.data.dto

import kotlinx.serialization.Serializable

/**
 * Response from Firebase containing the model catalog.
 */
@Serializable
data class ModelCatalogResponse(
    val families: List<ModelFamilyDto>,
    val models: List<LocalModelDto>,
    val version: Int,
    val lastUpdated: Long
)

/**
 * DTO for LocalModel from Firebase.
 */
@Serializable
data class LocalModelDto(
    val id: String,
    val name: String,
    val description: String,
    val size: Long,
    val downloadUrl: String,
    val fileName: String,
    val performance: ModelPerformanceDto,
    val useCases: List<String>,
    val quantization: String,
    val parameters: String,
    val contextLength: Int,
    val familyId: String,
    val isRecommended: Boolean = false,
    val isEnabled: Boolean = true
)

/**
 * DTO for ModelPerformance from Firebase.
 */
@Serializable
data class ModelPerformanceDto(
    val tokensPerSecond: Float,
    val memoryRequired: Long,
    val cpuIntensive: Boolean,
    val gpuAccelerated: Boolean,
    val rating: String
)

/**
 * DTO for ModelFamily from Firebase.
 */
@Serializable
data class ModelFamilyDto(
    val id: String,
    val name: String,
    val description: String,
    val developer: String,
    val license: String,
    val websiteUrl: String? = null
)
