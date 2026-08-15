package com.iris.data.db.sync

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Keeps the change tracking triggers and the [com.iris.data.db.entity.SyncStateEntity]
 * row in place.
 *
 * Room creates the `sync_state` and `sync_change_log` tables itself for fresh
 * installations, but it does not manage triggers or table content, so both are
 * (re)created here. All statements are idempotent.
 */
class SyncDatabaseCallback : RoomDatabase.Callback() {

    @Volatile
    private var setUpDone = false

    override fun onCreate(db: SupportSQLiteDatabase) {
        setUp(db)
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        if (setUpDone) return
        setUp(db)
    }

    private fun setUp(db: SupportSQLiteDatabase) {
        if (!SyncSchema.tablesExist(db)) return
        SyncSchema.seedSyncState(db)
        if (!SyncSchema.triggersExist(db)) {
            SyncSchema.createTriggers(db)
        }
        setUpDone = true
    }
}
