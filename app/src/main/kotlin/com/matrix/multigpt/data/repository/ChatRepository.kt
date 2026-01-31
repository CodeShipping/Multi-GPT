package com.matrix.multigpt.data.repository

import com.matrix.multigpt.data.database.entity.ChatRoom
import com.matrix.multigpt.data.database.entity.Message
import com.matrix.multigpt.data.dto.ApiState
import com.matrix.multigpt.data.model.ApiType
import kotlinx.coroutines.flow.Flow

interface ChatRepository {

    suspend fun completeOpenAIChat(question: Message, history: List<Message>): Flow<ApiState>
    suspend fun completeAnthropicChat(question: Message, history: List<Message>): Flow<ApiState>
    suspend fun completeGoogleChat(question: Message, history: List<Message>): Flow<ApiState>
    suspend fun completeGroqChat(question: Message, history: List<Message>): Flow<ApiState>
    suspend fun completeOllamaChat(question: Message, history: List<Message>): Flow<ApiState>
    suspend fun completeBedrockChat(question: Message, history: List<Message>): Flow<ApiState>
    suspend fun fetchChatList(): List<ChatRoom>
    suspend fun fetchMessages(chatId: Int): List<Message>
    fun generateDefaultChatTitle(messages: List<Message>): String?
    suspend fun updateChatTitle(chatRoom: ChatRoom, title: String)
    suspend fun saveChat(chatRoom: ChatRoom, messages: List<Message>): ChatRoom
    suspend fun deleteChats(chatRooms: List<ChatRoom>)
    
    /**
     * Get the actually used platforms for a specific chat.
     * Returns platforms from messages that have actual AI responses.
     */
    suspend fun getUsedPlatformsForChat(chatId: Int): List<ApiType>
    
    /**
     * Get all actually used platforms for all chats.
     * Returns a map of chatId to list of used platforms.
     */
    suspend fun getAllChatsUsedPlatforms(): Map<Int, List<ApiType>>
}
