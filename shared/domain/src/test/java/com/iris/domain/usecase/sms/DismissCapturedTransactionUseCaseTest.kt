package com.iris.domain.usecase.sms

import androidx.room.withTransaction
import arrow.core.right
import com.iris.base.time.TimeProvider
import com.iris.data.db.IrisRoomDatabase
import com.iris.data.model.sms.CapturedEntry
import com.iris.data.model.sms.CapturedKind
import com.iris.data.model.sms.CapturedTransaction
import com.iris.data.model.sms.CapturedTransactionId
import com.iris.data.model.sms.MessageFingerprint
import com.iris.data.model.sms.ProcessedMessage
import com.iris.data.model.sms.ProcessedOutcome
import com.iris.data.repository.CapturedTransactionRepository
import com.iris.data.repository.FinancialSenderRepository
import com.iris.data.repository.ProcessedMessageRepository
import com.iris.sms.parser.SmsParser
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
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
import java.util.UUID

/**
 * Dismissing has one job beyond deleting a row: making sure the same message can never bring the
 * item back (FR-025). Both halves are asserted, including the end-to-end "and now capture refuses
 * it" that is the whole point.
 */
class DismissCapturedTransactionUseCaseTest {

    private val db = mockk<IrisRoomDatabase>()
    private val capturedTransactionRepository =
        mockk<CapturedTransactionRepository>(relaxUnitFun = true)
    private val processedMessageRepository = mockk<ProcessedMessageRepository>(relaxUnitFun = true)
    private val senderRepository = mockk<FinancialSenderRepository>()
    private val timeProvider = mockk<TimeProvider>()

    private val id = CapturedTransactionId(UUID.randomUUID())
    private lateinit var useCase: DismissCapturedTransactionUseCase

    @Before
    fun setup() {
        mockkStatic(ROOM_DATABASE_KT)
        coEvery { db.withTransaction(any<suspend () -> Any?>()) } coAnswers {
            secondArg<suspend () -> Any?>().invoke()
        }
        every { timeProvider.utcNow() } returns SmsFixtures.CapturedAt
        coEvery { senderRepository.findAll() } returns listOf(SmsFixtures.sender())
        coEvery { capturedTransactionRepository.findById(id) } returns
            CapturedEntry(principal = SmsFixtures.captured(id = id), fee = null)

        useCase = DismissCapturedTransactionUseCase(
            db = db,
            capturedTransactionRepository = capturedTransactionRepository,
            processedMessageRepository = processedMessageRepository,
            senderRepository = senderRepository,
            timeProvider = timeProvider,
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(ROOM_DATABASE_KT)
    }

    @Test
    fun `dismissing deletes the captured row and inserts nothing`() = runTest {
        // when
        val result = useCase.dismiss(id)

        // then
        result.shouldBeRight()
        coVerify(exactly = 1) { capturedTransactionRepository.deleteEntry(id) }
    }

    @Test
    fun `dismissing records the outcome so the decision survives a re-delivery`() = runTest {
        // given
        val recorded = slot<ProcessedMessage>()
        coEvery { processedMessageRepository.record(capture(recorded)) } just runs

        // when
        useCase.dismiss(id)

        // then
        recorded.captured.outcome shouldBe ProcessedOutcome.Dismissed
        recorded.captured.fingerprint shouldBe MessageFingerprint.unsafe("mpesa:UGP7B0ITE4")
        recorded.captured.sender shouldBe SmsFixtures.Mpesa
    }

    @Test
    fun `a dismissed message is refused by capture when it arrives again`() = runTest {
        // given a processed-message store that remembers, and a capture path over the same store
        val handled = mutableSetOf<String>()
        coEvery { processedMessageRepository.record(any()) } answers {
            handled += firstArg<ProcessedMessage>().fingerprint.value
        }
        // A value class arrives at the mock as its underlying String, not as the wrapper.
        coEvery { processedMessageRepository.isProcessed(any()) } answers {
            firstArg<String>() in handled
        }
        val capture = captureUseCase()

        // when the user dismisses, and the identical message is then re-delivered
        useCase.dismiss(id)
        val result = capture.capture(SmsFixtures.message())

        // then it never comes back
        result.shouldBeLeft() shouldBe SmsCaptureError.AlreadyProcessed(
            MessageFingerprint.unsafe("mpesa:UGP7B0ITE4"),
        )
        coVerify(exactly = 0) { capturedTransactionRepository.saveEntry(any()) }
    }

    @Test
    fun `an item with no reference is still deleted, even though its key cannot be rebuilt`() =
        runTest {
            // given a wording that carried no reference code
            coEvery { capturedTransactionRepository.findById(id) } returns CapturedEntry(
                principal = SmsFixtures.captured(id = id, reference = null),
                fee = null,
            )

            // when
            val result = useCase.dismiss(id)

            // then the CAPTURED row written at capture time keeps blocking re-delivery
            result.shouldBeRight()
            coVerify(exactly = 1) { capturedTransactionRepository.deleteEntry(id) }
            coVerify(exactly = 0) { processedMessageRepository.record(any()) }
        }

    @Test
    fun `dismissing something already gone is reported, not crashed`() = runTest {
        // given
        coEvery { capturedTransactionRepository.findById(id) } returns null

        // when
        val result = useCase.dismiss(id)

        // then
        result.shouldBeLeft() shouldBe DismissCaptureError.CapturedItemGone(id)
    }

    @Test
    fun `a failed delete is reported as persistence, leaving the item in place`() = runTest {
        // given
        coEvery { capturedTransactionRepository.deleteEntry(id) } throws
            IllegalStateException("disk full")

        // when
        val result = useCase.dismiss(id)

        // then
        result.shouldBeLeft() shouldBe DismissCaptureError.Persistence("IllegalStateException")
    }

    // --- User Story 2: transaction costs ------------------------------------------------------

    @Test
    fun `discarding a payment takes its charge with it by default`() = runTest {
        // given a payment with the charge captured beside it
        withFee()

        // when the user dismisses without saying anything about the fee
        val result = useCase.dismiss(id)

        // then both rows go, in one transaction — no orphaned cost is left behind (FR-018)
        result.shouldBeRight()
        coVerify(exactly = 1) { capturedTransactionRepository.deleteEntry(id) }
        coVerify(exactly = 0) { capturedTransactionRepository.deleteById(any()) }
    }

    @Test
    fun `the user can discard the payment and keep the charge`() = runTest {
        // given
        val fee = withFee()
        val kept = slot<CapturedTransaction>()
        coEvery { capturedTransactionRepository.save(capture(kept)) } just runs

        // when
        val result = useCase.dismiss(id, alsoRemoveFee = false)

        // then the charge survives on its own terms, no longer pointing at a row that is gone
        result.shouldBeRight()
        kept.captured.id shouldBe fee.id
        kept.captured.kind.shouldBeInstanceOf<CapturedKind.Fee>().parent shouldBe null
        coVerify(exactly = 1) { capturedTransactionRepository.deleteById(id) }
        coVerify(exactly = 0) { capturedTransactionRepository.deleteEntry(any()) }
    }

    @Test
    fun `keeping the charge keeps the tax that was reported with it`() = runTest {
        // given the KCB charge that reported its tax component
        withFee(tax = 8.25)
        val kept = slot<CapturedTransaction>()
        coEvery { capturedTransactionRepository.save(capture(kept)) } just runs

        // when
        useCase.dismiss(id, alsoRemoveFee = false)

        // then detaching the fee does not quietly drop the detail on it (FR-019)
        kept.captured.kind.shouldBeInstanceOf<CapturedKind.Fee>().tax?.value shouldBe 8.25
    }

    @Test
    fun `keeping a charge that is not there is not an error`() = runTest {
        // given a payment that reported no cost at all

        // when the flag arrives anyway, as it will from a stale view state
        val result = useCase.dismiss(id, alsoRemoveFee = false)

        // then
        result.shouldBeRight()
        coVerify(exactly = 0) { capturedTransactionRepository.save(any()) }
    }

    @Test
    fun `a charge that cannot be detached leaves the payment in place`() = runTest {
        // given
        withFee()
        coEvery { capturedTransactionRepository.save(any()) } throws
            IllegalStateException("disk full")

        // when
        val result = useCase.dismiss(id, alsoRemoveFee = false)

        // then nothing was half-dismissed
        result.shouldBeLeft() shouldBe DismissCaptureError.Persistence("IllegalStateException")
        coVerify(exactly = 0) { capturedTransactionRepository.deleteById(any()) }
    }

    /** A pending payment with a charge attached, as capture writes it. */
    private fun withFee(tax: Double? = null): CapturedTransaction {
        val fee = SmsFixtures.fee(parent = id, tax = tax)
        coEvery { capturedTransactionRepository.findById(id) } returns CapturedEntry(
            principal = SmsFixtures.captured(id = id),
            fee = fee,
        )
        return fee
    }

    /** A capture path sharing this test's processed-message store, so FR-025 is testable end to end. */
    private fun captureUseCase(): CaptureSmsUseCase {
        val gate = mockk<SmsCaptureGate>()
        coEvery { gate.isEnabled() } returns true
        coEvery { gate.hasReceivePermission() } returns true
        val parser = mockk<SmsParser>()
        every { parser.parse(any()) } returns SmsFixtures.parsed().right()
        coEvery { senderRepository.findEnabled(any()) } returns SmsFixtures.sender()
        val detect = mockk<DetectDuplicateTransactionUseCase>()
        coEvery { detect.detect(any(), any(), any(), any()) } returns null

        return CaptureSmsUseCase(
            gate = gate,
            senderRepository = senderRepository,
            parser = parser,
            processedMessageRepository = processedMessageRepository,
            capturedTransactionRepository = capturedTransactionRepository,
            detectDuplicate = detect,
            timeProvider = timeProvider,
        )
    }

    private companion object {
        private const val ROOM_DATABASE_KT = "androidx.room.RoomDatabaseKt"
    }
}
