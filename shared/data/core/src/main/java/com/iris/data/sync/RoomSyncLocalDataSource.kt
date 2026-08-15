package com.iris.data.sync

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.iris.data.db.IrisRoomDatabase
import com.iris.data.db.dao.read.SyncChangeLogDao
import com.iris.data.db.dao.read.SyncStateDao
import com.iris.data.db.dao.write.WriteSyncChangeLogDao
import com.iris.data.db.dao.write.WriteSyncStateDao
import com.iris.data.db.entity.SyncChangeLogEntity
import com.iris.data.db.sync.SyncChangeLogging
import com.iris.data.db.sync.SyncEntityType
import com.iris.data.db.sync.SyncOperation
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SyncLocalDataSource] on top of the Room database.
 *
 * Rows are read and written generically, column by column, instead of through the
 * typed DAOs. That keeps the sync engine independent of the entity definitions:
 * a column added in a later schema version is synced without touching this class,
 * and a column unknown to this app version is ignored instead of failing.
 *
 * Only tables of [SyncEntityType] and columns that actually exist in the local
 * schema are ever addressed, so the generated SQL can not be influenced by remote
 * content. Values are always passed as bind arguments.
 */
@Singleton
class RoomSyncLocalDataSource @Inject constructor(
    private val database: IrisRoomDatabase,
    private val syncChangeLogDao: SyncChangeLogDao,
    private val writeSyncChangeLogDao: WriteSyncChangeLogDao,
    private val syncStateDao: SyncStateDao,
    private val writeSyncStateDao: WriteSyncStateDao,
    private val changeLogging: SyncChangeLogging
) : SyncLocalDataSource {

    private val columnsByTable = ConcurrentHashMap<String, Set<String>>()

    override suspend fun deviceId(): String? = changeLogging.deviceId()

    override suspend fun pullCursor(): Long = syncStateDao.find()?.lastPullCursor ?: 0

    override suspend fun setPullCursor(cursor: Long) =
        writeSyncStateDao.setLastPullCursor(cursor)

    override suspend fun setLastSyncedAt(lastSyncedAt: Long) =
        writeSyncStateDao.setLastSyncedAt(lastSyncedAt)

    override suspend fun pendingChanges(limit: Int, maxAttempts: Int): List<SyncChangeLogEntity> =
        syncChangeLogDao.findPending(limit = limit, maxAttempts = maxAttempts)

    override suspend fun localChange(entityType: String, entityId: String): SyncChangeLogEntity? =
        syncChangeLogDao.findByEntity(entityType = entityType, entityId = entityId)

    override suspend fun readRecord(change: SyncChangeLogEntity): SyncRecord? {
        val type = SyncEntityType.fromTableName(change.entityType) ?: return null
        if (change.operation == SyncOperation.DELETE) return change.toRecord(payload = emptyMap())

        val payload = readRow(type, change.entityId) ?: return null
        return change.toRecord(payload = payload)
    }

    override suspend fun applyRemote(record: SyncRecord) {
        val type = record.type ?: return
        changeLogging.withoutChangeLogging {
            if (record.deleted) {
                deleteRow(type, record.entityId)
            } else {
                upsertRow(type, record.entityId, record.payload)
            }
            writeSyncChangeLogDao.save(record.toChange())
        }
    }

    override suspend fun markSynced(record: SyncRecord) = writeSyncChangeLogDao.markSynced(
        entityType = record.entityType,
        entityId = record.entityId,
        syncedUpTo = record.updatedAt
    )

    override suspend fun recordFailure(entityType: String, entityId: String, error: String?) =
        writeSyncChangeLogDao.recordFailure(
            entityType = entityType,
            entityId = entityId,
            error = error
        )

    private fun SyncRecord.toChange() = SyncChangeLogEntity(
        entityType = entityType,
        entityId = entityId,
        operation = if (deleted) SyncOperation.DELETE else SyncOperation.UPSERT,
        updatedAt = updatedAt,
        deviceId = deviceId,
        pendingSync = false
    )

    private fun SyncChangeLogEntity.toRecord(payload: Map<String, String?>) = SyncRecord(
        entityType = entityType,
        entityId = entityId,
        updatedAt = updatedAt,
        deleted = operation == SyncOperation.DELETE,
        deviceId = deviceId,
        payload = payload
    )

    /** Reads a row as a column name to text value map, `null` when it no longer exists. */
    private fun readRow(type: SyncEntityType, entityId: String): Map<String, String?>? {
        val query = "SELECT * FROM `${type.tableName}` WHERE ${whereId(type)}"
        return database.query(query, idValues(type, entityId)).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            (0 until cursor.columnCount).associate { index ->
                cursor.getColumnName(index) to
                    if (cursor.isNull(index)) null else cursor.getString(index)
            }
        }
    }

    /**
     * Writes the columns of [payload] known to the local schema.
     *
     * An existing row is updated instead of replaced, so that columns this app
     * version does not send yet, or that the sending version did not know, keep
     * their local value instead of being reset.
     */
    private fun upsertRow(type: SyncEntityType, entityId: String, payload: Map<String, String?>) {
        val columns = columnsOf(type)
        val known = payload.filterKeys { it in columns }
        if (known.isEmpty()) return

        val values = ContentValues()
        known.forEach { (column, value) -> values.put(column, value) }

        val updated = database.openHelper.writableDatabase.update(
            type.tableName,
            SQLiteDatabase.CONFLICT_REPLACE,
            values,
            whereId(type),
            idValues(type, entityId)
        )
        if (updated == 0) {
            database.openHelper.writableDatabase.insert(
                type.tableName,
                SQLiteDatabase.CONFLICT_REPLACE,
                values
            )
        }
    }

    private fun deleteRow(type: SyncEntityType, entityId: String) {
        database.openHelper.writableDatabase.delete(
            type.tableName,
            whereId(type),
            idValues(type, entityId)
        )
    }

    /** `WHERE` clause matching a row by its primary key columns. */
    private fun whereId(type: SyncEntityType): String =
        type.idColumns.joinToString(separator = " AND ") { "`$it` = ?" }

    /** Splits a composite entity id back into the values of the primary key columns. */
    private fun idValues(type: SyncEntityType, entityId: String): Array<Any?> {
        val values = entityId.split(SyncEntityType.ID_SEPARATOR)
        require(values.size == type.idColumns.size) {
            "Unexpected id '$entityId' for ${type.tableName}"
        }
        return Array(values.size) { index -> values[index] }
    }

    /** Columns of a table as declared by the local schema, read once per table. */
    private fun columnsOf(type: SyncEntityType): Set<String> =
        columnsByTable.getOrPut(type.tableName) {
            database.query("PRAGMA table_info(`${type.tableName}`)", null).use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                buildSet {
                    while (cursor.moveToNext()) {
                        add(cursor.getString(nameIndex))
                    }
                }
            }
        }
}
