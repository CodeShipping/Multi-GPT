package com.matrix.multigpt.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.matrix.multigpt.data.database.dao.ChatRoomDao
import com.matrix.multigpt.data.database.dao.MessageDao
import com.matrix.multigpt.data.network.AnthropicAPI
import com.matrix.multigpt.data.network.BedrockAPI
import com.matrix.multigpt.data.repository.ChatRepository
import com.matrix.multigpt.data.repository.ChatRepositoryImpl
import com.matrix.multigpt.data.repository.SettingRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ChatRepositoryModule {


    @Provides
    @Singleton
    fun provideChatRepository(
        @ApplicationContext appContext: Context,
        chatRoomDao: ChatRoomDao,
        messageDao: MessageDao,
        settingRepository: SettingRepository,
        anthropicAPI: AnthropicAPI,
        bedrockAPI: BedrockAPI
    ): ChatRepository = ChatRepositoryImpl(appContext, chatRoomDao, messageDao, settingRepository, anthropicAPI, bedrockAPI)
}
