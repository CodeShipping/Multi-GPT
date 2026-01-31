package com.matrix.multigpt.localinference.di

import com.matrix.multigpt.localinference.data.network.ModelCatalogService
import com.matrix.multigpt.localinference.data.network.ModelCatalogServiceImpl
import com.matrix.multigpt.localinference.data.repository.LocalModelRepository
import com.matrix.multigpt.localinference.data.repository.LocalModelRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for local inference feature.
 * Provides dependencies for model catalog and download management.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LocalInferenceModule {

    @Binds
    @Singleton
    abstract fun bindModelCatalogService(
        impl: ModelCatalogServiceImpl
    ): ModelCatalogService

    @Binds
    @Singleton
    abstract fun bindLocalModelRepository(
        impl: LocalModelRepositoryImpl
    ): LocalModelRepository
}
