package com.matrix.multigpt.data.network

import com.matrix.multigpt.data.dto.bedrock.BedrockAuthMethod
import com.matrix.multigpt.data.dto.bedrock.BedrockCredentials
import com.matrix.multigpt.data.dto.bedrock.BedrockError
import com.matrix.multigpt.data.dto.bedrock.BedrockMessage
import com.matrix.multigpt.data.dto.bedrock.BedrockRequest
import com.matrix.multigpt.data.dto.bedrock.BedrockStreamChunk
import com.matrix.multigpt.data.dto.bedrock.ConverseContent
import com.matrix.multigpt.data.dto.bedrock.ConverseInferenceConfig
import com.matrix.multigpt.data.dto.bedrock.ConverseMessage
import com.matrix.multigpt.data.dto.bedrock.ConverseRequest
import com.matrix.multigpt.data.dto.bedrock.ConverseSystemMessage
import com.matrix.multigpt.data.dto.bedrock.TextGenerationConfig
import com.matrix.multigpt.util.AwsSignatureV4
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.headers
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.HttpStatement
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readUTF8Line
import javax.inject.Inject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class BedrockAPIImpl @Inject constructor(
    private val networkClient: NetworkClient
) : BedrockAPI {

    private var bedrockCredentials: BedrockCredentials? = null

    override fun setCredentials(credentials: AwsSignatureV4.AwsCredentials?) {
        // Convert old format to new format for backwards compatibility
        this.bedrockCredentials = credentials?.let {
            BedrockCredentials(
                authMethod = BedrockAuthMethod.SIGNATURE_V4,
                accessKeyId = it.accessKeyId,
                secretAccessKey = it.secretAccessKey,
                region = it.region,
                sessionToken = it.sessionToken
            )
        }
    }

    override fun setRegion(region: String) {
        bedrockCredentials = bedrockCredentials?.copy(region = region) ?: BedrockCredentials(region = region)
    }

    // New method to set complete Bedrock credentials
    override fun setBedrockCredentials(credentials: BedrockCredentials?) {
        this.bedrockCredentials = credentials
    }

    override fun streamChatMessage(
        messages: List<Pair<String, String>>,
        model: String,
        systemPrompt: String?,
        temperature: Double?,
        maxTokens: Int?,
        topP: Double?
    ): Flow<BedrockStreamChunk> {
        
        return flow {
            try {
                val creds = bedrockCredentials ?: run {
                    emit(BedrockStreamChunk(error = BedrockError(type = "auth_error", message = "Bedrock credentials not configured")))
                    return@flow
                }

                val request = createRequest(messages, model, systemPrompt, temperature, maxTokens, topP)
                val requestBody = Json.encodeToJsonElement(request).toString()
                
                val (uri, endpoint, builder) = when (creds.authMethod) {
                    BedrockAuthMethod.API_KEY -> {
                        // Use Bearer token authentication with Converse API
                        if (creds.apiKey.isBlank()) {
                            emit(BedrockStreamChunk(error = BedrockError(type = "auth_error", message = "Bedrock API key is required")))
                            return@flow
                        }
                        
                        val converseUri = "/model/${model}/converse"
                        val converseEndpoint = "https://bedrock-runtime.${creds.region}.amazonaws.com${converseUri}"
                        val converseRequest = createConverseRequest(messages, systemPrompt, temperature, maxTokens, topP)
                        val converseBody = Json.encodeToString(ConverseRequest.serializer(), converseRequest)
                        
                        Triple(
                            converseUri,
                            converseEndpoint,
                            HttpRequestBuilder().apply {
                                method = HttpMethod.Post
                                url(converseEndpoint)
                                contentType(ContentType.Application.Json)
                                setBody(converseBody)
                                headers {
                                    append("Authorization", "Bearer ${creds.apiKey}")
                                    append("Content-Type", "application/json")
                                }
                            }
                        )
                    }
                    
                    BedrockAuthMethod.SIGNATURE_V4 -> {
                        // Use AWS Signature V4 authentication
                        if (creds.accessKeyId.isBlank() || creds.secretAccessKey.isBlank()) {
                            emit(BedrockStreamChunk(error = BedrockError(type = "auth_error", message = "AWS Access Key ID and Secret Access Key are required")))
                            return@flow
                        }
                        
                        val streamUri = "/model/${model}/invoke-with-response-stream"
                        val streamEndpoint = "https://bedrock-runtime.${creds.region}.amazonaws.com${streamUri}"
                        
                        val awsCredentials = AwsSignatureV4.AwsCredentials(
                            accessKeyId = creds.accessKeyId,
                            secretAccessKey = creds.secretAccessKey,
                            sessionToken = creds.sessionToken,
                            region = creds.region
                        )
                        
                        val headers = mutableMapOf(
                            "Content-Type" to "application/json",
                            "Accept" to "application/vnd.amazon.eventstream"
                        )
                        
                        val signedRequest = AwsSignatureV4.signRequest(
                            method = "POST",
                            uri = streamUri,
                            headers = headers,
                            payload = requestBody,
                            credentials = awsCredentials,
                            service = "bedrock"
                        )

                        Triple(
                            streamUri,
                            streamEndpoint,
                            HttpRequestBuilder().apply {
                                method = HttpMethod.Post
                                url(streamEndpoint)
                                contentType(ContentType.Application.Json)
                                setBody(requestBody)
                                
                                signedRequest.headers.forEach { (key, value) ->
                                    headers { append(key, value) }
                                }
                            }
                        )
                    }
                }

                HttpStatement(builder = builder, client = networkClient()).execute { response ->
                    if (creds.authMethod == BedrockAuthMethod.API_KEY) {
                        streamConverseEventsFrom(response)
                    } else {
                        streamEventsFrom(response, model)
                    }
                }
                
            } catch (e: Exception) {
                emit(BedrockStreamChunk(error = BedrockError(type = "network_error", message = e.message ?: "Unknown error")))
            }
        }
    }

    private fun createRequest(
        messages: List<Pair<String, String>>,
        model: String,
        systemPrompt: String?,
        temperature: Double?,
        maxTokens: Int?,
        topP: Double?
    ): BedrockRequest {
        
        return when {
            model.startsWith("anthropic.claude") -> {
                // Anthropic Claude format
                BedrockRequest(
                    messages = messages.map { BedrockMessage(role = it.first, content = it.second) },
                    max_tokens = maxTokens ?: 4096,
                    temperature = temperature,
                    top_p = topP,
                    system = systemPrompt,
                    anthropic_version = "bedrock-2023-05-31"
                )
            }
            model.startsWith("amazon.titan") -> {
                // Amazon Titan format
                val conversationText = buildString {
                    if (!systemPrompt.isNullOrEmpty()) {
                        append("System: $systemPrompt\n\n")
                    }
                    messages.forEach { (role, content) ->
                        val displayRole = when (role) {
                            "user" -> "User"
                            "assistant" -> "Assistant"
                            else -> role.capitalize()
                        }
                        append("$displayRole: $content\n\n")
                    }
                    append("Assistant:")
                }
                
                BedrockRequest(
                    inputText = conversationText,
                    textGenerationConfig = TextGenerationConfig(
                        maxTokenCount = maxTokens ?: 4096,
                        temperature = temperature,
                        topP = topP
                    )
                )
            }
            model.startsWith("ai21.") -> {
                // AI21 format
                val prompt = buildString {
                    if (!systemPrompt.isNullOrEmpty()) {
                        append("$systemPrompt\n\n")
                    }
                    messages.forEach { (role, content) ->
                        val displayRole = when (role) {
                            "user" -> "Human"
                            "assistant" -> "Assistant"
                            else -> role.capitalize()
                        }
                        append("$displayRole: $content\n\n")
                    }
                    append("Assistant:")
                }
                
                BedrockRequest(
                    inputText = prompt,
                    textGenerationConfig = TextGenerationConfig(
                        maxTokenCount = maxTokens ?: 4096,
                        temperature = temperature,
                        topP = topP
                    )
                )
            }
            model.startsWith("cohere.") -> {
                // Cohere format
                val prompt = buildString {
                    if (!systemPrompt.isNullOrEmpty()) {
                        append("$systemPrompt\n\n")
                    }
                    messages.forEach { (role, content) ->
                        append("$content\n")
                    }
                }
                
                BedrockRequest(
                    inputText = prompt,
                    textGenerationConfig = TextGenerationConfig(
                        maxTokenCount = maxTokens ?: 4096,
                        temperature = temperature,
                        topP = topP
                    )
                )
            }
            model.startsWith("meta.llama") -> {
                // Meta Llama format
                val prompt = buildString {
                    if (!systemPrompt.isNullOrEmpty()) {
                        append("<s>[INST] <<SYS>>\n$systemPrompt\n<</SYS>>\n\n")
                    } else {
                        append("<s>[INST] ")
                    }
                    
                    messages.forEachIndexed { index, (role, content) ->
                        when (role) {
                            "user" -> {
                                if (index == 0 && !systemPrompt.isNullOrEmpty()) {
                                    append("$content [/INST]")
                                } else {
                                    append("<s>[INST] $content [/INST]")
                                }
                            }
                            "assistant" -> append(" $content </s>")
                        }
                    }
                    
                    if (messages.lastOrNull()?.first == "user") {
                        // Ready for assistant response
                    } else {
                        append("<s>[INST] ")
                    }
                }
                
                BedrockRequest(
                    inputText = prompt,
                    textGenerationConfig = TextGenerationConfig(
                        maxTokenCount = maxTokens ?: 4096,
                        temperature = temperature,
                        topP = topP
                    )
                )
            }
            else -> {
                // Default format
                BedrockRequest(
                    messages = messages.map { BedrockMessage(role = it.first, content = it.second) },
                    max_tokens = maxTokens ?: 4096,
                    temperature = temperature,
                    top_p = topP,
                    system = systemPrompt
                )
            }
        }
    }

    private fun createConverseRequest(
        messages: List<Pair<String, String>>,
        systemPrompt: String?,
        temperature: Double?,
        maxTokens: Int?,
        topP: Double?
    ): ConverseRequest {
        val converseMessages = messages.map { (role, content) ->
            ConverseMessage(
                role = role,
                content = listOf(ConverseContent(text = content))
            )
        }
        
        val systemMessages = if (!systemPrompt.isNullOrBlank()) {
            listOf(ConverseSystemMessage(text = systemPrompt))
        } else null
        
        val inferenceConfig = if (maxTokens != null || temperature != null || topP != null) {
            ConverseInferenceConfig(
                maxTokens = maxTokens,
                temperature = temperature,
                topP = topP
            )
        } else null
        
        return ConverseRequest(
            messages = converseMessages,
            system = systemMessages,
            inferenceConfig = inferenceConfig
        )
    }

    private suspend fun FlowCollector<BedrockStreamChunk>.streamConverseEventsFrom(
        response: HttpResponse
    ) {
        val responseBody = response.body<String>()
        val jsonInstance = Json { ignoreUnknownKeys = true }
        
        try {
            // Parse the Converse API response
            val converseResponse = Json.parseToJsonElement(responseBody).jsonObject
            
            if (converseResponse.containsKey("output")) {
                val output = converseResponse["output"]?.jsonObject
                val message = output?.get("message")?.jsonObject
                val contentArray = message?.get("content")?.jsonArray
                val content = contentArray?.firstOrNull()?.jsonObject
                val text = content?.get("text")?.jsonPrimitive?.content
                
                if (!text.isNullOrBlank()) {
                    emit(BedrockStreamChunk(content_block = com.matrix.multigpt.data.dto.bedrock.BedrockContentBlock(type = "text", text = text)))
                }
            }
            
            if (converseResponse.containsKey("error")) {
                val error = converseResponse["error"]?.jsonObject
                val errorMessage = error?.get("message")?.jsonPrimitive?.content ?: "Unknown error"
                emit(BedrockStreamChunk(error = BedrockError(type = "api_error", message = errorMessage)))
            }
            
        } catch (e: Exception) {
            emit(BedrockStreamChunk(error = BedrockError(type = "parse_error", message = "Failed to parse response: ${e.message}")))
        }
    }

    private suspend inline fun <reified T> FlowCollector<T>.streamEventsFrom(
        response: HttpResponse,
        model: String
    ) {
        val channel: ByteReadChannel = response.body()
        val jsonInstance = Json { ignoreUnknownKeys = true }

        try {
            while (currentCoroutineContext().isActive && !channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: continue
                
                when {
                    line.isEmpty() -> continue
                    line.startsWith("data:") -> {
                        val data = line.removePrefix("data:").trim()
                        if (data == "[DONE]") break
                        
                        try {
                            val chunk: T = jsonInstance.decodeFromString(data)
                            emit(chunk)
                        } catch (e: Exception) {
                            // Try to parse as error or continue
                            continue
                        }
                    }
                    line.startsWith("event:") -> {
                        val event = line.removePrefix("event:").trim()
                        if (event == "completion" || event == "done") break
                    }
                }
            }
        } finally {
            channel.cancel()
        }
    }
}
