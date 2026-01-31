package com.matrix.multigpt.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Common response structure for OpenAI and compatible APIs (OpenAI, Groq)
 */
@Serializable
data class OpenAIModelsResponse(
    @SerialName("data") val data: List<OpenAIModel>,
    @SerialName("object") val objectType: String = "list"
)

@Serializable
data class OpenAIModel(
    @SerialName("id") val id: String,
    @SerialName("object") val objectType: String = "model",
    @SerialName("created") val created: Long? = null,
    @SerialName("owned_by") val ownedBy: String? = null
)

/**
 * Response structure for Google AI models
 */
@Serializable
data class GoogleModelsResponse(
    @SerialName("models") val models: List<GoogleModel>
)

@Serializable
data class GoogleModel(
    @SerialName("name") val name: String,
    @SerialName("displayName") val displayName: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("supportedGenerationMethods") val supportedGenerationMethods: List<String> = emptyList()
)

/**
 * Unified model representation for the app
 */
data class ModelInfo(
    val id: String,
    val name: String,
    val description: String? = null,
    val createdAt: Long? = null,
    val isAvailable: Boolean = true // For local models: true if downloaded
)

/**
 * Result wrapper for model fetching operations
 */
sealed class ModelFetchResult {
    data class Success(val models: List<ModelInfo>) : ModelFetchResult()
    data class Error(val message: String) : ModelFetchResult()
    object Loading : ModelFetchResult()
}
