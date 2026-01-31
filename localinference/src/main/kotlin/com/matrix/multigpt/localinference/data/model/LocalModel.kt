package com.matrix.multigpt.localinference.data.model

import kotlinx.serialization.Serializable

/**
 * Represents a local AI model available for download and inference.
 */
@Serializable
data class LocalModel(
    val id: String,
    val name: String,
    val description: String,
    val size: Long, // Size in bytes
    val downloadUrl: String,
    val fileName: String,
    val performance: ModelPerformance,
    val useCases: List<UseCase>,
    val quantization: String,
    val parameters: String, // e.g., "1B", "3B", "7B"
    val contextLength: Int,
    val familyId: String,
    val isRecommended: Boolean = false,
    val isImported: Boolean = false // True if imported from local storage
)

/**
 * Model performance metrics for device suitability.
 */
@Serializable
data class ModelPerformance(
    val tokensPerSecond: Float, // Average tokens/sec on mid-range device
    val memoryRequired: Long, // RAM required in bytes
    val cpuIntensive: Boolean,
    val gpuAccelerated: Boolean,
    val rating: PerformanceRating
)

/**
 * Performance rating categories.
 */
@Serializable
enum class PerformanceRating {
    FAST,      // Very fast inference, suitable for all devices
    BALANCED,  // Good balance of speed and quality
    QUALITY,   // Higher quality but slower
    DEMANDING  // Requires high-end device
}

/**
 * Use cases for the model.
 */
@Serializable
enum class UseCase {
    CHAT,           // General conversation
    CODING,         // Code generation and assistance
    CREATIVE,       // Creative writing, stories
    SUMMARIZATION,  // Text summarization
    TRANSLATION,    // Language translation
    QUESTION_ANSWERING, // Q&A tasks
    MATH,           // Mathematical reasoning
    GENERAL         // General purpose
}

/**
 * Represents the download/installation state of a model.
 */
@Serializable
data class LocalModelState(
    val modelId: String,
    val status: ModelStatus,
    val downloadProgress: Float = 0f, // 0.0 to 1.0
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val localPath: String? = null,
    val errorMessage: String? = null
)

/**
 * Status of a local model.
 */
@Serializable
enum class ModelStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    PAUSED,
    DOWNLOADED,
    LOADING,
    LOADED,
    ERROR
}

/**
 * Model family grouping related models together.
 */
@Serializable
data class ModelFamily(
    val id: String,
    val name: String,
    val description: String,
    val developer: String,
    val license: String,
    val websiteUrl: String? = null
)
