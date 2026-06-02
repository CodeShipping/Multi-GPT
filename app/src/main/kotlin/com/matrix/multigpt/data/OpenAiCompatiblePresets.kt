package com.matrix.multigpt.data

/**
 * Catalog of popular OpenAI-compatible providers.
 * All follow the OpenAI Chat Completions wire format:
 * POST {baseUrl}v1/chat/completions with Authorization: Bearer <key>
 */
object OpenAiCompatiblePresets {

    data class Preset(
        val id: String,
        val name: String,
        val baseUrl: String,
        val exampleModel: String,
        val apiKeyUrl: String,
        val notes: String,
        val emoji: String = "🤖"
    )

    val PRESETS: List<Preset> = listOf(
        Preset(
            id = "openrouter",
            name = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/",
            exampleModel = "openai/gpt-4o-mini",
            apiKeyUrl = "https://openrouter.ai/keys",
            notes = "One key, hundreds of models.",
            emoji = "🛣️"
        ),
        Preset(
            id = "deepseek",
            name = "DeepSeek",
            baseUrl = "https://api.deepseek.com/",
            exampleModel = "deepseek-chat",
            apiKeyUrl = "https://platform.deepseek.com/api_keys",
            notes = "Cheap & capable. Free starter credits.",
            emoji = "🐳"
        ),
        Preset(
            id = "mistral",
            name = "Mistral",
            baseUrl = "https://api.mistral.ai/",
            exampleModel = "mistral-small-latest",
            apiKeyUrl = "https://console.mistral.ai/api-keys/",
            notes = "European AI, Mistral & Codestral.",
            emoji = "🇫🇷"
        ),
        Preset(
            id = "together",
            name = "Together AI",
            baseUrl = "https://api.together.xyz/",
            exampleModel = "meta-llama/Llama-3.3-70B-Instruct-Turbo",
            apiKeyUrl = "https://api.together.xyz/settings/api-keys",
            notes = "Open-source models at scale.",
            emoji = "🤝"
        ),
        Preset(
            id = "xai",
            name = "xAI (Grok)",
            baseUrl = "https://api.x.ai/",
            exampleModel = "grok-2-latest",
            apiKeyUrl = "https://console.x.ai/",
            notes = "Grok by xAI.",
            emoji = "🦾"
        ),
        Preset(
            id = "perplexity",
            name = "Perplexity",
            baseUrl = "https://api.perplexity.ai/",
            exampleModel = "llama-3.1-sonar-small-128k-online",
            apiKeyUrl = "https://www.perplexity.ai/settings/api",
            notes = "Built-in web search.",
            emoji = "🔎"
        ),
        Preset(
            id = "fireworks",
            name = "Fireworks AI",
            baseUrl = "https://api.fireworks.ai/inference/",
            exampleModel = "accounts/fireworks/models/llama-v3p1-70b-instruct",
            apiKeyUrl = "https://fireworks.ai/account/api-keys",
            notes = "Fast open-source inference.",
            emoji = "🎆"
        ),
        Preset(
            id = "sambanova",
            name = "SambaNova",
            baseUrl = "https://api.sambanova.ai/",
            exampleModel = "Meta-Llama-3.1-70B-Instruct",
            apiKeyUrl = "https://cloud.sambanova.ai/apis",
            notes = "Very fast Llama inference.",
            emoji = "🚀"
        ),
        Preset(
            id = "novita",
            name = "Novita AI",
            baseUrl = "https://api.novita.ai/",
            exampleModel = "meta-llama/llama-3.1-70b-instruct",
            apiKeyUrl = "https://novita.ai/dashboard/key",
            notes = "Cheap open-source inference.",
            emoji = "🌐"
        )
    )

    fun byId(id: String?): Preset? = id?.let { pid -> PRESETS.firstOrNull { it.id == pid } }
}
