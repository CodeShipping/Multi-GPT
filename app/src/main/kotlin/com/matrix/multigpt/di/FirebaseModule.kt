package com.matrix.multigpt.di

import android.content.Context
import com.matrix.multigpt.util.FirebaseManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {
    
    @Provides
    @Singleton
    fun provideFirebaseManager(@ApplicationContext context: Context): FirebaseManager {
        val firebaseManager = FirebaseManager()
        firebaseManager.initialize(context)
        return firebaseManager
    }
}
