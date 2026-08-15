package com.iris.data.db.sync

/** Kind of local change recorded in the `sync_change_log` table. */
enum class SyncOperation {
    /** The record was created or modified locally. */
    UPSERT,

    /** The record was deleted locally. The log row acts as a tombstone. */
    DELETE
}
