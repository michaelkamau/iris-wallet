package com.iris.data.sync

import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SyncRemoteDataSource] used when no cloud backend is configured, for example in
 * builds shipped without Google services.
 *
 * It reports no remote changes and drops uploads, which keeps every consumer of
 * the sync engine working while sync is effectively disabled.
 */
@Singleton
class NoOpSyncRemoteDataSource @Inject constructor() : SyncRemoteDataSource {

    override suspend fun fetchChanges(sinceCursor: Long, limit: Int): SyncPage =
        SyncPage(records = emptyList(), cursor = sinceCursor)

    override suspend fun push(records: List<SyncRecord>) = Unit
}
