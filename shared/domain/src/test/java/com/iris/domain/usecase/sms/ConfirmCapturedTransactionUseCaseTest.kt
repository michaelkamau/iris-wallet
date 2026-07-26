package com.iris.domain.usecase.sms

import androidx.room.withTransaction
import arrow.core.left
import arrow.core.right
import com.iris.base.time.TimeProvider
import com.iris.data.db.IrisRoomDatabase
import com.iris.data.model.CategoryId
import com.iris.data.model.Expense
import com.iris.data.model.Income
import com.iris.data.model.Transaction
import com.iris.data.model.primitive.NotBlankTrimmedString
import com.iris.data.model.primitive.PositiveDouble
import com.iris.data.model.sms.CapturedEntry
import com.iris.data.model.sms.CapturedTransaction
import com.iris.data.model.sms.CapturedTransactionId
import com.iris.data.model.sms.MoneyDirection
import com.iris.data.repository.CapturedTransactionRepository
import com.iris.data.repository.CounterpartyCategoryRepository
import com.iris.data.repository.TransactionRepository
import com.iris.sms.parser.primitive.CounterpartyNormalizer
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * Confirming is the only moment SMS capture is allowed to touch money, so this asserts both what
 * it writes and — just as importantly — what it refuses to write.
 */
class ConfirmCapturedTransactionUseCaseTest {

    private val db = mockk<IrisRoomDatabase>()
    private val capturedTransactionRepository =
        mockk<CapturedTransactionRepository>(relaxUnitFun = true)
    private val transactionRepository = mockk<TransactionRepository>(relaxUnitFun = true)
    private val counterpartyCategoryRepository =
        mockk<CounterpartyCategoryRepository>(relaxUnitFun = true)
    private val ensureTransactionCostCategory = mockk<EnsureTransactionCostCategoryUseCase>()

    private val id = CapturedTransactionId(UUID.randomUUID())
    private lateinit var useCase: ConfirmCapturedTransactionUseCase

    @Before
    fun setup() {
        mockkStatic(ROOM_DATABASE_KT)
        passThroughTransaction()
        coEvery { ensureTransactionCostCategory.ensure() } returns CostCategory.right()
        useCase = ConfirmCapturedTransactionUseCase(
            db = db,
            capturedTransactionRepository = capturedTransactionRepository,
            transactionRepository = transactionRepository,
            counterpartyCategoryRepository = counterpartyCategoryRepository,
            counterpartyNormalizer = CounterpartyNormalizer(),
            ensureTransactionCostCategory = ensureTransactionCostCategory,
            timeProvider = FixedTimeProvider,
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(ROOM_DATABASE_KT)
    }

    @Test
    fun `an item with no account is refused, and nothing at all is written`() = runTest {
        // given
        pending(SmsFixtures.captured(id = id, account = null))

        // when
        val result = useCase.confirm(id)

        // then no default account is invented on the user's behalf
        result.shouldBeLeft() shouldBe ConfirmCaptureError.MissingAccount(id)
        coVerify(exactly = 0) { transactionRepository.save(any()) }
        coVerify(exactly = 0) { capturedTransactionRepository.deleteEntry(any()) }
    }

    @Test
    fun `an item confirmed with an account chosen at review time is committed`() = runTest {
        // given
        pending(SmsFixtures.captured(id = id, account = null))
        val saved = slot<Transaction>()
        coEvery { transactionRepository.save(capture(saved)) } just runs

        // when
        val result = useCase.confirm(id, account = SmsFixtures.Account)

        // then
        result.shouldBeRight() shouldBe saved.captured.id
        saved.captured.shouldBeInstanceOf<Expense>().account shouldBe SmsFixtures.Account
    }

    /**
     * FR-023 in one assertion: whatever the parser read, the ledger records what the user last
     * saw on screen. An edit that reaches the review UI but not the insert is invisible until the
     * user finds the wrong figure in their reports weeks later.
     */
    @Test
    fun `an edited amount, payee and date are the values that reach the ledger`() = runTest {
        // given a message the parser read imperfectly
        pending(
            SmsFixtures.captured(
                id = id,
                amount = 1_350.0,
                counterparty = "Frnk Inn Kikuy",
            ),
        )
        val saved = slot<Transaction>()
        coEvery { transactionRepository.save(capture(saved)) } just runs

        // when the user corrects all three before confirming
        useCase.confirm(
            id = id,
            amount = PositiveDouble.unsafe(1_530.0),
            counterparty = NotBlankTrimmedString.unsafe("Frank Inn Kikuyu"),
            time = CorrectedTime,
        )

        // then none of the parsed values survive into the ledger
        val expense = saved.captured.shouldBeInstanceOf<Expense>()
        expense.value.amount.value shouldBe 1_530.0
        expense.title?.value shouldBe "Frank Inn Kikuyu"
        expense.time shouldBe CorrectedTime
    }

    @Test
    fun `a field the user did not touch keeps what was extracted`() = runTest {
        // given
        pending(SmsFixtures.captured(id = id, counterparty = "James Kinyua"))
        val saved = slot<Transaction>()
        coEvery { transactionRepository.save(capture(saved)) } just runs

        // when only the amount is corrected
        useCase.confirm(id, amount = PositiveDouble.unsafe(1_530.0))

        // then the payee and the message's own timestamp are untouched (FR-014)
        val expense = saved.captured.shouldBeInstanceOf<Expense>()
        expense.value.amount.value shouldBe 1_530.0
        expense.title?.value shouldBe "James Kinyua"
        expense.time shouldBe SmsFixtures.PaidAt
    }

    @Test
 fun `money out becomes an expense, money in becomes an income`() = runTest { // given
        pending(SmsFixtures.captured(id = id, direction = MoneyDirection.MoneyIn))
        val saved = slot<Transaction>()
        coEvery { transactionRepository.save(capture(saved)) } just runs

        // when
        useCase.confirm(id)

        // then
        val income = saved.captured.shouldBeInstanceOf<Income>()
        income.value.amount.value shouldBe 1_350.0
        income.value.asset shouldBe SmsFixtures.Kes
        income.time shouldBe SmsFixtures.PaidAt
    }

    @Test
    fun `exactly one transaction is inserted and the pending row is removed`() = runTest {
        // given
        pending(SmsFixtures.captured(id = id))

        // when
        useCase.confirm(id)

        // then
        coVerify(exactly = 1) { transactionRepository.save(any()) }
        coVerify(exactly = 1) { capturedTransactionRepository.deleteEntry(id) }
    }

    @Test
    fun `a confirmed row carries nothing that marks it as having come from a message`() = runTest {
        // given
        pending(SmsFixtures.captured(id = id))
        val saved = slot<Transaction>()
        coEvery { transactionRepository.save(capture(saved)) } just runs

        // when
        useCase.confirm(id)

        // then it is indistinguishable from an entry typed by hand
        val expense = saved.captured.shouldBeInstanceOf<Expense>()
        expense.tags shouldBe emptyList()
        expense.settled shouldBe true
        expense.metadata.recurringRuleId shouldBe null
        expense.metadata.paidForDateTime shouldBe null
        expense.metadata.loanId shouldBe null
        expense.metadata.loanRecordId shouldBe null
    }

    @Test
    fun `the counterparty becomes the title and the reference is appended to the description`() =
        runTest {
            // given
            pending(SmsFixtures.captured(id = id, description = "Lunch"))
            val saved = slot<Transaction>()
            coEvery { transactionRepository.save(capture(saved)) } just runs

            // when
            useCase.confirm(id)

            // then
            saved.captured.title?.value shouldBe "James Kinyua"
            saved.captured.description?.value shouldBe "Lunch\nRef: UGP7B0ITE4"
        }

    @Test
    fun `a message with no reference leaves the description untouched`() = runTest {
        // given
        pending(SmsFixtures.captured(id = id, reference = null, description = "Lunch"))
        val saved = slot<Transaction>()
        coEvery { transactionRepository.save(capture(saved)) } just runs

        // when
        useCase.confirm(id)

        // then
        saved.captured.description?.value shouldBe "Lunch"
    }

    @Test
    fun `a reference with no description still reaches the ledger`() = runTest {
        // given
        pending(SmsFixtures.captured(id = id, description = null))
        val saved = slot<Transaction>()
        coEvery { transactionRepository.save(capture(saved)) } just runs

        // when
        useCase.confirm(id)

        // then
        saved.captured.description?.value shouldBe "Ref: UGP7B0ITE4"
    }

    @Test
    fun `the category chosen at review time wins over the captured suggestion`() = runTest {
        // given
        val chosen = CategoryId(UUID.randomUUID())
        pending(SmsFixtures.captured(id = id))
        val saved = slot<Transaction>()
        coEvery { transactionRepository.save(capture(saved)) } just runs

        // when
        useCase.confirm(id, category = chosen)

        // then
        saved.captured.category shouldBe chosen
    }

    @Test
    fun `an edited description replaces the captured one, reference still appended`() = runTest {
        // given
        pending(SmsFixtures.captured(id = id, description = "Lunch"))
        val saved = slot<Transaction>()
        coEvery { transactionRepository.save(capture(saved)) } just runs

        // when
        useCase.confirm(id, description = NotBlankTrimmedString.unsafe("Dinner with Ann"))

        // then
        saved.captured.description?.value shouldBe "Dinner with Ann\nRef: UGP7B0ITE4"
    }

    @Test
    fun `confirming something already gone is reported, not crashed`() = runTest {
        // given
        coEvery { capturedTransactionRepository.findById(id) } returns null

        // when
        val result = useCase.confirm(id)

        // then
        result.shouldBeLeft() shouldBe ConfirmCaptureError.CapturedItemGone(id)
    }

    @Test
    fun `a failed insert rolls the whole block back and reports persistence`() = runTest {
        // given
        pending(SmsFixtures.captured(id = id))
        coEvery { transactionRepository.save(any()) } throws IllegalStateException("disk full")

        // when
        val result = useCase.confirm(id)

        // then the pending row survives, so the user can try again
        result.shouldBeLeft() shouldBe ConfirmCaptureError.Persistence("IllegalStateException")
        coVerify(exactly = 0) { capturedTransactionRepository.deleteEntry(any()) }
    }

    // --- User Story 2: transaction costs ------------------------------------------------------

    @Test
    fun `a payment with a charge commits two transactions, not one with a bigger amount`() =
        runTest {
            // given the DTB transfer and the 59.76 charge it reported
            pending(SmsFixtures.captured(id = id), fee = SmsFixtures.fee(parent = id))
            val saved = mutableListOf<Transaction>()
            coEvery { transactionRepository.save(capture(saved)) } just runs

            // when
            val result = useCase.confirm(id)

            // then two distinct expenses reach the ledger (FR-016)
            result.shouldBeRight()
            saved.size shouldBe 2
            saved.map { it.shouldBeInstanceOf<Expense>().value.amount.value } shouldBe
                listOf(1_350.0, 59.76)
        }

    @Test
    fun `the charge lands in the dedicated transaction-cost category, the payment does not`() =
        runTest {
            // given
            val chosen = CategoryId(UUID.randomUUID())
            pending(SmsFixtures.captured(id = id), fee = SmsFixtures.fee(parent = id))
            val saved = mutableListOf<Transaction>()
            coEvery { transactionRepository.save(capture(saved)) } just runs

            // when
            useCase.confirm(id, category = chosen)

            // then the user's choice is not overwritten, and the fee is not left uncategorised
            saved.map { it.category } shouldBe listOf(chosen, CostCategory)
        }

    @Test
    fun `the category is asked for once per confirm and reused across confirms`() = runTest {
        // given two fee-bearing items
        val second = CapturedTransactionId(UUID.randomUUID())
        pending(SmsFixtures.captured(id = id), fee = SmsFixtures.fee(parent = id))
        pending(SmsFixtures.captured(id = second), fee = SmsFixtures.fee(parent = second))

        // when
        useCase.confirm(id)
        useCase.confirm(second)

        // then the use case that owns "create once, reuse thereafter" is consulted, not bypassed
        coVerify(exactly = 2) { ensureTransactionCostCategory.ensure() }
    }

    @Test
    fun `a payment with no charge asks for no category and writes one row`() = runTest {
        // given the M-PESA message reporting `Transaction cost, Ksh0.00` (Acceptance 2.3)
        pending(SmsFixtures.captured(id = id))

        // when
        useCase.confirm(id)

        // then nothing is created on the user's behalf that they have no fee to put in
        coVerify(exactly = 1) { transactionRepository.save(any()) }
        coVerify(exactly = 0) { ensureTransactionCostCategory.ensure() }
    }

    @Test
    fun `the reported tax is detail on the charge, never a third transaction`() = runTest {
        // given the `Transaction cost KES 66.00 Incl. Tax Amount KES 8.25` message
        pending(
            SmsFixtures.captured(id = id),
            fee = SmsFixtures.fee(parent = id, amount = 66.00, tax = 8.25),
        )
        val saved = mutableListOf<Transaction>()
        coEvery { transactionRepository.save(capture(saved)) } just runs

        // when
        useCase.confirm(id)

        // then (FR-019)
        saved.size shouldBe 2
        saved.last().shouldBeInstanceOf<Expense>().value.amount.value shouldBe 66.00
        saved.last().description?.value shouldBe "Incl. tax KES 8.25\nRef: UGP7B0ITE4"
    }

    @Test
    fun `a charge with no payment behind it confirms on its own, under the cost category`() =
        runTest {
            // given a message that reported nothing but a charge (FR-020)
            coEvery { capturedTransactionRepository.findById(id) } returns CapturedEntry(
                principal = SmsFixtures.fee(parent = null, amount = 33.00, id = id),
                fee = null,
            )
            val saved = slot<Transaction>()
            coEvery { transactionRepository.save(capture(saved)) } just runs

            // when
            val result = useCase.confirm(id)

            // then
            result.shouldBeRight()
            saved.captured.shouldBeInstanceOf<Expense>().value.amount.value shouldBe 33.00
            saved.captured.category shouldBe CostCategory
        }

    @Test
    fun `a charge that cannot be categorised rolls the payment back with it`() = runTest {
        // given
        pending(SmsFixtures.captured(id = id), fee = SmsFixtures.fee(parent = id))
        coEvery { ensureTransactionCostCategory.ensure() } returns "no disk".left()

        // when
        val result = useCase.confirm(id)

        // then neither row reached the ledger and the item is still there to retry (SC-003)
        result.shouldBeLeft() shouldBe ConfirmCaptureError.CategoryCreationFailed("no disk")
        coVerify(exactly = 0) { transactionRepository.save(any()) }
        coVerify(exactly = 0) { capturedTransactionRepository.deleteEntry(any()) }
    }

    @Test
    fun `a charge that fails to insert takes the payment down with it`() = runTest {
        // given the second save is the one that fails
        pending(SmsFixtures.captured(id = id), fee = SmsFixtures.fee(parent = id))
        var calls = 0
        coEvery { transactionRepository.save(any()) } answers {
            check(++calls != 2) { "disk full" }
        }

        // when
        val result = useCase.confirm(id)

        // then the whole block rolled back, so no half-recorded payment survives
        result.shouldBeLeft() shouldBe ConfirmCaptureError.Persistence("IllegalStateException")
        coVerify(exactly = 0) { capturedTransactionRepository.deleteEntry(any()) }
    }

    @Test
    fun `confirming removes the charge from review together with its payment`() = runTest {
        // given
        pending(SmsFixtures.captured(id = id), fee = SmsFixtures.fee(parent = id))

        // when
        useCase.confirm(id)

        // then `deleteEntry` is the call that covers both rows (FR-018)
        coVerify(exactly = 1) { capturedTransactionRepository.deleteEntry(id) }
    }

    private fun pending(captured: CapturedTransaction, fee: CapturedTransaction? = null) {
        coEvery { capturedTransactionRepository.findById(captured.id) } returns
            CapturedEntry(principal = captured, fee = fee)
    }

    /** Runs the block inline, so an exception still escapes exactly as Room would let it. */
    private fun passThroughTransaction() {
        coEvery { db.withTransaction(any<suspend () -> Any?>()) } coAnswers {
            // Arg 0 is the receiver of a statically mocked extension function; arg 1 is the block.
            secondArg<suspend () -> Any?>().invoke()
        }
    }

    /** Fixed rather than mocked: the memory's `updatedAt` is incidental to every case here. */
    private object FixedTimeProvider : TimeProvider {
        override fun getZoneId(): ZoneId = ZoneId.of("UTC")
        override fun utcNow(): Instant = SmsFixtures.CapturedAt
        override fun localNow(): LocalDateTime = LocalDateTime.ofInstant(utcNow(), getZoneId())
        override fun localDateNow(): LocalDate = localNow().toLocalDate()
        override fun localTimeNow(): LocalTime = localNow().toLocalTime()
    }

    private companion object {
        private const val ROOM_DATABASE_KT = "androidx.room.RoomDatabaseKt"

        /** The date the user corrects to in the FR-023 case: an hour earlier than parsed. */
        private val CorrectedTime: Instant = Instant.parse("2026-07-25T15:50:00Z")

        /** Whatever `EnsureTransactionCostCategoryUseCase` returns; its identity is the point. */
        private val CostCategory =
            CategoryId(UUID.fromString("00000000-0000-0000-0000-0000000000c1"))
    }
}
