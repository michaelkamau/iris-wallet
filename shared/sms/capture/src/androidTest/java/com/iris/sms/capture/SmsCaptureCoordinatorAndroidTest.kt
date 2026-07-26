package com.iris.sms.capture

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iris.base.TestDispatchersProvider
import com.iris.base.time.TimeProvider
import com.iris.data.DataObserver
import com.iris.data.db.IrisRoomDatabase
import com.iris.data.model.Account
import com.iris.data.model.AccountId
import com.iris.data.model.Expense
import com.iris.data.model.PositiveValue
import com.iris.data.model.TransactionId
import com.iris.data.model.TransactionMetadata
import com.iris.data.model.primitive.AssetCode
import com.iris.data.model.primitive.ColorInt
import com.iris.data.model.primitive.NotBlankTrimmedString
import com.iris.data.model.primitive.PositiveDouble
import com.iris.data.model.sms.CaptureOrigin
import com.iris.data.model.sms.CapturedKind
import com.iris.data.model.sms.FinancialSender
import com.iris.data.model.sms.MessageFingerprint
import com.iris.data.model.sms.MoneyDirection
import com.iris.data.model.sms.RuleSetId
import com.iris.data.model.sms.SenderId
import com.iris.data.repository.AccountRepository
import com.iris.data.repository.CapturedTransactionRepository
import com.iris.data.repository.CurrencyRepository
import com.iris.data.repository.FinancialSenderRepository
import com.iris.data.repository.ProcessedMessageRepository
import com.iris.data.repository.RepositoryMemoFactory
import com.iris.data.repository.TagRepository
import com.iris.data.repository.TransactionRepository
import com.iris.data.repository.mapper.AccountMapper
import com.iris.data.repository.mapper.CapturedTransactionMapper
import com.iris.data.repository.mapper.FinancialSenderMapper
import com.iris.data.repository.mapper.TagMapper
import com.iris.data.repository.mapper.TransactionMapper
import com.iris.domain.usecase.sms.CaptureSmsUseCase
import com.iris.domain.usecase.sms.DetectDuplicateTransactionUseCase
import com.iris.domain.usecase.sms.SmsCaptureGate
import com.iris.sms.parser.RuleDrivenSmsParser
import com.iris.sms.parser.primitive.AmountParser
import com.iris.sms.parser.primitive.CounterpartyNormalizer
import com.iris.sms.parser.primitive.PromoTailStripper
import com.iris.sms.parser.primitive.SmsDateTimeParser
import com.iris.sms.parser.rules.CompiledRuleCatalogSource
import com.iris.sms.parser.rules.DtbRules
import com.iris.sms.parser.rules.KcbRules
import com.iris.sms.parser.rules.MpesaRules
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * The broadcast path, end to end, against real SQLite on a real device.
 *
 * Everything below the coordinator is the production object: the real rule catalogue, the real
 * parser, the real repositories and a real Room database. Only the two things that cannot exist on
 * a test device are stood in for — the preference/permission gate and the notification poster.
 *
 * The point of running this on a device rather than on the JVM is the database. Dedupe lives in a
 * unique index on `processed_messages`, the captured row round-trips through Room's type
 * converters, and none of that is exercised by a mocked repository.
 */
@RunWith(AndroidJUnit4::class)
class SmsCaptureCoordinatorAndroidTest {

    private lateinit var db: IrisRoomDatabase
    private lateinit var coordinator: SmsCaptureCoordinator
    private lateinit var capturedTransactions: CapturedTransactionRepository
    private lateinit var processedMessages: ProcessedMessageRepository
    private lateinit var senders: FinancialSenderRepository
    private lateinit var accounts: AccountRepository
    private lateinit var transactions: TransactionRepository

    private val gate = TestGate()

    @Before
    fun setup(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, IrisRoomDatabase::class.java).build()

        val memoFactory = RepositoryMemoFactory(DataObserver(), TestDispatchersProvider)
        val accountRepository = AccountRepository(
            mapper = AccountMapper(
                currencyRepository = CurrencyRepository(
                    settingsDao = db.settingsDao,
                    writeSettingsDao = db.writeSettingsDao,
                    dispatchersProvider = TestDispatchersProvider,
                ),
            ),
            accountDao = db.accountDao,
            writeAccountDao = db.writeAccountDao,
            dispatchersProvider = TestDispatchersProvider,
            memoFactory = memoFactory,
        )
        val transactionRepository = TransactionRepository(
            mapper = TransactionMapper(accountRepository),
            transactionDao = db.transactionDao,
            writeTransactionDao = db.writeTransactionDao,
            dispatchersProvider = TestDispatchersProvider,
            tagRepository = TagRepository(
                mapper = TagMapper(),
                tagDao = db.tagDao,
                tagAssociationDao = db.tagAssociationDao,
                writeTagDao = db.writeTagDao,
                writeTagAssociationDao = db.writeTagAssociationDao,
                dispatchersProvider = TestDispatchersProvider,
                memoFactory = memoFactory,
            ),
        )

        capturedTransactions = CapturedTransactionRepository(
            mapper = CapturedTransactionMapper(),
            dao = db.capturedTransactionDao,
            writeDao = db.writeCapturedTransactionDao,
            db = db,
            dispatchersProvider = TestDispatchersProvider,
        )
        processedMessages = ProcessedMessageRepository(
            dao = db.processedMessageDao,
            writeDao = db.writeProcessedMessageDao,
            dispatchersProvider = TestDispatchersProvider,
        )
        senders = FinancialSenderRepository(
            mapper = FinancialSenderMapper(),
            dao = db.financialSenderDao,
            writeDao = db.writeFinancialSenderDao,
            dispatchersProvider = TestDispatchersProvider,
        )

        coordinator = SmsCaptureCoordinator(
            gate = gate,
            captureSms = CaptureSmsUseCase(
                gate = gate,
                senderRepository = senders,
                parser = RuleDrivenSmsParser(
                    catalog = CompiledRuleCatalogSource(
                        setOf(MpesaRules.ruleSet, DtbRules.ruleSet, KcbRules.ruleSet),
                    ),
                    amountParser = AmountParser(),
                    dateTimeParser = SmsDateTimeParser(),
                    counterpartyNormalizer = CounterpartyNormalizer(),
                    promoStripper = PromoTailStripper(),
                ),
                processedMessageRepository = processedMessages,
                capturedTransactionRepository = capturedTransactions,
                detectDuplicate = DetectDuplicateTransactionUseCase(transactionRepository),
                timeProvider = FixedTimeProvider,
            ),
            capturedTransactionRepository = capturedTransactions,
            notifier = SmsCaptureNotifier(context),
            dispatchersProvider = TestDispatchersProvider,
        )

        accounts = accountRepository
        transactions = transactionRepository
        senders.save(MpesaSender)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun aDeliveredMessageBecomesExactlyOnePendingRow(): Unit = runBlocking {
        // given a message from an enrolled sender

        // when
        deliver(MPESA_SENDER, MPESA_PAID_BODY)

        // then
        val pending = capturedTransactions.findAllPending()
        pending.size shouldBe 1
        val captured = pending.single().principal
        captured.amount.value shouldBe 1350.00
        captured.reference?.value shouldBe "UGP7B0ITE4"
        captured.counterparty?.value shouldBe "James Kinyua Mwangi"
        (captured.kind as CapturedKind.Principal).direction shouldBe MoneyDirection.MoneyOut
        captured.account shouldBe MpesaSender.account
        captured.origin shouldBe CaptureOrigin.LiveBroadcast
    }

    @Test
    fun redeliveringTheSameMessageDoesNotCaptureItTwice(): Unit = runBlocking {
        // given the message has already been captured
        deliver(MPESA_SENDER, MPESA_PAID_BODY)

        // when the exact same broadcast arrives again
        deliver(MPESA_SENDER, MPESA_PAID_BODY)

        // then
        capturedTransactions.findAllPending().size shouldBe 1
    }

    @Test
    fun redeliveryIsDedupedEvenWhenTheReceiptTimeDiffers(): Unit = runBlocking {
        // given
        deliver(MPESA_SENDER, MPESA_PAID_BODY, receivedAt = 1_700_000_000_000L)

        // when the same message is replayed hours later
        deliver(MPESA_SENDER, MPESA_PAID_BODY, receivedAt = 1_700_040_000_000L)

        // then the fingerprint ignores receipt time, so this is still one capture
        capturedTransactions.findAllPending().size shouldBe 1
    }

    @Test
    fun aCapturedMessageIsRecordedAsProcessed(): Unit = runBlocking {
        // given
        deliver(MPESA_SENDER, MPESA_PAID_BODY)

        // when
        val processed = processedMessages.isProcessed(MessageFingerprint.unsafe("mpesa:UGP7B0ITE4"))

        // then the reference-derived fingerprint is what makes re-delivery cheap to reject
        processed shouldBe true
    }

    @Test
    fun twoDifferentMessagesFromTheSameSenderBothLand(): Unit = runBlocking {
        // given
        deliver(MPESA_SENDER, MPESA_PAID_BODY)

        // when
        deliver(MPESA_SENDER, MPESA_SECOND_PAID_BODY)

        // then
        capturedTransactions.findAllPending().size shouldBe 2
    }

    @Test
    fun aMessageFromAnUnknownSenderIsNeverInspected(): Unit = runBlocking {
        // given a sender the user never enrolled

        // when
        deliver("RANDOMSHOP", MPESA_PAID_BODY)

        // then
        capturedTransactions.findAllPending().shouldBeEmpty()
        processedMessages.isProcessed(MessageFingerprint.unsafe("mpesa:UGP7B0ITE4")) shouldBe false
    }

    @Test
    fun aBalanceEnquiryFromAnEnrolledSenderIsNotCaptured(): Unit = runBlocking {
        // given

        // when
        deliver(MPESA_SENDER, BALANCE_BODY)

        // then
        capturedTransactions.findAllPending().shouldBeEmpty()
    }

    @Test
    fun nothingIsCapturedWhileTheMasterSwitchIsOff(): Unit = runBlocking {
        // given
        gate.enabled = false

        // when
        deliver(MPESA_SENDER, MPESA_PAID_BODY)

        // then
        capturedTransactions.findAllPending().shouldBeEmpty()
        processedMessages.isProcessed(MessageFingerprint.unsafe("mpesa:UGP7B0ITE4")) shouldBe false
    }

    @Test
    fun nothingIsCapturedWithoutTheReceiveSmsPermission(): Unit = runBlocking {
        // given
        gate.permitted = false

        // when
        deliver(MPESA_SENDER, MPESA_PAID_BODY)

        // then
        capturedTransactions.findAllPending().shouldBeEmpty()
    }

    /**
     * FR-032. Switching the feature off has to leave the app exactly as it was, and "exactly as
     * it was" includes the money already in it — not just the absence of new captures.
     */
    @Test
    fun withCaptureDisabledTheExistingLedgerIsUntouched(): Unit = runBlocking {
        // given a transaction the user entered by hand, and capture switched off
        val existing = existingTransaction()
        gate.enabled = false

        // when a payment message arrives anyway
        deliver(MPESA_SENDER, MPESA_PAID_BODY)

        // then nothing was captured, nothing was recorded as seen, and the ledger is unchanged
        capturedTransactions.findAllPending().shouldBeEmpty()
        capturedTransactions.pendingCount().first() shouldBe 0
        processedMessages.isProcessed(MessageFingerprint.unsafe("mpesa:UGP7B0ITE4")) shouldBe false
        transactions.findById(existing).shouldNotBeNull()
        transactions.findAll().size shouldBe 1
    }

    /**
     * The same, for a user who was asked and said no. A refusal must cost them nothing beyond
     * the feature itself (FR-032, Acceptance 4.2).
     */
    @Test
    fun withThePermissionDeniedTheExistingLedgerIsUntouched(): Unit = runBlocking {
        // given
        val existing = existingTransaction()
        gate.permitted = false

        // when
        deliver(MPESA_SENDER, MPESA_PAID_BODY)

        // then
        capturedTransactions.findAllPending().shouldBeEmpty()
        transactions.findById(existing).shouldNotBeNull()
        transactions.findAll().size shouldBe 1
    }

    /**
     * Disabling mid-stream. Anything already captured is the user's work, not the feature's, so
     * it survives the switch being turned off (Acceptance 4.4).
     */
    @Test
    fun disablingCaptureLeavesAlreadyCapturedItemsWhereTheyAre(): Unit = runBlocking {
        // given one message was captured while the feature was on
        deliver(MPESA_SENDER, MPESA_PAID_BODY)
        capturedTransactions.findAllPending().size shouldBe 1

        // when the user switches it off and a second message arrives
        gate.enabled = false
        deliver(MPESA_SENDER, MPESA_SECOND_PAID_BODY)

        // then the second is ignored and the first is still waiting for them
        capturedTransactions.findAllPending().size shouldBe 1
    }

    private suspend fun existingTransaction(): TransactionId {
        val id = TransactionId(UUID.randomUUID())
        accounts.save(
            Account(
                id = AccountId(EXISTING_ACCOUNT),
                name = NotBlankTrimmedString.unsafe("Cash"),
                asset = AssetCode.unsafe("KES"),
                color = ColorInt(0),
                icon = null,
                includeInBalance = true,
                orderNum = 0.0,
            ),
        )
        transactions.save(
            Expense(
                id = id,
                title = NotBlankTrimmedString.unsafe("Lunch"),
                description = null,
                category = null,
                time = FixedTimeProvider.utcNow(),
                settled = true,
                metadata = TransactionMetadata(
                    recurringRuleId = null,
                    paidForDateTime = null,
                    loanId = null,
                    loanRecordId = null,
                ),
                tags = emptyList(),
                value = PositiveValue(
                    amount = PositiveDouble.unsafe(500.0),
                    asset = AssetCode.unsafe("KES"),
                ),
                account = AccountId(EXISTING_ACCOUNT),
            ),
        )
        return id
    }

    @Test
    fun aMultipartMessageIsJoinedBeforeItIsParsed(): Unit = runBlocking {
        // given the confirmation arrives as two parts of one broadcast
        val split = MPESA_PAID_BODY.chunked(MPESA_PAID_BODY.length / 2 + 1)

        // when
        deliverParts(split.map { SmsPart(MPESA_SENDER, it, RECEIVED_AT) })

        // then joining is what makes the whole message parseable at all
        capturedTransactions.findAllPending().size shouldBe 1
    }

    @Test
    fun theBroadcastIsAlwaysReleasedEvenWhenNothingIsCaptured(): Unit = runBlocking {
        // given a message that will be rejected
        var finished = false

        // when
        awaitCapture(listOf(SmsPart("RANDOMSHOP", MPESA_PAID_BODY, RECEIVED_AT))) { finished = true }

        // then the receiver's `goAsync` token is released regardless (FR-005)
        finished shouldBe true
    }

    // --- User Story 2: transaction costs ------------------------------------------------------

    @Test
    fun aReportedChargeBecomesASecondRowOnTheSameEntry(): Unit = runBlocking {
        // given the quickstart V4 DTB transfer
        senders.save(DtbSender)

        // when
        deliver(DTB_SENDER, DTB_TRANSFER_BODY)

        // then one reviewable item, carrying its charge (FR-016)
        val entry = capturedTransactions.findAllPending().single()
        entry.principal.amount.value shouldBe 6000.00
        entry.principal.counterparty?.value shouldBe "Michael Kamau Njuguna"
        entry.fee?.amount?.value shouldBe 59.76
        (entry.fee?.kind as CapturedKind.Fee).parent shouldBe entry.principal.id
    }

    @Test
    fun theChargeAndItsPrincipalSurviveTheDatabaseSeparately(): Unit = runBlocking {
        // given
        senders.save(DtbSender)

        // when
        deliver(DTB_SENDER, DTB_TRANSFER_BODY)

        // then two rows exist in SQLite, joined only by the parent link
        db.capturedTransactionDao.findAll().size shouldBe 2
        capturedTransactions.findAllPending().size shouldBe 1
    }

    @Test
    fun aReportedTaxIsRetainedOnTheChargeAndNotAsAThirdRow(): Unit = runBlocking {
        // given the KCB message, whose promo tail must not corrupt the cost or the tax
        senders.save(KcbSender)

        // when
        deliver(KCB_SENDER, KCB_SEND_TO_MPESA_BODY)

        // then (FR-019)
        val entry = capturedTransactions.findAllPending().single()
        entry.principal.amount.value shouldBe 13000.00
        entry.principal.reference?.value shouldBe "UGO680QNMS"
        val fee = entry.fee?.kind as CapturedKind.Fee
        entry.fee?.amount?.value shouldBe 66.00
        fee.tax?.value shouldBe 8.25
        db.capturedTransactionDao.findAll().size shouldBe 2
    }

    @Test
    fun aZeroTransactionCostCreatesNoChargeRowAtAll(): Unit = runBlocking {
        // given the M-PESA wording reporting `Transaction cost, Ksh0.00` (Acceptance 2.3)

        // when
        deliver(MPESA_SENDER, MPESA_PAID_BODY)

        // then the row cannot even be built — `PositiveDouble` refuses zero
        capturedTransactions.findAllPending().single().fee shouldBe null
        db.capturedTransactionDao.findAll().size shouldBe 1
    }

    private suspend fun deliver(sender: String, body: String, receivedAt: Long = RECEIVED_AT) {
        deliverParts(listOf(SmsPart(sender, body, receivedAt)))
    }

    private suspend fun deliverParts(parts: List<SmsPart>) {
        awaitCapture(parts) {}
    }

    /**
     * Capture is deliberately fire-and-forget in production, so the test waits on the same
     * callback the receiver uses to release the broadcast rather than on a sleep.
     */
    private suspend fun awaitCapture(parts: List<SmsPart>, onFinished: () -> Unit) {
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            coordinator.captureAsync(parts) {
                onFinished()
                continuation.resumeWith(Result.success(Unit))
            }
        }
    }

    private class TestGate(
        var enabled: Boolean = true,
        var permitted: Boolean = true,
    ) : SmsCaptureGate {
        override suspend fun isEnabled(): Boolean = enabled
        override fun hasReceivePermission(): Boolean = permitted
        override fun canReadInbox(): Boolean = permitted
    }

    private object FixedTimeProvider : TimeProvider {
        private val now: Instant = Instant.parse("2026-07-26T09:00:00Z")
        override fun getZoneId(): ZoneId = ZoneId.of("Africa/Nairobi")
        override fun utcNow(): Instant = now
        override fun localNow(): LocalDateTime = LocalDateTime.ofInstant(now, getZoneId())
        override fun localDateNow(): LocalDate = localNow().toLocalDate()
        override fun localTimeNow(): LocalTime = localNow().toLocalTime()
    }

    private companion object {
        private const val MPESA_SENDER = "MPESA"
        private const val RECEIVED_AT = 1_785_000_000_000L

        private val MpesaSender = FinancialSender(
            id = SenderId.unsafe(MPESA_SENDER),
            displayName = NotBlankTrimmedString.unsafe("M-PESA"),
            ruleSet = RuleSetId.unsafe("mpesa"),
            account = null,
            enabled = true,
        )

        private val EXISTING_ACCOUNT = UUID.fromString("dddddddd-0000-0000-0000-000000000001")

        private const val MPESA_PAID_BODY =
            "UGP7B0ITE4 Confirmed Ksh1,350.00 paid to james Kinyua Mwangi9. on 25/7/26 at " +
                "7:50 PM.New M-PESA balance is Ksh1,242.02. Transaction cost, Ksh0.00. " +
                "Amount you can transact within the day is 498,650.00. " +
                "Download My OneApp on https://saf.cx/lPKcC"

        private const val MPESA_SECOND_PAID_BODY =
            "UGO7B0DQ91 Confirmed. Ksh3,050.00 paid to FRANK INN KIKUYU. on 24/7/26 at " +
                "4:10 PM.New M-PESA balance is Ksh2,592.02. Transaction cost, Ksh0.00."

        private const val DTB_SENDER = "DTB-KENYA"
        private const val KCB_SENDER = "KCB"

        private val DtbSender = MpesaSender.copy(
            id = SenderId.unsafe(DTB_SENDER),
            displayName = NotBlankTrimmedString.unsafe("Diamond Trust Bank"),
            ruleSet = RuleSetId.unsafe("dtb"),
        )

        private val KcbSender = MpesaSender.copy(
            id = SenderId.unsafe(KCB_SENDER),
            displayName = NotBlankTrimmedString.unsafe("KCB Bank"),
            ruleSet = RuleSetId.unsafe("kcb"),
        )

        /** The quickstart V4 message, character for character. */
        private const val DTB_TRANSFER_BODY =
            "DTB 6000.00 KES has been successfully sent to Michael Kamau Njuguna. " +
                "Ref. AD3EA389C13A7 on 25 Jul 2026 at 17:41 EAT. Charges 59.76 KES"

        /** The KCB message from spec.md, promo tail and all. */
        private const val KCB_SEND_TO_MPESA_BODY =
            "MBNHEUH57FE6NEO9 Completed. Your SEND TO M-PESA request of KES 13,000.00 " +
                "from 135****819 to 254****956 - JACKLINE MUTHEU NGEI at 2026-07-24 07:19:54 PM " +
                "has been processed successfully. Transaction cost KES 66.00 Incl. Tax Amount " +
                "KES 8.25. M-PESA REF: UGO680QNMS. Pata extra cash with a flexible Mobile Loan. " +
                "Dial *522# or use the KCB App to check limit."

        private const val BALANCE_BODY =
            "UGP7B0ZZZZ Confirmed. Your M-PESA balance was Ksh1,242.02 on 25/7/26 at 7:55 PM."
    }
}
