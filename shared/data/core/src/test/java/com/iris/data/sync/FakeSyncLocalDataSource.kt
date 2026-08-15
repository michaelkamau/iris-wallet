package com.iris.data.sync

import com.iris.data.db.entity.SyncChangeLogEntity
import com.iris.data.db.sync.SyncEntityType
import com.iris.data.db.sync.SyncOperation

/**
 * In memory [SyncLocalDataSource] behaving like the SQLite triggers do: every
 * local write keeps exactly one change log entry per record, deletions leave a
 * tombstone behind, and changes applied from the remote are not logged.
 */
class FakeSyncLocalDataSource(
    private val id: String
) : SyncLocalDataSource {

    val rows = mutableMapOf<String, Map<String, String?>>()

    /** Keys of the records applied from the remote, in the order they were applied. */
    val appliedOrder = mutableListOf<String>()
    private val changes = mutableMapOf<String, SyncChangeLogEntity>()
    private var pullCursor = 0L
    var lastSyncedAt: Long? = null
        private set

    override suspend fun deviceId(): String = id

    override suspend fun pullCursor(): Long = pullCursor

    override suspend fun setPullCursor(cursor: Long) {
        pullCursor = cursor
    }

    override suspend fun setLastSyncedAt(lastSyncedAt: Long) {
        this.lastSyncedAt = lastSyncedAt
    }

    override suspend fun pendingChanges(limit: Int, maxAttempts: Int): List<SyncChangeLogEntity> =
        changes.values
            .filter { it.pendingSync && it.attemptCount < maxAttempts }
            .sortedBy { it.updatedAt }
            .take(limit)

    override suspend fun localChange(entityType: String, entityId: String): SyncChangeLogEntity? =
        changes[key(entityType, entityId)]

    override suspend fun readRecord(change: SyncChangeLogEntity): SyncRecord? {
        val deleted = change.operation == SyncOperation.DELETE
        val payload = rows[key(change.entityType, change.entityId)]
        if (!deleted && payload == null) return null
        return SyncRecord(
            entityType = change.entityType,
            entityId = change.entityId,
            updatedAt = change.updatedAt,
            deleted = deleted,
            deviceId = change.deviceId,
            payload = payload.takeUnless { deleted }.orEmpty()
        )
    }

    override suspend fun applyRemote(record: SyncRecord) {
        val key = key(record.entityType, record.entityId)
        appliedOrder += key
        if (record.deleted) {
            rows.remove(key)
        } else {
            // Mirrors the Room implementation, which updates the columns it knows
            // and leaves the other ones untouched.
            rows[key] = rows[key].orEmpty() + record.payload
        }
        changes[key] = SyncChangeLogEntity(
            entityType = record.entityType,
            entityId = record.entityId,
            operation = if (record.deleted) SyncOperation.DELETE else SyncOperation.UPSERT,
            updatedAt = record.updatedAt,
            deviceId = record.deviceId,
            pendingSync = false
        )
    }

    override suspend fun markSynced(record: SyncRecord) {
        val key = key(record.entityType, record.entityId)
        val change = changes[key] ?: return
        if (change.updatedAt <= record.updatedAt) {
            changes[key] = change.copy(pendingSync = false, attemptCount = 0, lastError = null)
        }
    }

    override suspend fun recordFailure(entityType: String, entityId: String, error: String?) {
        val key = key(entityType, entityId)
        val change = changes[key] ?: return
        changes[key] = change.copy(attemptCount = change.attemptCount + 1, lastError = error)
    }

    /** Simulates a local write, exactly like the insert and update triggers do. */
    fun write(
        type: SyncEntityType,
        entityId: String,
        payload: Map<String, String?>,
        updatedAt: Long
    ) {
        val key = key(type.tableName, entityId)
        rows[key] = payload
        changes[key] = SyncChangeLogEntity(
            entityType = type.tableName,
            entityId = entityId,
            operation = SyncOperation.UPSERT,
            updatedAt = updatedAt,
            deviceId = id
        )
    }

    /** Simulates a local deletion, exactly like the delete trigger does. */
    fun delete(type: SyncEntityType, entityId: String, updatedAt: Long) {
        val key = key(type.tableName, entityId)
        rows.remove(key)
        changes[key] = SyncChangeLogEntity(
            entityType = type.tableName,
            entityId = entityId,
            operation = SyncOperation.DELETE,
            updatedAt = updatedAt,
            deviceId = id
        )
    }

    private fun key(entityType: String, entityId: String) = "$entityType/$entityId"
}
