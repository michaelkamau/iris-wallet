package com.iris.domain.usecase.sms

import com.iris.data.model.AccountId
import com.iris.data.model.Expense
import com.iris.data.model.PositiveValue
import com.iris.data.model.Transaction
import com.iris.data.model.TransactionId
import com.iris.data.model.TransactionMetadata
import com.iris.data.model.Transfer
import com.iris.data.model.primitive.AssetCode
import com.iris.data.model.primitive.PositiveDouble
import com.iris.data.repository.TransactionRepository
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.UUID

/**
 * Duplicate detection only ever *flags* (FR-029), so these tests are as much about what it leaves
 * alone as about what it finds.
 *
 * The day boundary is Nairobi's, not UTC's — a payment at 01:30 local is still "today" to the
 * person who made it, even though UTC has it on yesterday.
 */
class DetectDuplicateTransactionUseCaseTest {

    private val transactionRepository = mockk<TransactionRepository>()
    private lateinit var useCase: DetectDuplicateTransactionUseCase

    @Before
    fun setup() {
        useCase = DetectDuplicateTransactionUseCase(transactionRepository)
    }

    @Test
    fun `the same account, amount and Nairobi day is reported as a possible duplicate`() =
        runTest {
            // given
            val existing = expense(amount = 1_350.0, at = PaidAt)
            ledgerHolds(existing)

            // when
            val result = useCase.detect(Account, amount(1_350.0), Kes, PaidAt)

            // then
            result shouldBe existing.id
        }

    @Test
    fun `an entry made hours earlier on the same Nairobi day still matches`() = runTest {
        // given a manual entry at 08:00 Nairobi and a message at 19:50 Nairobi
        val existing = expense(amount = 1_350.0, at = Instant.parse("2026-07-25T05:00:00Z"))
        ledgerHolds(existing)

        // when
        val result = useCase.detect(Account, amount(1_350.0), Kes, PaidAt)

        // then
        result shouldBe existing.id
    }

    @Test
    fun `a different account is never a duplicate, however alike it looks`() = runTest {
        // given
        ledgerHolds(expense(amount = 1_350.0, at = PaidAt, account = OtherAccount))

        // when
        val result = useCase.detect(Account, amount(1_350.0), Kes, PaidAt)

        // then
        result shouldBe null
    }

    @Test
    fun `a different amount on the same day is not a duplicate`() = runTest {
        // given
        ledgerHolds(expense(amount = 1_349.99, at = PaidAt))

        // when
        val result = useCase.detect(Account, amount(1_350.0), Kes, PaidAt)

        // then
        result shouldBe null
    }

    @Test
    fun `the same amount on the previous Nairobi day is not a duplicate`() = runTest {
        // given 24 Jul 2026 at 23:00 Nairobi — inside a rolling 24 hours, but a different day
        ledgerHolds(expense(amount = 1_350.0, at = Instant.parse("2026-07-24T20:00:00Z")))

        // when
        val result = useCase.detect(Account, amount(1_350.0), Kes, PaidAt)

        // then
        result shouldBe null
    }

    @Test
    fun `a different asset on the same day is not a duplicate`() = runTest {
        // given
        ledgerHolds(expense(amount = 1_350.0, at = PaidAt, asset = AssetCode.unsafe("USD")))

        // when
        val result = useCase.detect(Account, amount(1_350.0), Kes, PaidAt)

        // then
        result shouldBe null
    }

    @Test
    fun `an empty ledger simply reports nothing`() = runTest {
        // given
        ledgerHolds()

        // when
        val result = useCase.detect(Account, amount(1_350.0), Kes, PaidAt)

        // then
        result shouldBe null
    }

    @Test
    fun `a transfer out of the account matches on the side money left`() = runTest {
        // given
        val existing = Transfer(
            id = TransactionId(UUID.randomUUID()),
            title = null,
            description = null,
            category = null,
            time = PaidAt,
            settled = true,
            metadata = NoMetadata,
            tags = emptyList(),
            fromAccount = Account,
            fromValue = PositiveValue(amount(1_350.0), Kes),
            toAccount = OtherAccount,
            toValue = PositiveValue(amount(1_350.0), Kes),
        )
        ledgerHolds(existing)

        // when
        val result = useCase.detect(Account, amount(1_350.0), Kes, PaidAt)

        // then
        result shouldBe existing.id
    }

    @Test
    fun `the query window is the Nairobi day, not the instant plus or minus a day`() = runTest {
        // given a message just after midnight Nairobi on 26 Jul
        val justAfterMidnight = Instant.parse("2026-07-25T21:10:00Z")
        val requested = mutableListOf<Pair<Instant, Instant>>()
        coEvery {
            transactionRepository.findAllByAccountAndBetween(any(), any(), any())
        } answers {
            requested += secondArg<Instant>() to thirdArg<Instant>()
            emptyList()
        }

        // when
        useCase.detect(Account, amount(1_350.0), Kes, justAfterMidnight)

        // then the window starts at 00:00 Nairobi on 26 Jul, which is 21:00 UTC on the 25th
        requested.single() shouldBe (
            Instant.parse("2026-07-25T21:00:00Z") to Instant.parse("2026-07-26T21:00:00Z")
            )
    }

    private fun ledgerHolds(vararg rows: Transaction) {
        coEvery {
            transactionRepository.findAllByAccountAndBetween(any(), any(), any())
        } returns rows.toList()
    }

    private fun expense(
        amount: Double,
        at: Instant,
        account: AccountId = Account,
        asset: AssetCode = Kes,
    ) = Expense(
        id = TransactionId(UUID.randomUUID()),
        title = null,
        description = null,
        category = null,
        time = at,
        settled = true,
        metadata = NoMetadata,
        tags = emptyList(),
        value = PositiveValue(amount(amount), asset),
        account = account,
    )

    private fun amount(value: Double) = PositiveDouble.unsafe(value)

    private companion object {
        private val Kes = AssetCode.unsafe("KES")
        private val Account =
            AccountId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"))
        private val OtherAccount =
            AccountId(UUID.fromString("00000000-0000-0000-0000-0000000000a2"))

        /** 25 Jul 2026, 19:50 Nairobi. */
        private val PaidAt: Instant = Instant.parse("2026-07-25T16:50:00Z")

        private val NoMetadata = TransactionMetadata(
            recurringRuleId = null,
            paidForDateTime = null,
            loanId = null,
            loanRecordId = null,
        )
    }
}
