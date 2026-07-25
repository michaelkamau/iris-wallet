package com.iris.domain.di

import com.iris.domain.features.Features
import com.iris.domain.features.IrisFeatures
import dagger.Binds
import dagger.Module
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface IrisCoreBindingsModule {
    @Binds
    fun bindFeatures(features: IrisFeatures): Features
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface FeaturesEntryPoint {
    fun getFeatures(): Features
}
