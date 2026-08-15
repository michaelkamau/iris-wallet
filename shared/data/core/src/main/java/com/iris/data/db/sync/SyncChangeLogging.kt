package com.iris.data.db.sync

import androidx.room.withTransaction
import com.iris.data.db.IrisRoomDatabase
import com.iris.data.db.dao.read.SyncStateDao
import com.iris.data.db.dao.write.WriteSyncStateDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gives access to the local change tracking state.
 *
 * Writes performed inside [withoutChangeLogging] are not recorded in the change
 * log, which is required when applying changes received from the remote: they
 * must not be scheduled for upload again.
 */
@Singleton
class SyncChangeLogging @Inject constructor(
    private val database: IrisRoomDatabase,
    private val syncStateDao: SyncStateDao,
    private val writeSyncStateDao: WriteSyncStateDao
) {

    /** Stable identifier of this installation. */
    suspend fun deviceId(): String? = syncStateDao.find()?.deviceId

    /**
     * Runs [block] in a database transaction during which local writes are not
     * recorded in the change log.
     *
     * Change logging is a database wide flag, so the whole block runs in a single
     * transaction to keep concurrent writes of other callers logged.
     */
    suspend fun <T> withoutChangeLogging(block: suspend () -> T): T =
        database.withTransaction {
            writeSyncStateDao.setChangeLoggingEnabled(false)
            try {
                block()
            } finally {
                writeSyncStateDao.setChangeLoggingEnabled(true)
            }
        }
}
