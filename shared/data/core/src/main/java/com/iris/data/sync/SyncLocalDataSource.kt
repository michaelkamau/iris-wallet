package com.iris.data.sync

import com.iris.data.db.entity.SyncChangeLogEntity

/**
 * Local side of the sync engine: reads the change log written by the SQLite
 * triggers, and applies records received from the remote.
 */
interface SyncLocalDataSource {

    /** Stable identifier of this installation, `null` before the sync state is seeded. */
    suspend fun deviceId(): String?

    /** Epoch millis of the newest remote change already applied locally. */
    suspend fun pullCursor(): Long

    /** Stores the pull cursor, only ever called after a page was applied successfully. */
    suspend fun setPullCursor(cursor: Long)

    /** Stores the timestamp of the last fully successful sync. */
    suspend fun setLastSyncedAt(lastSyncedAt: Long)

    /**
     * Local changes waiting to be pushed, oldest first, skipping records that
     * failed more than [maxAttempts] times.
     */
    suspend fun pendingChanges(limit: Int, maxAttempts: Int): List<SyncChangeLogEntity>

    /** Change log entry of a record, `null` when the record was never written locally. */
    suspend fun localChange(entityType: String, entityId: String): SyncChangeLogEntity?

    /**
     * Builds the record to upload for [change], reading the current row content.
     *
     * Returns `null` when the row backing an upsert no longer exists, which
     * happens when it was deleted after the change log entry was read.
     */
    suspend fun readRecord(change: SyncChangeLogEntity): SyncRecord?

    /**
     * Applies [record] to the local database without recording it as a local
     * change, so that it is not pushed back to the remote.
     */
    suspend fun applyRemote(record: SyncRecord)

    /** Marks the change of a pushed record as synced. */
    suspend fun markSynced(record: SyncRecord)

    /** Records a failed push attempt, used for backoff and poison record detection. */
    suspend fun recordFailure(entityType: String, entityId: String, error: String?)
}
