package com.iris.base.di

import com.iris.base.resource.AndroidResourceProvider
import com.iris.base.resource.ResourceProvider
import com.iris.base.threading.DispatchersProvider
import com.iris.base.threading.IrisDispatchersProvider
import com.iris.base.time.TimeConverter
import com.iris.base.time.TimeProvider
import com.iris.base.time.impl.DeviceTimeProvider
import com.iris.base.time.impl.StandardTimeConverter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface BaseHiltBindings {
    @Binds
    fun dispatchersProvider(impl: IrisDispatchersProvider): DispatchersProvider

    @Binds
    fun bindTimezoneProvider(impl: DeviceTimeProvider): TimeProvider

    @Binds
    fun bindTimeConverter(impl: StandardTimeConverter): TimeConverter

    @Binds
    fun resourceProvider(impl: AndroidResourceProvider): ResourceProvider
}