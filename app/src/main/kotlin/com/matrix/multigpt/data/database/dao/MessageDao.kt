package com.matrix.multigpt.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.matrix.multigpt.data.database.entity.Message

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE chat_id=:chatInt")
    suspend fun loadMessages(chatInt: Int): List<Message>

    @Insert
    suspend fun addMessages(vararg messages: Message)

    @Update
    suspend fun editMessages(vararg message: Message)

    @Delete
    suspend fun deleteMessages(vararg message: Message)

    /**
     * Get distinct platforms actually used in a chat (from AI responses).
     * Excludes null platforms (user messages).
     */
    @Query("SELECT DISTINCT platform_type FROM messages WHERE chat_id=:chatId AND platform_type IS NOT NULL")
    suspend fun getUsedPlatforms(chatId: Int): List<String>

    /**
     * Get all distinct platforms used across all chats.
     * Returns map of chatId to used platforms.
     */
    @Query("SELECT chat_id, GROUP_CONCAT(DISTINCT platform_type) as platforms FROM messages WHERE platform_type IS NOT NULL GROUP BY chat_id")
    suspend fun getAllChatsUsedPlatforms(): List<ChatUsedPlatforms>
}

/**
 * Data class for holding chat id and its used platforms.
 */
data class ChatUsedPlatforms(
    @androidx.room.ColumnInfo(name = "chat_id")
    val chatId: Int,
    @androidx.room.ColumnInfo(name = "platforms")
    val platforms: String?
)
