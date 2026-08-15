package com.iris.data.sync

import com.iris.data.db.entity.SyncChangeLogEntity
import com.iris.data.db.sync.SyncOperation
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides whether a record received from the remote replaces the local one.
 *
 * The policy is last writer wins per record:
 * - the newer `updatedAt` wins,
 * - on equal timestamps a deletion wins over a modification, so that a record
 *   deleted on one device does not come back on another,
 * - remaining ties are broken by device id, which makes the outcome identical on
 *   every device and therefore guarantees convergence.
 *
 * A deletion is not special cased beyond the tie break: an edit made after a
 * deletion intentionally resurrects the record, which is what a user editing an
 * offline device expects.
 */
@Singleton
class ConflictResolver @Inject constructor() {

    /** `true` when [remote] must be applied on top of the local state [local]. */
    fun shouldApplyRemote(remote: SyncRecord, local: SyncChangeLogEntity?): Boolean {
        if (local == null) return true
        if (remote.updatedAt != local.updatedAt) return remote.updatedAt > local.updatedAt

        val remoteDeleted = remote.deleted
        val localDeleted = local.operation == SyncOperation.DELETE
        if (remoteDeleted != localDeleted) return remoteDeleted

        return remote.deviceId.orEmpty() > local.deviceId.orEmpty()
    }
}
