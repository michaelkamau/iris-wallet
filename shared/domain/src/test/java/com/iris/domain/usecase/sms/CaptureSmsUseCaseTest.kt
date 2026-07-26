package com.iris.domain.usecase.sms

import arrow.core.left
import arrow.core.right
import com.iris.base.time.TimeProvider
import com.iris.data.model.TransactionId
import com.iris.data.model.sms.CapturedEntry
import com.iris.data.model.sms.CapturedKind
import com.iris.data.model.sms.MessageFingerprint
import com.iris.data.model.sms.ProcessedMessage
import com.iris.data.model.sms.ProcessedOutcome
import com.iris.data.repository.CapturedTransactionRepository
import com.iris.data.repository.FinancialSenderRepository
import com.iris.data.repository.ProcessedMessageRepository
import com.iris.domain.usecase.sms.SmsFixtures.Mpesa
import com.iris.sms.parser.SmsParseError
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
import io.mockk.runs
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * The capture funnel is the one place a message can turn into something the user has to deal with,
 * so every way it must decline is asserted here rather than left to the receiver to discover.
 */
class CaptureSmsUseCaseTest {

    private val gate = mockk<SmsCaptureGate>()
    private val senderRepository = mockk<FinancialSenderRepository>()
    private val parser = mockk<SmsParser>()
    private val processedMessageRepository = mockk<ProcessedMessageRepository>(relaxUnitFun = true)
    private val capturedTransactionRepository =
        mockk<CapturedTransactionRepository>(relaxUnitFun = true)
    private val detectDuplicate = mockk<DetectDuplicateTransactionUseCase>()
    private val timeProvider = mockk<TimeProvider>()

    private lateinit var useCase: CaptureSmsUseCase

    @Before
    fun setup() {
        every { timeProvider.utcNow() } returns SmsFixtures.CapturedAt
        coEvery { gate.isEnabled() } returns true
        coEvery { gate.hasReceivePermission() } returns true
        coEvery { gate.isCapturing() } returns true
        coEvery { processedMessageRepository.isProcessed(any()) } returns false
        coEvery { detectDuplicate.detect(any(), any(), any(), any()) } returns null
        coEvery { parser.parse(any()) } returns SmsFixtures.parsed().right()
        coEvery { senderRepository.findEnabled(any()) } returns SmsFixtures.sender()
        coEvery { senderRepository.findAll() } returns listOf(SmsFixtures.sender())

        useCase = CaptureSmsUseCase(
            gate = gate,
            senderRepository = senderRepository,
            parser = parser,
            processedMessageRepository = processedMessageRepository,
            capturedTransactionRepository = capturedTransactionRepository,
            detectDuplicate = detectDuplicate,
            timeProvider = timeProvider,
        )
    }

    @Test
    fun `capture is refused outright while the master switch is off`() = runTest {
        // given
        coEvery { gate.isEnabled() } returns false

        // when
        val result = useCase.capture(SmsFixtures.message())

        // then
        result.shouldBeLeft() shouldBe SmsCaptureError.CaptureDisabled
        coVerify(exactly = 0) { parser.parse(any()) }
        coVerify(exactly = 0) { capturedTransactionRepository.saveEntry(any()) }
    }

    @Test
    fun `capture is refused when the switch is on but the permission is not granted`() = runTest {
        // given
        coEvery { gate.hasReceivePermission() } returns false

        // when
        val result = useCase.capture(SmsFixtures.message())

        // then
        result.shouldBeLeft() shouldBe SmsCaptureError.PermissionMissing
        coVerify(exactly = 0) { parser.parse(any()) }
    }

    @Test
    fun `a sender the user never configured is never inspected`() = runTest {
        // given
        coEvery { senderRepository.findEnabled(any()) } returns null
        coEvery { senderRepository.findAll() } returns emptyList()

        // when
        val result = useCase.capture(SmsFixtures.message())

        // then
        result.shouldBeLeft() shouldBe SmsCaptureError.SenderNotConfigured(Mpesa)
        coVerify(exactly = 0) { parser.parse(any()) }
    }

    @Test
    fun `a known but switched-off sender is refused as disabled, not as unknown`() = runTest {
        // given
        coEvery { senderRepository.findEnabled(any()) } returns null
        coEvery { senderRepository.findAll() } returns listOf(SmsFixtures.sender(enabled = false))

        // when
        val result = useCase.capture(SmsFixtures.message())

        // then
        result.shouldBeLeft() shouldBe SmsCaptureError.SenderDisabled(Mpesa)
    }

    @Test
    fun `a message that was already handled is not captured a second time`() = runTest {
        // given
        coEvery { processedMessageRepository.isProcessed(any()) } returns true

        // when
        val result = useCase.capture(SmsFixtures.message())

        // then
        val error = result.shouldBeLeft()
        error shouldBe SmsCaptureError.AlreadyProcessed(
            MessageFingerprint.unsafe("mpesa:UGP7B0ITE4"),
        )
        coVerify(exactly = 0) { capturedTransactionRepository.saveEntry(any()) }
        coVerify(exactly = 0) { processedMessageRepository.record(any()) }
    }

    @Test
    fun `a sender with no account mapping still captures, with no account chosen`() = runTest {
        // given
        coEvery { senderRepository.findEnabled(any()) } returns SmsFixtures.sender(account = null)

        // when
        val result = useCase.capture(SmsFixtures.message())

        // then the item is held for review rather than filed against a guessed account
        val entry = result.shouldBeRight()
        entry.principal.account shouldBe null
        entry.principal.duplicateOf shouldBe null
        coVerify(exactly = 0) { detectDuplicate.detect(any(), any(), any(), any()) }
    }

    @Test
    fun `a message that is not a transaction is remembered as ignored, not captured`() = runTest {
        // given
        val cause = SmsParseError.NoMatchingPattern(SmsFixtures.parsed().ruleSet)
        coEvery { parser.parse(any()) } returns cause.left()
        val recorded = slot<ProcessedMessage>()
        coEvery { processedMessageRepository.record(capture(recorded)) } just runs

        // when
        val result = useCase.capture(SmsFixtures.message())

        // then
        result.shouldBeLeft() shouldBe SmsCaptureError.ParseFailure(cause)
        recorded.captured.outcome shouldBe ProcessedOutcome.Ignored
        recorded.captured.sender shouldBe Mpesa
        recorded.captured.fingerprint.value.startsWith("h:") shouldBe true
        coVerify(exactly = 0) { capturedTransactionRepository.saveEntry(any()) }
    }

    @Test
    fun `a captured message is written together with its proof of handling`() = runTest {
        // given
        val entry = slot<CapturedEntry>()
        val recorded = slot<ProcessedMessage>()
        coEvery { capturedTransactionRepository.saveEntry(capture(entry)) } just runs
        coEvery { processedMessageRepository.record(capture(recorded)) } just runs

        // when
        val result = useCase.capture(SmsFixtures.message())

        // then
        result.shouldBeRight().principal.reference?.value shouldBe "UGP7B0ITE4"
        entry.captured.principal.account shouldBe SmsFixtures.Account
        entry.captured.principal.capturedAt shouldBe SmsFixtures.CapturedAt
        entry.captured.fee shouldBe null
        recorded.captured.outcome shouldBe ProcessedOutcome.Captured
        recorded.captured.fingerprint shouldBe MessageFingerprint.unsafe("mpesa:UGP7B0ITE4")
    }

    @Test
    fun `a ledger row on the same day for the same account is flagged, never discarded`() =
        runTest {
            // given
            val existing = TransactionId(UUID.randomUUID())
            coEvery { detectDuplicate.detect(any(), any(), any(), any()) } returns existing

            // when
            val result = useCase.capture(SmsFixtures.message())

            // then the item still exists; the user decides
            result.shouldBeRight().principal.duplicateOf shouldBe existing
            coVerify(exactly = 1) { capturedTransactionRepository.saveEntry(any()) }
        }

    @Test
    fun `a failure to write is reported rather than thrown out of the receiver`() = runTest {
        // given
        coEvery { capturedTransactionRepository.saveEntry(any()) } throws
            IllegalStateException("db is gone")

        // when
        val result = useCase.capture(SmsFixtures.message())

        // then
        result.shouldBeLeft() shouldBe SmsCaptureError.Persistence("IllegalStateException")
    }

    @Test
    fun `the same message delivered twice yields exactly one captured item`() = runTest {
        // given a store that actually remembers what it was told
        val handled = mutableSetOf<String>()
        // A value class arrives at the mock as its underlying String, not as the wrapper.
        coEvery { processedMessageRepository.isProcessed(any()) } answers {
            firstArg<String>() in handled
        }
        coEvery { processedMessageRepository.record(any()) } answers {
            handled += firstArg<ProcessedMessage>().fingerprint.value
        }

        // when the identical message arrives over the broadcast and again out of the inbox
        val first = useCase.capture(SmsFixtures.message())
        val second = useCase.capture(SmsFixtures.message())

        // then
        first.shouldBeRight()
        second.shouldBeLeft() shouldBe SmsCaptureError.AlreadyProcessed(
            MessageFingerprint.unsafe("mpesa:UGP7B0ITE4"),
        )
        coVerify(exactly = 1) { capturedTransactionRepository.saveEntry(any()) }
    }

    @Test
    fun `a message with no reference still dedupes, on the digest of its body`() = runTest {
        // given a provider wording that carries no reference code at all
        coEvery { parser.parse(any()) } returns SmsFixtures.parsed(reference = null).right()
        val handled = mutableSetOf<String>()
        // A value class arrives at the mock as its underlying String, not as the wrapper.
        coEvery { processedMessageRepository.isProcessed(any()) } answers {
            firstArg<String>() in handled
        }
        coEvery { processedMessageRepository.record(any()) } answers {
            handled += firstArg<ProcessedMessage>().fingerprint.value
        }

        // when the same body arrives twice, with different receipt times
        val first = useCase.capture(SmsFixtures.message())
        val second = useCase.capture(
            SmsFixtures.message().copy(receivedAt = SmsFixtures.PaidAt.plusSeconds(3_600)),
        )

        // then the receipt time is not part of the key, so the second is still a duplicate
        first.shouldBeRight()
        second.shouldBeLeft().shouldBeInstanceOf<SmsCaptureError.AlreadyProcessed>()
        coVerify(exactly = 1) { capturedTransactionRepository.saveEntry(any()) }
    }

    // --- User Story 2: transaction costs ------------------------------------------------------

    @Test
    fun `a reported charge is captured beside the payment, not folded into it`() = runTest {
        // given the DTB transfer that reported `Charges 59.76 KES`
        coEvery { parser.parse(any()) } returns SmsFixtures.parsed(fee = 59.76).right()
        val saved = slot<CapturedEntry>()
        coEvery { capturedTransactionRepository.saveEntry(capture(saved)) } just runs

        // when
        useCase.capture(SmsFixtures.message()).shouldBeRight()

        // then two rows go in on one call, so review can never show a payment with a lost fee
        val entry = saved.captured
        entry.principal.amount.value shouldBe 1_350.0
        entry.fee?.amount?.value shouldBe 59.76
        coVerify(exactly = 1) { capturedTransactionRepository.saveEntry(any()) }
    }

    @Test
    fun `the charge points back at the payment it belongs to`() = runTest {
        // given
        coEvery { parser.parse(any()) } returns SmsFixtures.parsed(fee = 59.76).right()
        val saved = slot<CapturedEntry>()
        coEvery { capturedTransactionRepository.saveEntry(capture(saved)) } just runs

        // when
        useCase.capture(SmsFixtures.message())

        // then FR-018 has something to work with when the payment is discarded
        val entry = saved.captured
        entry.fee?.kind.shouldBeInstanceOf<CapturedKind.Fee>().parent shouldBe entry.principal.id
        entry.principal.kind.shouldBeInstanceOf<CapturedKind.Principal>()
    }

    @Test
    fun `a reported tax rides on the charge instead of becoming its own row`() = runTest {
        // given the KCB `Transaction cost KES 66.00 Incl. Tax Amount KES 8.25` wording
        coEvery { parser.parse(any()) } returns
            SmsFixtures.parsed(fee = 66.00, tax = 8.25).right()
        val saved = slot<CapturedEntry>()
        coEvery { capturedTransactionRepository.saveEntry(capture(saved)) } just runs

        // when
        useCase.capture(SmsFixtures.message())

        // then (FR-019)
        val fee = saved.captured.fee
        fee?.amount?.value shouldBe 66.00
        fee?.kind.shouldBeInstanceOf<CapturedKind.Fee>().tax?.value shouldBe 8.25
    }

    @Test
    fun `a zero charge produces no charge row at all`() = runTest {
        // given the M-PESA `Transaction cost, Ksh0.00` messages, which the parser cannot even
        // represent as a fee because `PositiveDouble` refuses zero (Acceptance 2.3)
        coEvery { parser.parse(any()) } returns SmsFixtures.parsed(fee = null).right()
        val saved = slot<CapturedEntry>()
        coEvery { capturedTransactionRepository.saveEntry(capture(saved)) } just runs

        // when
        useCase.capture(SmsFixtures.message())

        // then
        saved.captured.fee shouldBe null
    }

    @Test
    fun `a charge advice with no payment in it is captured on its own`() = runTest {
        // given a bank charge notification (FR-020)
        coEvery { parser.parse(any()) } returns
            SmsFixtures.parsed(amount = null, fee = 33.00).right()
        val saved = slot<CapturedEntry>()
        coEvery { capturedTransactionRepository.saveEntry(capture(saved)) } just runs

        // when
        useCase.capture(SmsFixtures.message()).shouldBeRight()

        // then the charge heads its own entry, with nothing to point back at
        val entry = saved.captured
        entry.fee shouldBe null
        entry.principal.amount.value shouldBe 33.00
        entry.principal.kind.shouldBeInstanceOf<CapturedKind.Fee>().parent shouldBe null
    }

    @Test
    fun `a standalone charge is checked against the ledger like any other head row`() = runTest {
        // given a charge the user had already entered by hand
        val existing = TransactionId(UUID.randomUUID())
        coEvery { parser.parse(any()) } returns
            SmsFixtures.parsed(amount = null, fee = 33.00).right()
        coEvery { detectDuplicate.detect(any(), any(), any(), any()) } returns existing
        val saved = slot<CapturedEntry>()
        coEvery { capturedTransactionRepository.saveEntry(capture(saved)) } just runs

        // when
        useCase.capture(SmsFixtures.message())

        // then it arrives in review already flagged, rather than silently doubling the charge
        saved.captured.principal.duplicateOf shouldBe existing
        // and it is compared once, not once per identity it could be read as
        coVerify(exactly = 1) { detectDuplicate.detect(any(), any(), any(), any()) }
    }

    @Test
    fun `a charge attached to a payment is not separately compared against the ledger`() =
        runTest {
            // given
            coEvery { parser.parse(any()) } returns SmsFixtures.parsed(fee = 59.76).right()

            // when
            useCase.capture(SmsFixtures.message())

            // then the fee is exactly as duplicated as its principal — no more, no less
            coVerify(exactly = 1) { detectDuplicate.detect(any(), any(), any(), any()) }
        }
}
