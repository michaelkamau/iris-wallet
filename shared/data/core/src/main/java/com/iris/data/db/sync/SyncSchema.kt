package com.iris.data.db.sync

import androidx.sqlite.db.SupportSQLiteDatabase
import com.iris.data.db.entity.SyncStateEntity
import java.util.UUID

/**
 * SQL used to create and maintain the local change tracking tables.
 *
 * Change tracking is implemented with SQLite triggers on purpose: they run inside
 * the same transaction as the write that fired them and cannot be bypassed by any
 * DAO, migration or raw query, which makes the change log impossible to diverge
 * from the tracked data.
 */
internal object SyncSchema {

    /** Epoch millis of the current time, as an SQL expression. */
    private const val NOW = "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)"

    private const val LOGGING_ENABLED =
        "COALESCE((SELECT changeLoggingEnabled FROM sync_state " +
            "WHERE id = ${SyncStateEntity.SINGLETON_ID}), 1) = 1"

    private const val DEVICE_ID =
        "(SELECT deviceId FROM sync_state WHERE id = ${SyncStateEntity.SINGLETON_ID})"

    private const val LOG_COLUMNS =
        "entityType, entityId, operation, updatedAt, deviceId, pendingSync, attemptCount, lastError"

    private const val CREATE_CHANGE_LOG_TABLE = """
        CREATE TABLE IF NOT EXISTS `sync_change_log` (
            `entityType` TEXT NOT NULL,
            `entityId` TEXT NOT NULL,
            `operation` TEXT NOT NULL,
            `updatedAt` INTEGER NOT NULL,
            `deviceId` TEXT,
            `pendingSync` INTEGER NOT NULL,
            `attemptCount` INTEGER NOT NULL,
            `lastError` TEXT,
            PRIMARY KEY(`entityType`, `entityId`)
        )
    """

    private const val CREATE_CHANGE_LOG_INDEX = """
        CREATE INDEX IF NOT EXISTS `index_sync_change_log_pendingSync_updatedAt`
        ON `sync_change_log` (`pendingSync`, `updatedAt`)
    """

    private const val CREATE_SYNC_STATE_TABLE = """
        CREATE TABLE IF NOT EXISTS `sync_state` (
            `deviceId` TEXT NOT NULL,
            `changeLoggingEnabled` INTEGER NOT NULL,
            `lastSyncedAt` INTEGER,
            `lastPullCursor` INTEGER NOT NULL DEFAULT 0,
            `id` INTEGER NOT NULL,
            PRIMARY KEY(`id`)
        )
    """

    fun createTables(db: SupportSQLiteDatabase) {
        db.execSQL(CREATE_SYNC_STATE_TABLE)
        db.execSQL(CREATE_CHANGE_LOG_TABLE)
        db.execSQL(CREATE_CHANGE_LOG_INDEX)
    }

    /** Creates the single [SyncStateEntity] row, unless it already exists. */
    fun seedSyncState(db: SupportSQLiteDatabase, deviceId: String = UUID.randomUUID().toString()) {
        db.execSQL(
            "INSERT OR IGNORE INTO sync_state " +
                "(id, deviceId, changeLoggingEnabled, lastSyncedAt, lastPullCursor) " +
                "VALUES (${SyncStateEntity.SINGLETON_ID}, ?, 1, NULL, 0)",
            arrayOf<Any>(deviceId)
        )
    }

    fun createTriggers(db: SupportSQLiteDatabase) {
        SyncEntityType.entries.forEach { entityType ->
            db.execSQL(insertTrigger(entityType))
            db.execSQL(updateTrigger(entityType))
            db.execSQL(deleteTrigger(entityType))
        }
    }

    /** `true` when both change tracking tables exist. */
    fun tablesExist(db: SupportSQLiteDatabase): Boolean =
        count(db, "type = 'table' AND name IN ('sync_state', 'sync_change_log')") == SYNC_TABLE_COUNT

    /** `true` when every change tracking trigger is in place. */
    fun triggersExist(db: SupportSQLiteDatabase): Boolean =
        count(db, "type = 'trigger' AND name LIKE 'sync_log_%'") ==
            SyncEntityType.entries.size * TRIGGERS_PER_TABLE

    private fun count(db: SupportSQLiteDatabase, where: String): Int =
        db.query("SELECT COUNT(*) FROM sqlite_master WHERE $where").use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }

    fun dropTriggers(db: SupportSQLiteDatabase) {
        SyncEntityType.entries.forEach { entityType ->
            listOf(INSERT, UPDATE, DELETE).forEach { suffix ->
                db.execSQL("DROP TRIGGER IF EXISTS `${triggerName(entityType, suffix)}`")
            }
        }
    }

    /**
     * Records every already existing row as a pending change, so that the first
     * sync of an existing installation uploads the whole local database.
     */
    fun backfillChangeLog(db: SupportSQLiteDatabase) {
        SyncEntityType.entries.forEach { entityType ->
            db.execSQL(
                """
                INSERT OR REPLACE INTO sync_change_log ($LOG_COLUMNS)
                SELECT '${entityType.tableName}', ${entityType.idExpression()},
                    '${SyncOperation.UPSERT}', $NOW, $DEVICE_ID, 1, 0, NULL
                FROM `${entityType.tableName}`
                """.trimIndent()
            )
        }
    }

    private fun insertTrigger(entityType: SyncEntityType): String = """
        CREATE TRIGGER IF NOT EXISTS `${triggerName(entityType, INSERT)}`
        AFTER INSERT ON `${entityType.tableName}`
        WHEN $LOGGING_ENABLED
        BEGIN
            ${logRow(entityType, SyncOperation.UPSERT, rowAlias = "NEW")}
        END
    """.trimIndent()

    private fun updateTrigger(entityType: SyncEntityType): String {
        val oldId = entityType.idExpression("OLD")
        val newId = entityType.idExpression("NEW")
        return """
            CREATE TRIGGER IF NOT EXISTS `${triggerName(entityType, UPDATE)}`
            AFTER UPDATE ON `${entityType.tableName}`
            WHEN $LOGGING_ENABLED
            BEGIN
                INSERT OR REPLACE INTO sync_change_log ($LOG_COLUMNS)
                SELECT '${entityType.tableName}', $oldId, '${SyncOperation.DELETE}',
                    $NOW, $DEVICE_ID, 1, 0, NULL
                WHERE $oldId <> $newId;
                ${logRow(entityType, SyncOperation.UPSERT, rowAlias = "NEW")}
            END
        """.trimIndent()
    }

    private fun deleteTrigger(entityType: SyncEntityType): String = """
        CREATE TRIGGER IF NOT EXISTS `${triggerName(entityType, DELETE)}`
        AFTER DELETE ON `${entityType.tableName}`
        WHEN $LOGGING_ENABLED
        BEGIN
            ${logRow(entityType, SyncOperation.DELETE, rowAlias = "OLD")}
        END
    """.trimIndent()

    private fun logRow(
        entityType: SyncEntityType,
        operation: SyncOperation,
        rowAlias: String
    ): String = "INSERT OR REPLACE INTO sync_change_log ($LOG_COLUMNS) VALUES (" +
        "'${entityType.tableName}', ${entityType.idExpression(rowAlias)}, '$operation', " +
        "$NOW, $DEVICE_ID, 1, 0, NULL);"

    private fun triggerName(entityType: SyncEntityType, suffix: String): String =
        "sync_log_${entityType.tableName}_$suffix"

    private const val SYNC_TABLE_COUNT = 2
    private const val TRIGGERS_PER_TABLE = 3
    private const val INSERT = "insert"
    private const val UPDATE = "update"
    private const val DELETE = "delete"
}
