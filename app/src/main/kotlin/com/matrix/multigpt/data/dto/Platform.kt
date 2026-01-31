package com.matrix.multigpt.data.dto

import com.matrix.multigpt.data.ModelConstants.getDefaultAPIUrl
import com.matrix.multigpt.data.model.ApiType

data class Platform(
    val name: ApiType,
    val selected: Boolean = false,
    val enabled: Boolean = false,
    val apiUrl: String = getDefaultAPIUrl(name),
    val token: String? = null,
    val model: String? = null,
    val temperature: Float? = null,
    val topP: Float? = null,
    val systemPrompt: String? = null,
    // Local AI specific settings
    val topK: Int? = null,
    val batchSize: Int? = null,
    val contextSize: Int? = null
)
