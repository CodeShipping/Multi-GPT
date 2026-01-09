package com.matrix.multigpt.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.matrix.multigpt.data.database.ChatDatabase
import com.matrix.multigpt.data.database.dao.ChatRoomDao
import com.matrix.multigpt.data.database.dao.MessageDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private const val DB_NAME = "chat"
    
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE messages ADD COLUMN model_name TEXT")
        }
    }

    @Provides
    fun provideChatRoomDao(chatDatabase: ChatDatabase): ChatRoomDao = chatDatabase.chatRoomDao()

    @Provides
    fun provideMessageDao(chatDatabase: ChatDatabase): MessageDao = chatDatabase.messageDao()

    @Provides
    @Singleton
    fun provideChatDatabase(@ApplicationContext appContext: Context): ChatDatabase = Room.databaseBuilder(
        appContext,
        ChatDatabase::class.java,
        DB_NAME
    )
        .addMigrations(MIGRATION_1_2)
        .build()
}
