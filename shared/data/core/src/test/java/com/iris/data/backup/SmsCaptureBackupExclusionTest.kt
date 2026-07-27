package com.iris.data.backup

import com.iris.base.di.KotlinxSerializationModule
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import org.junit.Test

/**
 * Pending captures are intentionally absent from the serializable backup boundary. Confirmed
 * entries are ordinary ledger transactions and remain covered by the existing backup tests.
 */
class SmsCaptureBackupExclusionTest {

    @Test
    fun `backup payload has no transient SMS capture tables`() {
        // given the exact aggregate that BackupDataUseCase serializes
        val payload = KotlinxSerializationModule.provideJson()
            .encodeToString(IrisWalletCompleteData())

        // then unreviewed data cannot be written to a backup
        listOf(
            "financial_senders",
            "captured_transactions",
            "processed_messages",
            "counterparty_categories",
        ).forEach { table ->
            payload.contains(table) shouldBe false
        }
    }
}
