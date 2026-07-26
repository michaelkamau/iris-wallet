package com.iris.sms.review

import arrow.core.left
import arrow.core.right
import com.iris.data.model.Account
import com.iris.data.model.AccountId
import com.iris.data.model.TransactionId
import com.iris.data.model.primitive.AssetCode
import com.iris.data.model.primitive.ColorInt
import com.iris.data.model.primitive.NotBlankTrimmedString
import com.iris.data.model.primitive.PositiveDouble
import com.iris.data.model.sms.CaptureOrigin
import com.iris.data.model.sms.CapturedEntry
import com.iris.data.model.sms.CapturedKind
import com.iris.data.model.sms.CapturedTransaction
import com.iris.data.model.sms.CapturedTransactionId
import com.iris.data.model.sms.MoneyDirection
import com.iris.data.model.sms.ProviderReference
import com.iris.data.model.sms.SenderId
import com.iris.data.repository.AccountRepository
import com.iris.data.repository.CapturedTransactionRepository
import com.iris.domain.usecase.sms.ConfirmCaptureError
import com.iris.domain.usecase.sms.ConfirmCapturedTransactionUseCase
import com.iris.domain.usecase.sms.DismissCapturedTransactionUseCase
import com.iris.navigation.Navigation
import com.iris.ui.FormatMoneyUseCase
import com.iris.ui.testing.ComposeViewModelTest
import com.iris.ui.testing.runTest
import com.iris.ui.time.TimeFormatter
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

/**
 * The review inbox is the only thing standing between a parsed message and the user's money, so
 * these tests are mostly about refusal: what it will not confirm, and what it does not show.
 */
class SmsReviewViewModelTest : ComposeViewModelTest() {

    private val capturedTransactionRepository = mockk<CapturedTransactionRepository>()
    private val accountRepository = mockk<AccountRepository>()
    private val confirmCapturedTransaction = mockk<ConfirmCapturedTransactionUseCase>()
    private val dismissCapturedTransaction = mockk<DismissCapturedTransactionUseCase>()
    private val formatMoney = mockk<FormatMoneyUseCase>()
    private val navigation = mockk<Navigation>(relaxed = true)

    /** The pending table, so a confirm or a dismiss is observable through a reload. */
    private val pending = mutableListOf<CapturedEntry>()

    @Before
    fun setup() {
        pending.clear()
        coEvery { capturedTransactionRepository.findAllPending() } answers { pending.toList() }
        coEvery { accountRepository.findAll() } returns listOf(account)
        coEvery { formatMoney.format(any(), any()) } answers {
            String.format(java.util.Locale.US, "%,.2f", firstArg<Double>())
        }
        // MockK hands a `@JvmInline value class` argument to `answers` as its underlying type, so
        // the id arrives as a bare UUID; asking for `CapturedTransactionId` here throws.
        coEvery { confirmCapturedTransaction.confirm(any(), any(), any(), any()) } answers {
            pending.removeAll { it.principal.id.value == firstArg<UUID>() }
            TransactionId(UUID.randomUUID()).right()
        }
        coEvery { dismissCapturedTransaction.dismiss(any(), any()) } answers {
            pending.removeAll { it.principal.id.value == firstArg<UUID>() }
            Unit.right()
        }
    }

    @Test
    fun `an empty inbox reports nothing to review, not an empty list`() {
        // given nothing pending

        // when / then
        viewModel().runTest { this shouldBe SmsReviewState.Empty }
    }

    @Test
    fun `a pending item is shown with its amount, payee and message time already formatted`() {
        // given
        pending += entry(id = ReadyId, account = account.id)

        // when / then
        viewModel().runTest {
            val content = shouldBeInstanceOf<SmsReviewState.Content>()
            content.pendingCount shouldBe 1
            val item = content.items.single()
            item.amountFormatted shouldBe "-KES 1,350.00"
            item.counterparty shouldBe "James Kinyua Mwangi"
            // The message's own timestamp, not the moment it was captured (FR-014).
            item.timeFormatted shouldBe "25 Jul 2026, 19:50"
            item.reference shouldBe "UGP7B0ITE4"
            item.accountName shouldBe "M-PESA"
            item.status shouldBe ReviewStatusUi.ReadyToConfirm
        }
    }

    @Test
    fun `an item whose sender has no account mapping asks for one`() {
        // given
        pending += entry(id = NeedsAccountId, account = null)

        // when / then
        viewModel().runTest {
            val item = shouldBeInstanceOf<SmsReviewState.Content>().items.single()
            item.accountName shouldBe null
            item.status shouldBe ReviewStatusUi.NeedsAccount
        }
    }

    @Test
    fun `confirming an item that still needs an account does nothing at all`() {
        // given
        pending += entry(id = NeedsAccountId, account = null)

        // when
        viewModel().runTest(events = listOf(SmsReviewEvent.OnConfirm(NeedsAccountId.value.toString()))) {
            // then the item is still there, untouched
            shouldBeInstanceOf<SmsReviewState.Content>().items.single().status shouldBe
                ReviewStatusUi.NeedsAccount
        }
        coVerify(exactly = 0) { confirmCapturedTransaction.confirm(any(), any(), any(), any()) }
    }

    @Test
    fun `confirming a ready item commits it once and takes it out of the list`() {
        // given
        pending += entry(id = ReadyId, account = account.id)

        // when
        viewModel().runTest(events = listOf(SmsReviewEvent.OnConfirm(ReadyId.value.toString()))) {
            // then
            this shouldBe SmsReviewState.Empty
        }
        coVerify(exactly = 1) { confirmCapturedTransaction.confirm(ReadyId, null, null, null) }
    }

    @Test
    fun `a confirm that fails leaves the item in the list rather than losing it`() {
        // given
        pending += entry(id = ReadyId, account = account.id)
        coEvery { confirmCapturedTransaction.confirm(any(), any(), any(), any()) } returns
            ConfirmCaptureError.MissingAccount(ReadyId).left()

        // when
        viewModel().runTest(events = listOf(SmsReviewEvent.OnConfirm(ReadyId.value.toString()))) {
            // then
            shouldBeInstanceOf<SmsReviewState.Content>().items.single().id shouldBe
                ReadyId.value.toString()
        }
    }

    @Test
    fun `dismissing removes the item and it does not come back on reload`() {
        // given
        pending += entry(id = ReadyId, account = account.id)

        // when
        viewModel().runTest(
            events = listOf(
                SmsReviewEvent.OnDismiss(ReadyId.value.toString(), alsoRemoveFee = true),
            ),
        ) {
            // then
            this shouldBe SmsReviewState.Empty
        }
        coVerify(exactly = 1) { dismissCapturedTransaction.dismiss(ReadyId, alsoRemoveFee = true) }
    }

    @Test
    fun `an item the ledger may already hold is flagged, and still confirmable`() {
        // given
        pending += entry(
            id = ReadyId,
            account = account.id,
            duplicateOf = TransactionId(UUID.randomUUID()),
        )

        // when / then
        viewModel().runTest {
            val item = shouldBeInstanceOf<SmsReviewState.Content>().items.single()
            item.status.shouldBeInstanceOf<ReviewStatusUi.PossibleDuplicate>()
        }
    }

    @Test
    fun `expanding an item records which one is open`() {
        // given
        pending += entry(id = ReadyId, account = account.id)

        // when
        viewModel().runTest(
            events = listOf(SmsReviewEvent.OnItemExpanded(ReadyId.value.toString())),
        ) {
            // then
            shouldBeInstanceOf<SmsReviewState.Content>().expandedItemId shouldBe
                ReadyId.value.toString()
        }
    }

    @Test
    fun `expanding an item that is then confirmed away collapses the list`() {
        // given
        pending += entry(id = ReadyId, account = account.id)
        pending += entry(id = NeedsAccountId, account = account.id)

        // when
        viewModel().runTest(
            events = listOf(
                SmsReviewEvent.OnItemExpanded(ReadyId.value.toString()),
                SmsReviewEvent.OnConfirm(ReadyId.value.toString()),
            ),
        ) {
            // then the stale expansion does not point at a row that no longer exists
            shouldBeInstanceOf<SmsReviewState.Content>().expandedItemId shouldBe null
        }
    }

    @Test
    fun `closing the screen navigates back and commits nothing`() {
        // given
        pending += entry(id = ReadyId, account = account.id)

        // when
        viewModel().runTest(events = listOf(SmsReviewEvent.OnClose)) {
            shouldBeInstanceOf<SmsReviewState.Content>()
        }

        // then
        coVerify(exactly = 1) { navigation.back() }
        coVerify(exactly = 0) { confirmCapturedTransaction.confirm(any(), any(), any(), any()) }
    }

    @Test
    fun `money coming in is not shown as money going out`() {
        // given
        pending += entry(
            id = ReadyId,
            account = account.id,
            direction = MoneyDirection.MoneyIn,
        )

        // when / then
        viewModel().runTest {
            shouldBeInstanceOf<SmsReviewState.Content>()
                .items.single().amountFormatted shouldBe "KES 1,350.00"
        }
    }

    // --- User Story 2: transaction costs ------------------------------------------------------

    @Test
    fun `an item shows the charge that came with it, beside the amount and not inside it`() {
        // given the DTB transfer that reported `Charges 59.76 KES`
        pending += entry(id = ReadyId, account = account.id, fee = 59.76)

        // when / then
        viewModel().runTest {
            val item = shouldBeInstanceOf<SmsReviewState.Content>().items.single()
            // The payment is untouched by the charge...
            item.amountFormatted shouldBe "-KES 1,350.00"
            // ...and the charge is shown unsigned, as a cost rather than a second outflow.
            item.feeFormatted shouldBe "KES 59.76"
        }
    }

    @Test
    fun `an item with no charge shows none, rather than a zero`() {
        // given the M-PESA message reporting `Transaction cost, Ksh0.00` (Acceptance 2.3)
        pending += entry(id = ReadyId, account = account.id, fee = null)

        // when / then
        viewModel().runTest {
            shouldBeInstanceOf<SmsReviewState.Content>().items.single().feeFormatted shouldBe null
        }
    }

    @Test
    fun `the charge is still shown while the item cannot yet be confirmed`() {
        // given a fee-bearing item whose sender has no account
        pending += entry(id = NeedsAccountId, account = null, fee = 59.76)

        // when / then the cost does not disappear behind the blocking status
        viewModel().runTest {
            val item = shouldBeInstanceOf<SmsReviewState.Content>().items.single()
            item.status shouldBe ReviewStatusUi.NeedsAccount
            item.feeFormatted shouldBe "KES 59.76"
        }
    }

    @Test
    fun `confirming a fee-bearing item clears the payment and its charge together`() {
        // given
        pending += entry(id = ReadyId, account = account.id, fee = 59.76, tax = 8.25)

        // when
        viewModel().runTest(
            events = listOf(SmsReviewEvent.OnConfirm(ReadyId.value.toString())),
        ) {
            // then one confirm empties the inbox — the fee is not left behind to review
            this shouldBe SmsReviewState.Empty
        }
        coVerify(exactly = 1) {
            confirmCapturedTransaction.confirm(ReadyId, any(), any(), any())
        }
    }

    @Test
    fun `discarding a payment asks for its charge to go too`() {
        // given
        pending += entry(id = ReadyId, account = account.id, fee = 59.76)

        // when
        viewModel().runTest(
            events = listOf(
                SmsReviewEvent.OnDismiss(ReadyId.value.toString(), alsoRemoveFee = true),
            ),
        ) {
            this shouldBe SmsReviewState.Empty
        }

        // then (FR-018)
        coVerify(exactly = 1) { dismissCapturedTransaction.dismiss(ReadyId, alsoRemoveFee = true) }
    }

    @Test
    fun `the user can discard the payment and keep the charge`() {
        // given
        pending += entry(id = ReadyId, account = account.id, fee = 59.76)

        // when
        viewModel().runTest(
            events = listOf(
                SmsReviewEvent.OnDismiss(ReadyId.value.toString(), alsoRemoveFee = false),
            ),
        ) {
            this shouldBe SmsReviewState.Empty
        }

        // then the choice reaches the use case rather than being flattened on the way
        coVerify(exactly = 1) { dismissCapturedTransaction.dismiss(ReadyId, alsoRemoveFee = false) }
    }

    private fun viewModel() = SmsReviewViewModel(
        capturedTransactionRepository = capturedTransactionRepository,
        accountRepository = accountRepository,
        confirmCapturedTransaction = confirmCapturedTransaction,
        dismissCapturedTransaction = dismissCapturedTransaction,
        formatMoney = formatMoney,
        timeFormatter = FixedTimeFormatter,
        navigation = navigation,
    )

    private fun entry(
        id: CapturedTransactionId,
        account: AccountId?,
        direction: MoneyDirection = MoneyDirection.MoneyOut,
        duplicateOf: TransactionId? = null,
        fee: Double? = null,
        tax: Double? = null,
    ) = CapturedEntry(
        principal = CapturedTransaction(
            id = id,
            sender = SenderId.unsafe("MPESA"),
            kind = CapturedKind.Principal(direction),
            amount = PositiveDouble.unsafe(1_350.0),
            asset = Kes,
            time = PaidAt,
            counterparty = NotBlankTrimmedString.unsafe("James Kinyua Mwangi"),
            reference = ProviderReference.unsafe("UGP7B0ITE4"),
            account = account,
            category = null,
            description = null,
            duplicateOf = duplicateOf,
            capturedAt = PaidAt,
            origin = CaptureOrigin.LiveBroadcast,
        ),
        fee = fee?.let {
            CapturedTransaction(
                id = CapturedTransactionId(UUID.randomUUID()),
                sender = SenderId.unsafe("MPESA"),
                kind = CapturedKind.Fee(
                    parent = id,
                    tax = tax?.let(PositiveDouble::unsafe),
                ),
                amount = PositiveDouble.unsafe(it),
                asset = Kes,
                time = PaidAt,
                counterparty = NotBlankTrimmedString.unsafe("James Kinyua Mwangi"),
                reference = ProviderReference.unsafe("UGP7B0ITE4"),
                account = account,
                category = null,
                description = null,
                duplicateOf = null,
                capturedAt = PaidAt,
                origin = CaptureOrigin.LiveBroadcast,
            )
        },
    )

    /**
     * A fixed formatter rather than a mock: [TimeFormatter]'s methods are member extensions, which
     * MockK can stub only awkwardly, and the point of the assertion is the value anyway.
     */
    private object FixedTimeFormatter : TimeFormatter {
        override fun LocalDateTime.format(style: TimeFormatter.Style) = "25 Jul 2026, 19:50"
        override fun LocalTime.format() = "19:50"
        override fun Instant.formatLocal(style: TimeFormatter.Style) = "25 Jul 2026, 19:50"
        override fun Instant.formatUtc(style: TimeFormatter.Style) = "25 Jul 2026, 16:50"
    }

    private companion object {
        private val Kes = AssetCode.unsafe("KES")
        private val PaidAt: Instant = Instant.parse("2026-07-25T16:50:00Z")

        private val ReadyId =
            CapturedTransactionId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
        private val NeedsAccountId =
            CapturedTransactionId(UUID.fromString("22222222-2222-2222-2222-222222222222"))

        private val account = Account(
            id = AccountId(UUID.fromString("00000000-0000-0000-0000-0000000000a1")),
            name = NotBlankTrimmedString.unsafe("M-PESA"),
            asset = Kes,
            color = ColorInt(0),
            icon = null,
            includeInBalance = true,
            orderNum = 0.0,
        )
    }
}
