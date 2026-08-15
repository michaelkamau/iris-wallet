package com.iris.data.di

import com.iris.data.sync.NoOpSyncRemoteDataSource
import com.iris.data.sync.RoomSyncLocalDataSource
import com.iris.data.sync.SyncLocalDataSource
import com.iris.data.sync.SyncRemoteDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Bindings of the sync engine.
 *
 * The remote data source defaults to a no-op implementation, a build shipping a
 * cloud backend replaces this binding with its own one.
 */
@Module
@InstallIn(SingletonComponent::class)
interface SyncModule {

    @Binds
    fun bindSyncLocalDataSource(impl: RoomSyncLocalDataSource): SyncLocalDataSource

    @Binds
    fun bindSyncRemoteDataSource(impl: NoOpSyncRemoteDataSource): SyncRemoteDataSource
}
