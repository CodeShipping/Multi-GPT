package com.matrix.multigpt.data.dto.bedrock

import kotlinx.serialization.Serializable

@Serializable
enum class BedrockAuthMethod {
    SIGNATURE_V4,  // Traditional AWS signing with Access Key ID + Secret Access Key
    API_KEY        // New Bearer token authentication
}

@Serializable
data class BedrockCredentials(
    val authMethod: BedrockAuthMethod = BedrockAuthMethod.API_KEY,
    // For Signature V4 authentication
    val accessKeyId: String = "",
    val secretAccessKey: String = "",
    val region: String = "us-east-1",
    val sessionToken: String? = null,
    // For API Key authentication
    val apiKey: String = ""
)
