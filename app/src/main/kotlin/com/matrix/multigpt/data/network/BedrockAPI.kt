package com.matrix.multigpt.data.network

import com.matrix.multigpt.data.dto.bedrock.BedrockCredentials
import com.matrix.multigpt.data.dto.bedrock.BedrockStreamChunk
import com.matrix.multigpt.util.AwsSignatureV4
import kotlinx.coroutines.flow.Flow

interface BedrockAPI {
    // Legacy methods for backwards compatibility
    fun setCredentials(credentials: AwsSignatureV4.AwsCredentials?)
    fun setRegion(region: String)
    
    // New method for enhanced credential support
    fun setBedrockCredentials(credentials: BedrockCredentials?)
    
    fun streamChatMessage(
        messages: List<Pair<String, String>>,
        model: String,
        systemPrompt: String? = null,
        temperature: Double? = null,
        maxTokens: Int? = null,
        topP: Double? = null
    ): Flow<BedrockStreamChunk>
}
