package com.iris.data.db.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Single row table holding the local sync state of this installation.
 *
 * The row is read by the SQLite triggers that maintain [SyncChangeLogEntity],
 * which is why it must always exist. It is created by the migration to schema
 * version 131 and by `SyncDatabaseCallback` for fresh installations.
 */
@Suppress("DataClassDefaultValues")
@Keep
@Serializable
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    /** Stable, randomly generated identifier of this installation. */
    @SerialName("deviceId")
    val deviceId: String,
    /**
     * When `false`, local writes are not recorded in [SyncChangeLogEntity].
     * Used while applying changes received from the remote, so that they are
     * not immediately pushed back.
     */
    @SerialName("changeLoggingEnabled")
    val changeLoggingEnabled: Boolean = true,
    /** Epoch millis of the last successful sync, `null` when never synced. */
    @SerialName("lastSyncedAt")
    val lastSyncedAt: Long? = null,
    /**
     * Epoch millis of the newest remote change already applied locally, used as
     * the cursor of the next incremental pull.
     */
    @SerialName("lastPullCursor")
    val lastPullCursor: Long = 0,
    @PrimaryKey
    @SerialName("id")
    val id: Int = SINGLETON_ID
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}
