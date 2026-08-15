package com.iris.data.sync

import androidx.annotation.Keep
import com.iris.data.db.sync.SyncEntityType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Transport representation of a single record, exchanged with the remote.
 *
 * The record is intentionally schema agnostic: [payload] holds the columns of the
 * row as text, keyed by column name. This keeps the transport format forward and
 * backward compatible, columns unknown to an older app version are ignored when
 * the record is applied locally.
 *
 * [entityType], [entityId], [updatedAt] and [deleted] are always transferred in
 * clear, they are the only fields the remote needs in order to serve incremental
 * queries. [payload] is the only part that has to be encrypted end to end.
 */
@Suppress("DataClassDefaultValues")
@Keep
@Serializable
data class SyncRecord(
    /** Table name of the record, see [SyncEntityType]. */
    @SerialName("entityType")
    val entityType: String,
    /** Primary key of the record, joined with [SyncEntityType.ID_SEPARATOR] for composite keys. */
    @SerialName("entityId")
    val entityId: String,
    /** Epoch millis of the write this record represents. */
    @SerialName("updatedAt")
    val updatedAt: Long,
    /** When `true` the record is a tombstone and [payload] is empty. */
    @SerialName("deleted")
    val deleted: Boolean = false,
    /** Installation that produced the record, used as a deterministic conflict tie breaker. */
    @SerialName("deviceId")
    val deviceId: String? = null,
    /** Columns of the row, as text, empty for tombstones. */
    @SerialName("payload")
    val payload: Map<String, String?> = emptyMap()
) {
    /** Type of the record, `null` when the remote sent a table this app version does not know. */
    val type: SyncEntityType?
        get() = SyncEntityType.fromTableName(entityType)
}
