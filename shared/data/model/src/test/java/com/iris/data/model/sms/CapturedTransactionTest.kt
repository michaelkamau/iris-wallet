package com.iris.data.model.sms

import com.iris.data.model.AccountId
import com.iris.data.model.TransactionId
import com.iris.data.model.primitive.AssetCode
import com.iris.data.model.primitive.PositiveDouble
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.Instant
import java.util.UUID

class CapturedTransactionTest {

    @Test
    fun `needs an account when no account is mapped`() {
        // given
        val captured = captured(account = null, duplicateOf = null)

        // when
        val status = captured.reviewStatus()

        // then
        status shouldBe ReviewStatus.NeedsAccount
    }

    @Test
    fun `is a possible duplicate when an existing transaction looks like the same event`() {
        // given
        val existing = TransactionId(UUID.fromString("00000000-0000-0000-0000-0000000000ff"))
        val captured = captured(account = AnAccount, duplicateOf = existing)

        // when
        val status = captured.reviewStatus()

        // then
        status shouldBe ReviewStatus.PossibleDuplicate(existing)
    }

    @Test
    fun `is ready to confirm when an account is mapped and nothing looks duplicated`() {
        // given
        val captured = captured(account = AnAccount, duplicateOf = null)

        // when
        val status = captured.reviewStatus()

        // then
        status shouldBe ReviewStatus.ReadyToConfirm
    }

    @Test
    fun `needing an account outranks a possible duplicate`() {
        // given
        val existing = TransactionId(UUID.fromString("00000000-0000-0000-0000-0000000000ff"))
        val captured = captured(account = null, duplicateOf = existing)

        // when
        val status = captured.reviewStatus()

        // then
        status shouldBe ReviewStatus.NeedsAccount
    }

    private fun captured(
        account: AccountId?,
        duplicateOf: TransactionId?,
    ) = CapturedTransaction(
        id = CapturedTransactionId(UUID.fromString("00000000-0000-0000-0000-00000000000a")),
        sender = SenderId.unsafe("MPESA"),
        kind = CapturedKind.Principal(MoneyDirection.MoneyOut),
        amount = PositiveDouble.unsafe(1350.0),
        asset = AssetCode.unsafe("KES"),
        time = AnInstant,
        counterparty = null,
        reference = ProviderReference.unsafe("UGP7B0ITE4"),
        account = account,
        category = null,
        description = null,
        duplicateOf = duplicateOf,
        capturedAt = AnInstant,
        origin = CaptureOrigin.LiveBroadcast,
    )

    companion object {
        private val AnInstant: Instant = Instant.parse("2026-07-25T16:50:00Z")
        private val AnAccount = AccountId(UUID.fromString("00000000-0000-0000-0000-00000000000b"))
    }
}
