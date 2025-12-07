package com.matrix.multigpt.data.dto.bedrock

import kotlinx.serialization.Serializable

@Serializable
data class BedrockRequest(
    val inputText: String? = null,
    val textGenerationConfig: TextGenerationConfig? = null,
    val messages: List<BedrockMessage>? = null,
    val max_tokens: Int? = null,
    val temperature: Double? = null,
    val top_p: Double? = null,
    val stop_sequences: List<String>? = null,
    val anthropic_version: String? = null,
    val system: String? = null
)

@Serializable
data class BedrockMessage(
    val role: String,
    val content: String
)

@Serializable
data class TextGenerationConfig(
    val maxTokenCount: Int? = null,
    val stopSequences: List<String>? = null,
    val temperature: Double? = null,
    val topP: Double? = null
)

@Serializable
data class ConverseRequest(
    val messages: List<ConverseMessage>,
    val system: List<ConverseSystemMessage>? = null,
    val inferenceConfig: ConverseInferenceConfig? = null
)

@Serializable
data class ConverseMessage(
    val role: String,
    val content: List<ConverseContent>
)

@Serializable
data class ConverseContent(
    val text: String
)

@Serializable
data class ConverseSystemMessage(
    val text: String
)

@Serializable
data class ConverseInferenceConfig(
    val maxTokens: Int? = null,
    val temperature: Double? = null,
    val topP: Double? = null
)
