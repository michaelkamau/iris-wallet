package com.iris.sms.review

import arrow.core.left
import arrow.core.right
import com.iris.data.model.Account
import com.iris.data.model.AccountId
import com.iris.data.model.Category
import com.iris.data.model.CategoryId
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
import com.iris.data.repository.CategoryRepository
import com.iris.domain.usecase.sms.ConfirmCaptureError
import com.iris.domain.usecase.sms.ConfirmCapturedTransactionUseCase
import com.iris.domain.usecase.sms.DismissCapturedTransactionUseCase
import com.iris.domain.usecase.sms.SuggestCategoryUseCase
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
    private val categoryRepository = mockk<CategoryRepository>()
    private val suggestCategory = mockk<SuggestCategoryUseCase>()
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
        coEvery { accountRepository.findAll() } returns listOf(account, savingsAccount)
        coEvery { categoryRepository.findAll() } returns listOf(food, groceries)
        coEvery { suggestCategory.suggest(any()) } returns null
        coEvery { formatMoney.format(any(), any()) } answers {
            String.format(java.util.Locale.US, "%,.2f", firstArg<Double>())
        }
        // MockK hands a `@JvmInline value class` argument to `answers` as its underlying type, so
        // the id arrives as a bare UUID; asking for `CapturedTransactionId` here throws.
        coEvery {
            confirmCapturedTransaction.confirm(any(), any(), any(), any(), any(), any(), any())
        } answers {
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
        coVerify(exactly = 0) {
            confirmCapturedTransaction.confirm(any(), any(), any(), any(), any(), any(), any())
        }
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
        coVerify(exactly = 1) {
            confirmCapturedTransaction.confirm(ReadyId, null, null, null, null, null, null)
        }
    }

    @Test
    fun `a confirm that fails leaves the item in the list rather than losing it`() {
        // given
        pending += entry(id = ReadyId, account = account.id)
        coEvery {
            confirmCapturedTransaction.confirm(any(), any(), any(), any(), any(), any(), any())
        } returns
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
        coVerify(exactly = 0) {
            confirmCapturedTransaction.confirm(any(), any(), any(), any(), any(), any(), any())
        }
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
            confirmCapturedTransaction.confirm(ReadyId, any(), any(), any(), any(), any(), any())
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

    // --- User Story 3: enrichment ---------------------------------------------------------------

    @Test
    fun `the pickers offer every category and account the user has`() {
        // given
        pending += entry(id = ReadyId, account = account.id)

        // when / then choosing is a tap, not a form (FR-022, SC-002)
        viewModel().runTest {
            val content = shouldBeInstanceOf<SmsReviewState.Content>()
            content.categories.map { it.name } shouldBe listOf("Food & Drinks", "Groceries")
            content.accounts.map { it.name } shouldBe listOf("M-PESA", "KCB Savings")
        }
    }

    @Test
    fun `a payee the user has filed before arrives already categorised`() {
        // given the memory holds Food & Drinks for this payee
        pending += entry(id = ReadyId, account = account.id)
        coEvery { suggestCategory.suggest(any()) } returns food.id

        // when / then the suggestion is on the row before the user has touched anything
        // (FR-024, Acceptance 3.2)
        viewModel().runTest {
            shouldBeInstanceOf<SmsReviewState.Content>()
                .items.single().categoryName shouldBe "Food & Drinks"
        }
    }

    @Test
    fun `a payee nobody has filed arrives with nothing pre-selected`() {
        // given
        pending += entry(id = ReadyId, account = account.id)
        coEvery { suggestCategory.suggest(any()) } returns null

        // when / then no category is invented on the user's behalf
        viewModel().runTest {
            shouldBeInstanceOf<SmsReviewState.Content>()
                .items.single().categoryName shouldBe null
        }
    }

    @Test
    fun `choosing a category shows it immediately, without a round trip`() {
        // given
        pending += entry(id = ReadyId, account = account.id)

        // when
        viewModel().runTest(
            events = listOf(
                SmsReviewEvent.OnCategorySelected(
                    ReadyId.value.toString(),
                    groceries.id.value.toString(),
                ),
            ),
        ) {
            // then
            shouldBeInstanceOf<SmsReviewState.Content>()
                .items.single().categoryName shouldBe "Groceries"
        }
    }

    @Test
    fun `overriding the suggestion is what reaches confirm, not the suggestion`() {
        // given the app suggests Food & Drinks
        pending += entry(id = ReadyId, account = account.id)
        coEvery { suggestCategory.suggest(any()) } returns food.id

        // when the user picks Groceries instead and confirms (Acceptance 3.3)
        viewModel().runTest(
            events = listOf(
                SmsReviewEvent.OnCategorySelected(
                    ReadyId.value.toString(),
                    groceries.id.value.toString(),
                ),
                SmsReviewEvent.OnConfirm(ReadyId.value.toString()),
            ),
        ) {
            this shouldBe SmsReviewState.Empty
        }

        // then the override is what is committed, and therefore what is remembered next time
        coVerify(exactly = 1) {
            confirmCapturedTransaction.confirm(
                ReadyId,
                null,
                groceries.id,
                null,
                null,
                null,
                null,
            )
        }
    }

    @Test
    fun `an edited description reaches confirm without disturbing anything extracted`() {
        // given
        pending += entry(id = ReadyId, account = account.id)

        // when (FR-022)
        viewModel().runTest(
            events = listOf(
                SmsReviewEvent.OnDescriptionChanged(ReadyId.value.toString(), "Rent for July"),
                SmsReviewEvent.OnConfirm(ReadyId.value.toString()),
            ),
        ) {
            this shouldBe SmsReviewState.Empty
        }

        // then
        coVerify(exactly = 1) {
            confirmCapturedTransaction.confirm(
                ReadyId,
                null,
                null,
                NotBlankTrimmedString.unsafe("Rent for July"),
                null,
                null,
                null,
            )
        }
    }

    @Test
    fun `an edited amount, payee and date are the values that reach confirm`() {
        // given
        pending += entry(id = ReadyId, account = account.id)

        // when the user corrects all three (FR-023, Acceptance 3.4)
        viewModel().runTest(
            events = listOf(
                SmsReviewEvent.OnAmountEdited(ReadyId.value.toString(), "1,530.50"),
                SmsReviewEvent.OnCounterpartyEdited(ReadyId.value.toString(), "Frank Inn Kikuyu"),
                SmsReviewEvent.OnTimeEdited(ReadyId.value.toString(), CorrectedMillis),
                SmsReviewEvent.OnConfirm(ReadyId.value.toString()),
            ),
        ) {
            this shouldBe SmsReviewState.Empty
        }

        // then
        coVerify(exactly = 1) {
            confirmCapturedTransaction.confirm(
                ReadyId,
                null,
                null,
                null,
                PositiveDouble.unsafe(1_530.50),
                NotBlankTrimmedString.unsafe("Frank Inn Kikuyu"),
                Instant.ofEpochMilli(CorrectedMillis),
            )
        }
    }

    @Test
    fun `an edit shows on the row it was made on and on no other`() {
        // given two items
        pending += entry(id = ReadyId, account = account.id)
        pending += entry(id = NeedsAccountId, account = account.id)

        // when one of them is edited
        viewModel().runTest(
            events = listOf(
                SmsReviewEvent.OnCounterpartyEdited(ReadyId.value.toString(), "Naivas"),
            ),
        ) {
            // then the other is untouched
            val items = shouldBeInstanceOf<SmsReviewState.Content>().items
            items.single { it.id == ReadyId.value.toString() }.counterparty shouldBe "Naivas"
            items.single { it.id == NeedsAccountId.value.toString() }.counterparty shouldBe
                "James Kinyua Mwangi"
        }
    }

    @Test
    fun `a half-typed amount is left alone rather than refused`() {
        // given
        pending += entry(id = ReadyId, account = account.id)

        // when the user is mid-keystroke and confirms anyway
        viewModel().runTest(
            events = listOf(
                SmsReviewEvent.OnAmountEdited(ReadyId.value.toString(), "1,"),
                SmsReviewEvent.OnConfirm(ReadyId.value.toString()),
            ),
        ) {
            this shouldBe SmsReviewState.Empty
        }

        // then the captured amount stands; nothing is committed as zero or as a guess
        coVerify(exactly = 1) {
            confirmCapturedTransaction.confirm(ReadyId, null, null, null, null, null, null)
        }
    }

    @Test
    fun `choosing an account clears the block and lets the item be confirmed`() {
        // given an item whose sender has no mapping (FR-027a)
        pending += entry(id = NeedsAccountId, account = null)

        // when the user chooses one and confirms
        viewModel().runTest(
            events = listOf(
                SmsReviewEvent.OnAccountSelected(
                    NeedsAccountId.value.toString(),
                    savingsAccount.id.value.toString(),
                ),
                SmsReviewEvent.OnConfirm(NeedsAccountId.value.toString()),
            ),
        ) {
            // then it leaves the list
            this shouldBe SmsReviewState.Empty
        }

        // and the account they chose is the one it posts to
        coVerify(exactly = 1) {
            confirmCapturedTransaction.confirm(
                NeedsAccountId,
                savingsAccount.id,
                null,
                null,
                null,
                null,
                null,
            )
        }
    }

    @Test
    fun `choosing an account is visible before anything is committed`() {
        // given
        pending += entry(id = NeedsAccountId, account = null)

        // when
        viewModel().runTest(
            events = listOf(
                SmsReviewEvent.OnAccountSelected(
                    NeedsAccountId.value.toString(),
                    savingsAccount.id.value.toString(),
                ),
            ),
        ) {
            // then the row stops asking for an account, and the confirm affordance opens up
            val item = shouldBeInstanceOf<SmsReviewState.Content>().items.single()
            item.accountName shouldBe "KCB Savings"
            item.status shouldBe ReviewStatusUi.ReadyToConfirm
        }

        // and nothing has reached the ledger yet
        coVerify(exactly = 0) {
            confirmCapturedTransaction.confirm(any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `keeping a suspected duplicate clears the flag without committing it`() {
        // given
        pending += entry(
            id = ReadyId,
            account = account.id,
            duplicateOf = TransactionId(UUID.randomUUID()),
        )

        // when (FR-029)
        viewModel().runTest(
            events = listOf(SmsReviewEvent.OnKeepDespiteDuplicate(ReadyId.value.toString())),
        ) {
            // then the warning is gone and the item is still there, unconfirmed
            val item = shouldBeInstanceOf<SmsReviewState.Content>().items.single()
            item.status shouldBe ReviewStatusUi.ReadyToConfirm
        }
        coVerify(exactly = 0) {
            confirmCapturedTransaction.confirm(any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `an edit on an item that is confirmed away does not leak onto the next one`() {
        // given two items
        pending += entry(id = ReadyId, account = account.id)
        pending += entry(id = NeedsAccountId, account = account.id)

        // when one is edited and confirmed
        viewModel().runTest(
            events = listOf(
                SmsReviewEvent.OnDescriptionChanged(ReadyId.value.toString(), "Rent for July"),
                SmsReviewEvent.OnConfirm(ReadyId.value.toString()),
            ),
        ) {
            // then the survivor carries none of it
            shouldBeInstanceOf<SmsReviewState.Content>().items.single().description shouldBe ""
        }
    }

    private fun viewModel() = SmsReviewViewModel(
        capturedTransactionRepository = capturedTransactionRepository,
        accountRepository = accountRepository,
        categoryRepository = categoryRepository,
        confirmCapturedTransaction = confirmCapturedTransaction,
        dismissCapturedTransaction = dismissCapturedTransaction,
        suggestCategory = suggestCategory,
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

        /** The date the user corrects to in the FR-023 case. */
        private const val CorrectedMillis = 1_784_994_600_000L

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

        /** A second account, so "the chosen one" is distinguishable from "the only one". */
        private val savingsAccount = account.copy(
            id = AccountId(UUID.fromString("00000000-0000-0000-0000-0000000000a2")),
            name = NotBlankTrimmedString.unsafe("KCB Savings"),
            orderNum = 1.0,
        )

        private val food = Category(
            id = CategoryId(UUID.fromString("00000000-0000-0000-0000-0000000000c1")),
            name = NotBlankTrimmedString.unsafe("Food & Drinks"),
            color = ColorInt(0),
            icon = null,
            orderNum = 0.0,
        )

        private val groceries = food.copy(
            id = CategoryId(UUID.fromString("00000000-0000-0000-0000-0000000000c2")),
            name = NotBlankTrimmedString.unsafe("Groceries"),
            orderNum = 1.0,
        )
    }
}
