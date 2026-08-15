package com.iris.data.sync

/** Outcome of a single sync cycle. */
sealed interface SyncResult {

    /** The cycle completed, [pulled] records were applied locally and [pushed] uploaded. */
    data class Success(val pulled: Int, val pushed: Int) : SyncResult

    /** The cycle was skipped because another one is already running. */
    data object AlreadyRunning : SyncResult

    /** The cycle failed, sync state is unchanged beyond the pages already committed. */
    data class Failure(val reason: String) : SyncResult
}

/** Current state of the sync engine, surfaced to the UI. */
sealed interface SyncStatus {

    /** No sync is running. */
    data object Idle : SyncStatus

    /** A sync cycle is in progress. */
    data object Syncing : SyncStatus

    /** The last cycle completed successfully at [at] (epoch millis). */
    data class Synced(val at: Long) : SyncStatus

    /** The last cycle failed. */
    data class Failed(val reason: String) : SyncStatus
}
