package com.matrix.multigpt.data

import com.matrix.multigpt.data.model.ApiType

object ModelConstants {
    // LinkedHashSet should be used to guarantee item order
    val openaiModels = linkedSetOf("gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-4")
    val anthropicModels = linkedSetOf("claude-3-5-sonnet-20240620", "claude-3-opus-20240229", "claude-3-sonnet-20240229", "claude-3-haiku-20240307")
    val googleModels = linkedSetOf("gemini-1.5-pro-latest", "gemini-1.5-flash-latest", "gemini-1.0-pro")
    val groqModels = linkedSetOf("llama-3.2-3b-preview", "llama-3.2-1b-preview", "llama-3.1-70b-versatile", "llama-3.1-8b-instant", "gemma2-9b-it")
    val ollamaModels = linkedSetOf<String>()
    val bedrockModels = linkedSetOf(
        "anthropic.claude-3-5-sonnet-20240620-v1:0",
        "anthropic.claude-3-sonnet-20240229-v1:0",
        "anthropic.claude-3-haiku-20240307-v1:0",
        "anthropic.claude-instant-v1",
        "amazon.titan-text-express-v1",
        "amazon.titan-text-lite-v1",
        "ai21.j2-ultra-v1",
        "ai21.j2-mid-v1",
        "cohere.command-text-v14",
        "cohere.command-light-text-v14",
        "meta.llama2-13b-chat-v1",
        "meta.llama2-70b-chat-v1"
    )

    const val OPENAI_API_URL = "https://api.openai.com/v1/"
    const val ANTHROPIC_API_URL = "https://api.anthropic.com/"
    const val GOOGLE_API_URL = "https://generativelanguage.googleapis.com"
    const val GROQ_API_URL = "https://api.groq.com/openai/v1/"
    const val BEDROCK_BASE_URL = "https://bedrock-runtime.us-east-1.amazonaws.com" // Default to us-east-1

    fun getDefaultAPIUrl(apiType: ApiType) = when (apiType) {
        ApiType.OPENAI -> OPENAI_API_URL
        ApiType.ANTHROPIC -> ANTHROPIC_API_URL
        ApiType.GOOGLE -> GOOGLE_API_URL
        ApiType.GROQ -> GROQ_API_URL
        ApiType.OLLAMA -> ""
        ApiType.BEDROCK -> BEDROCK_BASE_URL
        ApiType.LOCAL -> "" // Local models don't need API URL
    }

    const val ANTHROPIC_MAXIMUM_TOKEN = 4096

    const val OPENAI_PROMPT =
        "You are a helpful, clever, and very friendly assistant. " +
            "You are familiar with various languages in the world. " +
            "You are to answer my questions precisely. "

    const val DEFAULT_PROMPT = "Your task is to answer my questions precisely."

    const val CHAT_TITLE_GENERATE_PROMPT =
        "Create a title that summarizes the chat. " +
            "The output must match the language that the user and the opponent is using, and should be less than 50 letters. " +
            "The output should only include the sentence in plain text without bullets or double asterisks. Do not use markdown syntax.\n" +
            "[Chat Content]\n"
}
