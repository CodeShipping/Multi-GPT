package com.matrix.multigpt.localinference.service

import android.content.Context
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Async background summarizer for conversation context.
 * Generates summaries of old messages to preserve context within token limits.
 * 
 * This runs AFTER response generation completes, in the background,
 * so it doesn't impact user experience.
 */
@Singleton
class ConversationSummarizer @Inject constructor(
    private val context: Context
) {
    // Background scope that survives brief UI transitions but can be cancelled
    private val summarizerScope = CoroutineScope(
        Dispatchers.IO + SupervisorJob() + CoroutineName("ConversationSummarizer")
    )
    
    private val prefs by lazy {
        context.getSharedPreferences("conversation_context", Context.MODE_PRIVATE)
    }
    
    // Track if summarization is in progress to avoid duplicates
    private val activeSummarizations = mutableSetOf<Int>()
    
    /**
     * Called AFTER response generation completes.
     * Runs in background, doesn't block UI or inference.
     * 
     * @param chatRoomId Unique ID for the conversation
     * @param messages All messages in the conversation (role, content pairs)
     * @param inferenceService The inference service to use for summarization
     */
    fun maybeUpdateSummary(
        chatRoomId: Int,
        messages: List<Pair<String, String>>,
        inferenceService: LocalInferenceService
    ) {
        // Only summarize every 4 turns to minimize overhead
        // Also require at least 8 messages (4 turns)
        if (messages.size % 8 != 0 || messages.size < 8) return
        
        // Don't run concurrent summarizations for same chat
        synchronized(activeSummarizations) {
            if (chatRoomId in activeSummarizations) return
            activeSummarizations.add(chatRoomId)
        }
        
        summarizerScope.launch {
            try {
                android.util.Log.d("Summarizer", "Starting summary for chat $chatRoomId (${messages.size} messages)")
                
                // Get older messages (excluding recent 4 turns = 8 messages)
                val oldMessages = messages.dropLast(8)
                    .filter { !it.second.startsWith("Error:") && !it.second.startsWith("(No response") }
                    .takeLast(12)  // Cap at 12 messages for summary input
                
                if (oldMessages.size < 4) {
                    android.util.Log.d("Summarizer", "Not enough old messages to summarize")
                    return@launch
                }
                
                // Build concise representation of messages
                val messagesText = oldMessages.joinToString("\n") { (role, content) ->
                    val roleLabel = if (role.lowercase() == "user") "U" else "A"
                    "$roleLabel: ${content.take(150)}${if (content.length > 150) "..." else ""}"
                }
                
                val summaryPrompt = """Summarize this conversation briefly (2-3 sentences max). Focus on:
- Main topics discussed
- Key decisions or conclusions
- Important facts mentioned

Conversation:
$messagesText

Summary:"""
                
                // Use lower temperature for factual summarization
                val result = inferenceService.generateChatCompletionSync(
                    messages = listOf(ChatMessage(ChatRole.USER, summaryPrompt)),
                    temperature = 0.3f,
                    maxTokens = 100  // Keep summary short
                )
                
                if (result.isSuccess) {
                    val summary = result.getOrNull()
                        ?.trim()
                        ?.removePrefix("Summary:")
                        ?.trim()
                        ?.take(400)  // Hard limit
                        ?: ""
                    
                    if (summary.isNotBlank() && summary.length > 20) {
                        prefs.edit()
                            .putString("summary_$chatRoomId", summary)
                            .putInt("summary_msg_count_$chatRoomId", messages.size)
                            .putLong("summary_time_$chatRoomId", System.currentTimeMillis())
                            .apply()
                        
                        android.util.Log.d("Summarizer", "Summary saved for chat $chatRoomId: ${summary.take(50)}...")
                    }
                } else {
                    android.util.Log.w("Summarizer", "Summary generation failed: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                android.util.Log.w("Summarizer", "Summary generation error: ${e.message}")
            } finally {
                synchronized(activeSummarizations) {
                    activeSummarizations.remove(chatRoomId)
                }
            }
        }
    }
    
    /**
     * Get saved summary for a conversation.
     * 
     * @param chatRoomId The conversation ID
     * @return The summary text, or null if no summary exists
     */
    fun getSummary(chatRoomId: Int): String? {
        return prefs.getString("summary_$chatRoomId", null)
    }
    
    /**
     * Get the message count when summary was last created.
     */
    fun getSummaryMessageCount(chatRoomId: Int): Int {
        return prefs.getInt("summary_msg_count_$chatRoomId", 0)
    }
    
    /**
     * Check if a summary is fresh (created recently).
     */
    fun isSummaryFresh(chatRoomId: Int, maxAgeMs: Long = 3600_000L): Boolean {
        val summaryTime = prefs.getLong("summary_time_$chatRoomId", 0)
        return (System.currentTimeMillis() - summaryTime) < maxAgeMs
    }
    
    /**
     * Clear summary for a conversation (e.g., when conversation is deleted).
     */
    fun clearSummary(chatRoomId: Int) {
        prefs.edit()
            .remove("summary_$chatRoomId")
            .remove("summary_msg_count_$chatRoomId")
            .remove("summary_time_$chatRoomId")
            .apply()
    }
    
    /**
     * Cancel all active summarizations and cleanup.
     */
    fun cancel() {
        summarizerScope.cancel()
        synchronized(activeSummarizations) {
            activeSummarizations.clear()
        }
    }
}
