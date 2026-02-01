package com.matrix.multigpt.localinference.service

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.codeshipping.llamakotlin.LlamaConfig
import org.codeshipping.llamakotlin.LlamaModel
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of LocalInferenceService using llama-kotlin-android.
 * Provides local AI inference capabilities using GGUF models.
 */
@Singleton
class LocalInferenceServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : LocalInferenceService {
    
    private var llamaModel: LlamaModel? = null
    private var loadedModelInfo: LoadedModelInfo? = null
    private val isGenerating = AtomicBoolean(false)
    private val shouldCancel = AtomicBoolean(false)
    
    // Flag to signal truncation happened - checked by LocalInferenceProvider
    @Volatile
    var lastGenerationWasTruncated = false
        private set
    
    override fun isModelLoaded(): Boolean {
        return llamaModel != null && loadedModelInfo != null
    }
    
    override suspend fun loadModel(
        modelPath: String,
        contextSize: Int,
        batchSize: Int,
        topK: Int,
        topP: Float
    ): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                // Unload any existing model first
                unloadModel()
                
                val modelFile = File(modelPath)
                if (!modelFile.exists()) {
                    return@withContext Result.failure(IllegalArgumentException("Model file not found: $modelPath"))
                }
                
                // Get ACTUAL device RAM (not JVM heap)
                val activityManager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                val memInfo = android.app.ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(memInfo)
                
                val totalRamMB = (memInfo.totalMem / (1024 * 1024)).toInt()
                val availableRamMB = (memInfo.availMem / (1024 * 1024)).toInt()
                val modelSizeMB = (modelFile.length() / (1024 * 1024)).toInt()
                
                // ABSOLUTE REQUIREMENT: Device must have sufficient total RAM
                // These models require consistent memory, not just "available" memory
                val requiredTotalRam = when {
                    modelSizeMB > 800 -> 8192  // 3B models need 8GB+
                    modelSizeMB > 600 -> 6144  // 1B+ models need 6GB+
                    else -> 4096               // 0.5B models need 4GB minimum
                }
                
                if (totalRamMB < requiredTotalRam) {
                    return@withContext Result.failure(
                        IllegalStateException(
                            "This device does not have enough RAM to run local AI models reliably. " +
                            "Total RAM: ${totalRamMB}MB, Required: ${requiredTotalRam}MB. " +
                            "Local AI requires devices with at least ${requiredTotalRam / 1024}GB of RAM. " +
                            "Please use cloud-based models instead (OpenAI, Claude, Gemini, etc.)."
                        )
                    )
                }
                
                // CRITICAL: Pre-flight memory check before loading
                // llama.cpp needs ~2-3x model size during inference
                val minimumRequired = modelSizeMB + 300 // Model + 300MB headroom
                if (memInfo.lowMemory) {
                    return@withContext Result.failure(
                        IllegalStateException("Device is in low memory state. Please close ALL other apps and try again.")
                    )
                }
                if (availableRamMB < minimumRequired) {
                    return@withContext Result.failure(
                        IllegalStateException(
                            "Not enough memory available to load this model. " +
                            "Available: ${availableRamMB}MB, Required: ~${minimumRequired}MB. " +
                            "Please close ALL other apps, then try again. " +
                            "If the problem persists, your device may not have enough total RAM (need ${requiredTotalRam / 1024}GB)."
                        )
                    )
                }
                
                // Load the model with performance-optimized settings
                val availableCores = Runtime.getRuntime().availableProcessors()
                // Use fewer threads on memory-constrained devices
                val threadCount = when {
                    availableRamMB < 1024 -> minOf(availableCores, 4)  // Low memory: limit threads
                    availableRamMB < 2048 -> minOf(availableCores, 6)  // Medium memory
                    else -> availableCores.coerceIn(2, 8)              // High memory
                }
                
                // Calculate RAM needed for model operations
                // llama.cpp needs significant memory during decode:
                // - Model weights: mostly via mmap but needs page cache
                // - KV cache: ctx * layers * hidden * 4 bytes * 2 (K+V)
                // - Scratch buffers for compute: proportional to model size
                // - Graph/tensor allocations during forward pass
                // 
                // Headroom scales with model size:
                // - 0.5B model: ~500MB headroom needed
                // - 1B model: ~800MB headroom needed  
                // - 3B model: ~1200MB headroom needed
                val headroomNeeded = (modelSizeMB * 1.2).toInt().coerceIn(400, 2000)
                val effectiveAvailable = availableRamMB - modelSizeMB - headroomNeeded
                
                android.util.Log.d("LocalInference", "Effective available for buffers: ${effectiveAvailable}MB (headroom: ${headroomNeeded}MB)")
                
                // CRITICAL CHECK: Refuse to load if not enough memory
                if (effectiveAvailable < 50) {
                    return@withContext Result.failure(
                        IllegalStateException(
                            "Not enough memory to run this model. " +
                            "Available: ${availableRamMB}MB, Model: ${modelSizeMB}MB, Need: ${modelSizeMB + headroomNeeded + 50}MB. " +
                            "Please close other apps or use a smaller model."
                        )
                    )
                }
                
                // Batch size based on effective available memory
                val optimalBatchSize = if (batchSize > 0) {
                    batchSize
                } else {
                    when {
                        effectiveAvailable < 150 -> 8
                        effectiveAvailable < 300 -> 16
                        effectiveAvailable < 500 -> 32
                        effectiveAvailable < 800 -> 64
                        effectiveAvailable < 1200 -> 128
                        effectiveAvailable < 2000 -> 256
                        else -> 512
                    }
                }
                
                // IMPORTANT: llama.cpp internally reserves ~300-400 tokens for generation
                // So minimum viable context is 512 (to have 100+ tokens for prompt)
                val MINIMUM_CONTEXT = 512  // Native library requires this minimum to function
                
                // Context size based on available memory
                // KV cache roughly: ctx * 200KB for 0.5B model, ctx * 400KB for 1B model
                val baseContextSize = when {
                    effectiveAvailable < 150 -> 512    // Minimum viable
                    effectiveAvailable < 300 -> 512    // Still minimum
                    effectiveAvailable < 500 -> 768    // Slight increase
                    effectiveAvailable < 800 -> 1024   // Medium-low
                    effectiveAvailable < 1200 -> 1536  // Medium
                    effectiveAvailable < 2000 -> 2048  // Good
                    else -> contextSize                 // Use requested
                }
                
                // Apply minimum and user-requested maximum
                val safeContextSize = minOf(contextSize, baseContextSize).coerceAtLeast(MINIMUM_CONTEXT)
                
                android.util.Log.d("LocalInference", "Device: ${totalRamMB}MB total, ${availableRamMB}MB available")
                android.util.Log.d("LocalInference", "Model: ${modelSizeMB}MB, Loading with threads=$threadCount, batch=$optimalBatchSize, ctx=$safeContextSize")
                
                // Check if we have enough RAM for the model
                val estimatedModelRam = modelSizeMB + (safeContextSize * 2 / 1024) // Model + context overhead
                if (availableRamMB < estimatedModelRam + 256) { // Need 256MB headroom
                    android.util.Log.w("LocalInference", "Warning: Low memory! Available: ${availableRamMB}MB, Estimated need: ${estimatedModelRam + 256}MB")
                }
                
                val model = LlamaModel.load(modelPath) {
                    this.contextSize = safeContextSize
                    this.threads = threadCount
                    this.threadsBatch = minOf(threadCount, 4) // Batch threads shouldn't exceed regular threads
                    this.batchSize = optimalBatchSize
                    this.temperature = 0.7f
                    this.topP = topP
                    this.topK = topK
                    this.useMmap = true   // Memory-mapped for faster loading
                    this.useMlock = false // Don't lock (uses too much memory)
                }
                
                llamaModel = model
                loadedModelInfo = LoadedModelInfo(
                    modelId = modelFile.nameWithoutExtension,
                    modelPath = modelPath,
                    modelName = modelFile.nameWithoutExtension,
                    contextSize = safeContextSize,
                    loadedAt = System.currentTimeMillis()
                )
                
                android.util.Log.i("LocalInference", "Model loaded successfully with ctx=$safeContextSize, batch=$optimalBatchSize")
                Result.success(true)
            } catch (e: Exception) {
                android.util.Log.e("LocalInference", "Failed to load model", e)
                Result.failure(e)
            }
        }
    }
    
    override fun unloadModel() {
        try {
            llamaModel?.close()
        } catch (e: Exception) {
            // Ignore close errors
        }
        llamaModel = null
        loadedModelInfo = null
    }
    
    override fun generateChatCompletion(
        messages: List<ChatMessage>,
        temperature: Float,
        maxTokens: Int
    ): Flow<String> = flow<String> {
        val model = llamaModel
            ?: throw IllegalStateException("No model loaded. Call loadModel() first.")
        
        android.util.Log.d("LocalInference", "generateChatCompletion started")
        
        // PRE-FLIGHT CHECK: Verify we have enough memory BEFORE calling native code
        val activityManager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        
        if (memInfo.lowMemory || (memInfo.availMem / (1024 * 1024)) < 400) {
            throw IllegalStateException(
                "Not enough memory for generation. Available: ${memInfo.availMem / (1024 * 1024)}MB. " +
                "Please close other apps and try again."
            )
        }
        
        // Cancel any previous generation and wait for it to finish
        if (model.isGenerating) {
            android.util.Log.d("LocalInference", "Cancelling previous generation...")
            model.cancelGeneration()
            // Wait for generation to actually stop (up to 3 seconds)
            repeat(30) {
                if (!model.isGenerating) return@repeat
                kotlinx.coroutines.delay(100)
            }
            android.util.Log.d("LocalInference", "Previous generation cancelled")
        }
        
        isGenerating.set(true)
        shouldCancel.set(false)
        lastGenerationWasTruncated = false  // Reset flag
        
        try {
            // Format messages into a prompt based on detected model type
            var prompt = formatChatPrompt(messages)
            android.util.Log.d("LocalInference", "Prompt formatted, length: ${prompt.length}")
            
            // CRITICAL SAFEGUARD: Limit prompt length to prevent native crash
            // Keep LATEST messages instead of oldest
            val batchSize = loadedModelInfo?.let { 
                when {
                    it.contextSize >= 1536 -> 128
                    it.contextSize >= 1024 -> 64
                    else -> 32
                }
            } ?: 128
            val safeTokenLimit = (batchSize * 0.8).toInt()
            val safeCharLimit = safeTokenLimit * 4
            
            if (prompt.length > safeCharLimit) {
                lastGenerationWasTruncated = true  // Set flag - truncation happening
                android.util.Log.w("LocalInference", "Prompt too long (${prompt.length} chars), truncating to keep latest messages (~$safeTokenLimit tokens)")
                
                // Find system message (always keep this)
                val systemEnd = prompt.indexOf("<|im_end|>")
                val systemPart = if (systemEnd > 0) {
                    prompt.substring(0, systemEnd + "<|im_end|>\n".length)
                } else {
                    "<|im_start|>system\nYou are a helpful AI assistant.<|im_end|>\n"
                }
                
                // Get the rest after system message
                val restOfPrompt = if (systemEnd > 0) {
                    prompt.substring(systemEnd + "<|im_end|>\n".length)
                } else {
                    prompt
                }
                
                // Keep from END to fit in remaining budget
                val remainingBudget = safeCharLimit - systemPart.length - 30 // 30 for assistant marker
                
                if (remainingBudget > 50 && restOfPrompt.length > remainingBudget) {
                    // Find a clean message boundary from the end
                    val startCut = restOfPrompt.length - remainingBudget
                    val cleanStart = restOfPrompt.indexOf("<|im_start|>", startCut)
                    
                    val latestMessages = if (cleanStart > 0) {
                        restOfPrompt.substring(cleanStart)
                    } else {
                        // Just take latest portion
                        restOfPrompt.takeLast(remainingBudget)
                    }
                    
                    prompt = systemPart + latestMessages
                    // Ensure it ends with assistant prompt
                    if (!prompt.endsWith("<|im_start|>assistant\n")) {
                        prompt = prompt.trimEnd() + "\n<|im_start|>assistant\n"
                    }
                } else {
                    // Very tight budget - just keep last user message
                    val lastUserStart = restOfPrompt.lastIndexOf("<|im_start|>user")
                    if (lastUserStart >= 0) {
                        val lastUserEnd = restOfPrompt.indexOf("<|im_end|>", lastUserStart)
                        val lastUserMsg = if (lastUserEnd > lastUserStart) {
                            restOfPrompt.substring(lastUserStart, lastUserEnd + "<|im_end|>".length)
                        } else {
                            restOfPrompt.substring(lastUserStart)
                        }
                        prompt = systemPart + lastUserMsg + "\n<|im_start|>assistant\n"
                    }
                }
                
                android.util.Log.d("LocalInference", "Truncated prompt (keeping latest): ${prompt.length} chars")
                android.util.Log.d("LocalInference", "Truncated preview: ${prompt.takeLast(200)}")
            }
            
            // Use atomic counter to safely track tokens across threads
            val tokenCount = java.util.concurrent.atomic.AtomicInteger(0)
            val startTime = System.currentTimeMillis()
            
            android.util.Log.d("LocalInference", "Starting generateStream")
            
            try {
                // Collect tokens without inner timeout - let flow complete naturally
                // The outer caller (LocalInferenceProvider) has its own timeout
                model.generateStream(prompt).collect { token ->
                    val count = tokenCount.incrementAndGet()
                    
                    // Log prefill completion on first token
                    if (count == 1) {
                        val prefillTime = System.currentTimeMillis() - startTime
                        android.util.Log.d("LocalInference", "First token after ${prefillTime}ms")
                    }
                    
                    if (!shouldCancel.get()) {
                        if (count <= 3 || count % 50 == 0) {
                            android.util.Log.d("LocalInference", "Token #$count: '$token'")
                        }
                        emit(token)
                    }
                }
                
                android.util.Log.d("LocalInference", "Stream completed naturally. Total tokens: ${tokenCount.get()}")
                
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Flow was cancelled (either by timeout from caller or user action)
                val count = tokenCount.get()
                android.util.Log.d("LocalInference", "Stream cancelled after $count tokens")
                if (count == 0) {
                    throw IllegalStateException("Model generation was cancelled before producing any output.")
                }
                // If we got some tokens, don't throw - we'll return what we have
            } catch (e: Exception) {
                android.util.Log.e("LocalInference", "Generate error: ${e.message}", e)
                throw e
            }
            
            // If no tokens were emitted, provide guidance
            if (tokenCount.get() == 0) {
                android.util.Log.w("LocalInference", "No tokens generated!")
                emit("(No response generated - this model may require a different prompt format. Try Llama 3.x compatible models.)")
            }
            
        } finally {
            isGenerating.set(false)
            android.util.Log.d("LocalInference", "generateChatCompletion finished")
        }
    }.flowOn(Dispatchers.IO)
    
    override suspend fun generateChatCompletionSync(
        messages: List<ChatMessage>,
        temperature: Float,
        maxTokens: Int
    ): Result<String> = withContext(Dispatchers.IO) {
        val model = llamaModel
            ?: return@withContext Result.failure(IllegalStateException("No model loaded. Call loadModel() first."))
        
        isGenerating.set(true)
        shouldCancel.set(false)
        lastGenerationWasTruncated = false  // Reset flag
        
        try {
            var prompt = formatChatPrompt(messages)
            
            // CRITICAL SAFEGUARD: Limit prompt length to prevent native crash
            // batch=128, safe limit = batch * 0.8 = ~100 tokens
            // Estimate: 4 chars per token, so max ~400 chars
            val batchSize = loadedModelInfo?.let { 
                // Estimate batch from context size
                when {
                    it.contextSize >= 1536 -> 128
                    it.contextSize >= 1024 -> 64
                    else -> 32
                }
            } ?: 128
            val safeTokenLimit = (batchSize * 0.8).toInt()  // 80% of batch for safety
            val safeCharLimit = safeTokenLimit * 4  // ~4 chars per token
            
            if (prompt.length > safeCharLimit) {
                lastGenerationWasTruncated = true  // Set flag - truncation happening
                android.util.Log.w("LocalInference", "Prompt too long (${prompt.length} chars), truncating to keep latest messages (~$safeTokenLimit tokens)")
                
                // Find system message (always keep this)
                val systemEnd = prompt.indexOf("<|im_end|>")
                val systemPart = if (systemEnd > 0) {
                    prompt.substring(0, systemEnd + "<|im_end|>\n".length)
                } else {
                    "<|im_start|>system\nYou are a helpful AI assistant.<|im_end|>\n"
                }
                
                // Get the rest after system message
                val restOfPrompt = if (systemEnd > 0) {
                    prompt.substring(systemEnd + "<|im_end|>\n".length)
                } else {
                    prompt
                }
                
                // Keep from END to fit in remaining budget
                val remainingBudget = safeCharLimit - systemPart.length - 30 // 30 for assistant marker
                
                if (remainingBudget > 50 && restOfPrompt.length > remainingBudget) {
                    // Find a clean message boundary from the end
                    val startCut = restOfPrompt.length - remainingBudget
                    val cleanStart = restOfPrompt.indexOf("<|im_start|>", startCut)
                    
                    val latestMessages = if (cleanStart > 0) {
                        restOfPrompt.substring(cleanStart)
                    } else {
                        // Just take latest portion
                        restOfPrompt.takeLast(remainingBudget)
                    }
                    
                    prompt = systemPart + latestMessages
                    // Ensure it ends with assistant prompt
                    if (!prompt.endsWith("<|im_start|>assistant\n")) {
                        prompt = prompt.trimEnd() + "\n<|im_start|>assistant\n"
                    }
                } else {
                    // Very tight budget - just keep last user message
                    val lastUserStart = restOfPrompt.lastIndexOf("<|im_start|>user")
                    if (lastUserStart >= 0) {
                        val lastUserEnd = restOfPrompt.indexOf("<|im_end|>", lastUserStart)
                        val lastUserMsg = if (lastUserEnd > lastUserStart) {
                            restOfPrompt.substring(lastUserStart, lastUserEnd + "<|im_end|>".length)
                        } else {
                            restOfPrompt.substring(lastUserStart)
                        }
                        prompt = systemPart + lastUserMsg + "\n<|im_start|>assistant\n"
                    }
                }
                
                android.util.Log.d("LocalInference", "Truncated prompt (keeping latest): ${prompt.length} chars")
                android.util.Log.d("LocalInference", "Truncated preview: ${prompt.takeLast(200)}")
            }
            
            val config = LlamaConfig {
                this.temperature = temperature
                this.maxTokens = maxTokens
            }
            
            val result = model.generate(prompt, config)
            
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            isGenerating.set(false)
        }
    }
    
    override fun getLoadedModelId(): String? {
        return loadedModelInfo?.modelId
    }
    
    override fun getModelInfo(): LoadedModelInfo? {
        return loadedModelInfo
    }
    
    override fun cancelGeneration() {
        shouldCancel.set(true)
    }
    
    override fun isGenerating(): Boolean {
        return isGenerating.get()
    }
    
    /**
     * Format chat messages into a prompt string.
     * Uses the model's embedded chat template from GGUF metadata for automatic format detection.
     * Falls back to ChatML format if no template is available or if native call fails.
     */
    private fun formatChatPrompt(messages: List<ChatMessage>): String {
        val model = llamaModel
        
        // Calculate total message content size
        val totalContentSize = messages.sumOf { it.content.length }
        
        // IMPORTANT: Skip native template for large prompts
        // The native applyChatTemplate has a buffer overflow bug that corrupts output 
        // when the JSON input exceeds ~500 characters
        val useNativeTemplate = totalContentSize < 400  // Safe threshold
        
        if (!useNativeTemplate) {
            android.util.Log.w("LocalInference", "Skipping native template due to large prompt size ($totalContentSize chars)")
        }
        
        // Try to use native chat template first (reads from GGUF metadata)
        // This can fail if the model was freed (e.g., app was backgrounded)
        if (model != null && useNativeTemplate) {
            try {
                // First, verify the model is still valid by checking a simple property
                // If this throws, the native context is invalid
                val chatTemplate: String
                try {
                    chatTemplate = model.getChatTemplate()
                } catch (e: Error) {
                    // Native error - model context is invalid, clear reference
                    android.util.Log.e("LocalInference", "Model context invalid (freed?), clearing reference", e)
                    llamaModel = null
                    loadedModelInfo = null
                    throw IllegalStateException("Model was unloaded. Please reload the model.")
                }
                
                android.util.Log.d("LocalInference", "Chat template available: ${chatTemplate.isNotEmpty()}")
                
                if (chatTemplate.isNotEmpty()) {
                    // Convert messages to JSON format for native template application
                    val messagesJson = buildMessagesJson(messages)
                    android.util.Log.d("LocalInference", "Applying native chat template, JSON size: ${messagesJson.length}")
                    
                    val prompt: String
                    try {
                        prompt = model.applyChatTemplate(messagesJson, true)
                    } catch (e: Error) {
                        // Native error during template application
                        android.util.Log.e("LocalInference", "Native template application crashed", e)
                        llamaModel = null
                        loadedModelInfo = null
                        throw IllegalStateException("Model was unloaded. Please reload the model.")
                    }
                    
                    if (prompt.isNotEmpty()) {
                        // Validate the prompt - check for corruption signs
                        val firstUserContent = messages.firstOrNull { it.role == ChatRole.USER }?.content?.take(30)
                        
                        // Check for common corruption patterns
                        val isMalformed = when {
                            firstUserContent != null && prompt.contains("<|start_header_id|>$firstUserContent") -> {
                                android.util.Log.w("LocalInference", "Template missing role after header")
                                true
                            }
                            // Template output too short (less than any meaningful response)
                            prompt.length < 30 -> {
                                android.util.Log.w("LocalInference", "Template output too short: ${prompt.length}")
                                true
                            }
                            // Check for corruption: special markers should have proper text after them
                            prompt.contains("<|im_start|>okenizer") || 
                            prompt.contains("<|im_start|>?") ||
                            prompt.matches(Regex(".*<\\|im_start\\|>[^a-z].*")) -> {
                                android.util.Log.w("LocalInference", "Detected corrupted template output")
                                true
                            }
                            else -> false
                        }
                        
                        if (!isMalformed) {
                            android.util.Log.d("LocalInference", "Native template applied successfully, length: ${prompt.length}")
                            android.util.Log.d("LocalInference", "Prompt preview: ${prompt.take(200)}")
                            return prompt
                        } else {
                            android.util.Log.w("LocalInference", "Native template produced malformed output, falling back")
                        }
                    }
                }
            } catch (e: IllegalStateException) {
                // Re-throw model unloaded errors
                throw e
            } catch (e: Exception) {
                android.util.Log.w("LocalInference", "Failed to apply native chat template: ${e.message}")
                // Fall through to default format
            }
        }
        
        // Detect model type from filename and use appropriate fallback format
        val modelName = loadedModelInfo?.modelName?.lowercase() ?: ""
        val modelPath = loadedModelInfo?.modelPath?.lowercase() ?: ""
        val combined = "$modelName $modelPath"
        
        return when {
            combined.contains("llama-2") || combined.contains("llama2") || 
            combined.contains("llama_2") || combined.contains("llama 2") -> {
                android.util.Log.d("LocalInference", "Using fallback Llama 2 format")
                formatLlama2Prompt(messages)
            }
            combined.contains("mistral") || combined.contains("mixtral") -> {
                android.util.Log.d("LocalInference", "Using fallback Mistral format")
                formatMistralPrompt(messages)
            }
            combined.contains("qwen") || combined.contains("yi-") -> {
                android.util.Log.d("LocalInference", "Using fallback ChatML format")
                formatChatMLPrompt(messages)
            }
            combined.contains("gemma") -> {
                android.util.Log.d("LocalInference", "Using fallback Gemma format")
                formatGemmaPrompt(messages)
            }
            combined.contains("phi") -> {
                android.util.Log.d("LocalInference", "Using fallback Phi format")
                formatPhiPrompt(messages)
            }
            else -> {
                // ChatML is the most universal format - works with many models
                android.util.Log.d("LocalInference", "Using fallback ChatML format (universal)")
                formatChatMLPrompt(messages)
            }
        }
    }
    
    /**
     * Convert messages to JSON format for native template application.
     */
    private fun buildMessagesJson(messages: List<ChatMessage>): String {
        // Add default system message if not present
        val hasSystem = messages.any { it.role == ChatRole.SYSTEM }
        val allMessages = if (!hasSystem) {
            listOf(ChatMessage(ChatRole.SYSTEM, "You are a helpful AI assistant.")) + messages
        } else {
            messages
        }
        
        return buildString {
            append("[")
            allMessages.forEachIndexed { index, message ->
                if (index > 0) append(",")
                val role = when (message.role) {
                    ChatRole.SYSTEM -> "system"
                    ChatRole.USER -> "user"
                    ChatRole.ASSISTANT -> "assistant"
                }
                // Escape content for JSON
                val content = message.content
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t")
                append("{\"role\":\"$role\",\"content\":\"$content\"}")
            }
            append("]")
        }
    }
    
    /**
     * Fallback Llama 3.x Instruct format.
     */
    private fun formatLlama3Prompt(messages: List<ChatMessage>): String {
        return buildString {
            append("<|begin_of_text|>")
            
            val hasSystem = messages.any { it.role == ChatRole.SYSTEM }
            if (!hasSystem) {
                append("<|start_header_id|>system<|end_header_id|>\n\n")
                append("You are a helpful AI assistant.")
                append("<|eot_id|>")
            }
            
            for (message in messages) {
                when (message.role) {
                    ChatRole.SYSTEM -> {
                        append("<|start_header_id|>system<|end_header_id|>\n\n")
                        append(message.content)
                        append("<|eot_id|>")
                    }
                    ChatRole.USER -> {
                        append("<|start_header_id|>user<|end_header_id|>\n\n")
                        append(message.content)
                        append("<|eot_id|>")
                    }
                    ChatRole.ASSISTANT -> {
                        append("<|start_header_id|>assistant<|end_header_id|>\n\n")
                        append(message.content)
                        append("<|eot_id|>")
                    }
                }
            }
            
            append("<|start_header_id|>assistant<|end_header_id|>\n\n")
        }
    }
    
    /**
     * Llama 2 Chat format.
     */
    private fun formatLlama2Prompt(messages: List<ChatMessage>): String {
        return buildString {
            val systemMessage = messages.find { it.role == ChatRole.SYSTEM }?.content 
                ?: "You are a helpful AI assistant."
            
            var isFirst = true
            for (message in messages.filter { it.role != ChatRole.SYSTEM }) {
                when (message.role) {
                    ChatRole.USER -> {
                        append("[INST] ")
                        if (isFirst) {
                            append("<<SYS>>\n$systemMessage\n<</SYS>>\n\n")
                            isFirst = false
                        }
                        append(message.content)
                        append(" [/INST]")
                    }
                    ChatRole.ASSISTANT -> {
                        append(" ")
                        append(message.content)
                        append(" </s><s>")
                    }
                    else -> {}
                }
            }
        }
    }
    
    /**
     * Mistral Instruct format.
     */
    private fun formatMistralPrompt(messages: List<ChatMessage>): String {
        return buildString {
            append("<s>")
            for (message in messages) {
                when (message.role) {
                    ChatRole.SYSTEM, ChatRole.USER -> {
                        append("[INST] ")
                        append(message.content)
                        append(" [/INST]")
                    }
                    ChatRole.ASSISTANT -> {
                        append(message.content)
                        append("</s>")
                    }
                }
            }
        }
    }
    
    /**
     * ChatML format (OpenAI/Qwen style).
     */
    private fun formatChatMLPrompt(messages: List<ChatMessage>): String {
        return buildString {
            val hasSystem = messages.any { it.role == ChatRole.SYSTEM }
            if (!hasSystem) {
                append("<|im_start|>system\nYou are a helpful AI assistant.<|im_end|>\n")
            }
            for (message in messages) {
                val role = when (message.role) {
                    ChatRole.SYSTEM -> "system"
                    ChatRole.USER -> "user"
                    ChatRole.ASSISTANT -> "assistant"
                }
                append("<|im_start|>$role\n${message.content}<|im_end|>\n")
            }
            append("<|im_start|>assistant\n")
        }
    }
    
    /**
     * Gemma format.
     */
    private fun formatGemmaPrompt(messages: List<ChatMessage>): String {
        return buildString {
            append("<bos>")
            for (message in messages) {
                when (message.role) {
                    ChatRole.USER -> {
                        append("<start_of_turn>user\n${message.content}<end_of_turn>\n")
                    }
                    ChatRole.ASSISTANT -> {
                        append("<start_of_turn>model\n${message.content}<end_of_turn>\n")
                    }
                    ChatRole.SYSTEM -> {
                        // Gemma doesn't have explicit system
                    }
                }
            }
            append("<start_of_turn>model\n")
        }
    }
    
    /**
     * Phi format (Microsoft).
     */
    private fun formatPhiPrompt(messages: List<ChatMessage>): String {
        return buildString {
            val systemMessage = messages.find { it.role == ChatRole.SYSTEM }?.content 
                ?: "You are a helpful AI assistant."
            append("<|system|>\n$systemMessage<|end|>\n")
            for (message in messages.filter { it.role != ChatRole.SYSTEM }) {
                when (message.role) {
                    ChatRole.USER -> append("<|user|>\n${message.content}<|end|>\n")
                    ChatRole.ASSISTANT -> append("<|assistant|>\n${message.content}<|end|>\n")
                    else -> {}
                }
            }
            append("<|assistant|>\n")
        }
    }
}
