package com.iris.data.datastore

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object DatastoreKeys {
    @Deprecated("will be removed")
    val GITHUB_OWNER = stringPreferencesKey("github_backup_owner")

    @Deprecated("will be removed")
    val GITHUB_REPO = stringPreferencesKey("github_backup_repo")

    @Deprecated("will be removed")
    val GITHUB_PAT = stringPreferencesKey("github_backup_pat")

    @Deprecated("will be removed")
    val GITHUB_LAST_BACKUP_EPOCH_SEC =
        longPreferencesKey("github_backup_last_backup_time_epoch_sec")

    fun irisFeature(key: String): Preferences.Key<Boolean> {
        return booleanPreferencesKey("feature_$key")
    }

    /**
     * Master switch for SMS capture. Absent means `false`, which is what keeps every existing user
     * unchanged on upgrade (FR-001, SC-010) — the feature only ever starts after an explicit opt-in.
     */
    val SMS_CAPTURE_ENABLED = booleanPreferencesKey("sms_capture_enabled")

    /** Category applied to captured transaction costs (FR-017). Stored as the `CategoryId` UUID. */
    val SMS_TRANSACTION_COST_CATEGORY_ID = stringPreferencesKey("sms_transaction_cost_category_id")

    /** Epoch millis of the one-off historical inbox import, so it never runs twice (FR-030). */
    val SMS_HISTORICAL_IMPORT_COMPLETED_AT = longPreferencesKey("sms_historical_import_completed_at")
}
