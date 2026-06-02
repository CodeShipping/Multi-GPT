package com.matrix.multigpt.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.matrix.multigpt.data.database.dao.ChatRoomDao
import com.matrix.multigpt.data.database.dao.MessageDao
import com.matrix.multigpt.data.database.entity.APITypeConverter
import com.matrix.multigpt.data.database.entity.ChatRoom
import com.matrix.multigpt.data.database.entity.Message

@Database(entities = [ChatRoom::class, Message::class], version = 3, exportSchema = false)
@TypeConverters(APITypeConverter::class)
abstract class ChatDatabase : RoomDatabase() {

    abstract fun chatRoomDao(): ChatRoomDao
    abstract fun messageDao(): MessageDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chats ADD COLUMN system_prompt TEXT DEFAULT NULL")
            }
        }
    }
}
