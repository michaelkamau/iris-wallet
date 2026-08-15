package com.iris.data.db.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.Index
import com.iris.data.db.sync.SyncOperation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Per-record sync metadata, maintained automatically by SQLite triggers.
 *
 * There is exactly one row per record of every syncable table:
 * - for existing records it holds the timestamp of the last local write ([updatedAt])
 *   and the device that performed it ([deviceId]),
 * - for deleted records it acts as a tombstone ([operation] == [SyncOperation.DELETE]),
 *   which is required to propagate deletions to other devices.
 *
 * [pendingSync] marks records that still have to be pushed to the remote.
 */
@Suppress("DataClassDefaultValues")
@Keep
@Serializable
@Entity(
    tableName = "sync_change_log",
    primaryKeys = ["entityType", "entityId"],
    indices = [Index(value = ["pendingSync", "updatedAt"])]
)
data class SyncChangeLogEntity(
    /** Table name of the changed record, see `SyncEntityType`. */
    @SerialName("entityType")
    val entityType: String,
    /** Primary key of the changed record, as text. */
    @SerialName("entityId")
    val entityId: String,
    @SerialName("operation")
    val operation: SyncOperation,
    /** Epoch millis of the local write. */
    @SerialName("updatedAt")
    val updatedAt: Long,
    @SerialName("deviceId")
    val deviceId: String? = null,
    @SerialName("pendingSync")
    val pendingSync: Boolean = true,
    @SerialName("attemptCount")
    val attemptCount: Int = 0,
    @SerialName("lastError")
    val lastError: String? = null
)
