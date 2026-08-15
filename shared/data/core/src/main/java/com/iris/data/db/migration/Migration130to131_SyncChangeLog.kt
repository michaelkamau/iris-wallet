package com.iris.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.iris.data.db.sync.SyncSchema

/**
 * Adds the local change tracking used by cloud sync:
 * the `sync_state` and `sync_change_log` tables plus the triggers keeping the
 * change log up to date. Existing rows are recorded as pending changes so that
 * the first sync uploads the whole local database.
 */
@Suppress("MagicNumber", "ClassNaming")
class Migration130to131_SyncChangeLog : Migration(130, 131) {
    override fun migrate(db: SupportSQLiteDatabase) {
        SyncSchema.createTables(db)
        SyncSchema.seedSyncState(db)
        SyncSchema.backfillChangeLog(db)
        SyncSchema.createTriggers(db)
    }
}
