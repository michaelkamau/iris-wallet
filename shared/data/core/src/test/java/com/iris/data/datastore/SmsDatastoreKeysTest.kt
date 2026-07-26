package com.iris.data.datastore

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import io.kotest.matchers.shouldBe
import org.junit.Test

/**
 * SC-010 stated as a test: on upgrade, every existing user's datastore has no SMS keys at all, and
 * that state must read as "capture off". A default of `true` anywhere would silently start reading
 * messages for people who never asked for it.
 */
class SmsDatastoreKeysTest {

    @Test
    fun `an absent sms capture flag reads as disabled`() {
        // given
        val untouched = emptyPreferences()

        // when
        val enabled = untouched[DatastoreKeys.SMS_CAPTURE_ENABLED] ?: false

        // then
        enabled shouldBe false
    }

    @Test
    fun `the absent sms capture flag is genuinely unset rather than stored as false`() {
        // given
        val untouched = emptyPreferences()

        // when
        val stored = untouched[DatastoreKeys.SMS_CAPTURE_ENABLED]

        // then
        stored shouldBe null
    }

    @Test
    fun `an explicit opt in is what turns capture on`() {
        // given
        val optedIn = mutablePreferencesOf()

        // when
        optedIn[DatastoreKeys.SMS_CAPTURE_ENABLED] = true

        // then
        (optedIn[DatastoreKeys.SMS_CAPTURE_ENABLED] ?: false) shouldBe true
    }

    @Test
    fun `the sms keys use stable names so an upgrade never loses a setting`() {
        // given / when / then
        DatastoreKeys.SMS_CAPTURE_ENABLED.name shouldBe "sms_capture_enabled"
        DatastoreKeys.SMS_TRANSACTION_COST_CATEGORY_ID.name shouldBe
            "sms_transaction_cost_category_id"
        DatastoreKeys.SMS_HISTORICAL_IMPORT_COMPLETED_AT.name shouldBe
            "sms_historical_import_completed_at"
    }

    @Test
    fun `the transaction cost category and import marker start unset`() {
        // given
        val untouched = emptyPreferences()

        // when / then
        untouched[DatastoreKeys.SMS_TRANSACTION_COST_CATEGORY_ID] shouldBe null
        untouched[DatastoreKeys.SMS_HISTORICAL_IMPORT_COMPLETED_AT] shouldBe null
    }
}
