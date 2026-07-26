package com.iris.sms.review

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.iris.data.model.AccountId
import com.iris.data.model.CategoryId
import com.iris.data.model.primitive.NotBlankTrimmedString
import com.iris.data.model.primitive.PositiveDouble
import com.iris.data.model.sms.CapturedEntry
import com.iris.data.model.sms.CapturedKind
import com.iris.data.model.sms.CapturedTransaction
import com.iris.data.model.sms.CapturedTransactionId
import com.iris.data.model.sms.MoneyDirection
import com.iris.data.model.sms.ReviewStatus
import com.iris.data.model.sms.reviewStatus
import com.iris.data.repository.AccountRepository
import com.iris.data.repository.CapturedTransactionRepository
import com.iris.data.repository.CategoryRepository
import com.iris.domain.usecase.sms.ConfirmCapturedTransactionUseCase
import com.iris.domain.usecase.sms.DismissCapturedTransactionUseCase
import com.iris.domain.usecase.sms.SuggestCategoryUseCase
import com.iris.navigation.Navigation
import com.iris.ui.ComposeViewModel
import com.iris.ui.FormatMoneyUseCase
import com.iris.ui.time.TimeFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import timber.log.Timber
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * The review inbox.
 *
 * Built entirely on the Compose runtime rather than on flows: `uiState()` is a composable whose
 * state reads are the subscription, and every piece of asynchronous work — the first load, a
 * confirm, a dismiss — runs inside a `LaunchedEffect` keyed on the outstanding command. Routing
 * the work through the composition rather than through `viewModelScope` gives everything one
 * ordering, which is also what makes the whole screen drivable from a list of events in a test.
 *
 * Formatting happens here, once, on the way out. The view never sees a `PositiveDouble` or an
 * `Instant`, so a screenshot of this screen cannot change because the clock moved.
 *
 * The user's in-progress edits live in [drafts] rather than being written back to
 * `captured_transactions` keystroke by keystroke. A half-typed amount is not a fact about the
 * user's money, and persisting one would survive a crash as if it were.
 */
@Stable
@HiltViewModel
class SmsReviewViewModel @Inject constructor(
    private val capturedTransactionRepository: CapturedTransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val confirmCapturedTransaction: ConfirmCapturedTransactionUseCase,
    private val dismissCapturedTransaction: DismissCapturedTransactionUseCase,
    private val suggestCategory: SuggestCategoryUseCase,
    private val formatMoney: FormatMoneyUseCase,
    private val timeFormatter: TimeFormatter,
    private val navigation: Navigation,
) : ComposeViewModel<SmsReviewState, SmsReviewEvent>() {

    private var loading by mutableStateOf(true)
    private var items by mutableStateOf<ImmutableList<CapturedItemUi>>(persistentListOf())
    private var categories by mutableStateOf<ImmutableList<CategoryPickUi>>(persistentListOf())
    private var accounts by mutableStateOf<ImmutableList<AccountPickUi>>(persistentListOf())
    private var expandedItemId by mutableStateOf<String?>(null)
    private var drafts by mutableStateOf<Map<String, Draft>>(emptyMap())
    private var command by mutableStateOf<Command?>(null)
    private var issued = 0

    @Composable
    override fun uiState(): SmsReviewState {
        // A null command is the initial load; every later value re-keys the effect, so the work
        // and the reload that follows it happen in one place and in one order.
        LaunchedEffect(command) { execute(command) }

        return when {
            loading -> SmsReviewState.Loading
            items.isEmpty() -> SmsReviewState.Empty
            else -> SmsReviewState.Content(
                items = items,
                pendingCount = items.size,
                expandedItemId = expandedItemId,
                categories = categories,
                accounts = accounts,
            )
        }
    }

    override fun onEvent(event: SmsReviewEvent) {
        when (event) {
            is SmsReviewEvent.OnItemExpanded -> expandedItemId = event.id
            is SmsReviewEvent.OnCategorySelected -> edit(event.id) {
                // An explicit choice, even one identical to the suggestion: from here on the
                // category travels as the user's decision rather than as a proposal.
                copy(category = event.categoryId.toCategoryId(), categoryChosen = true)
            }

            is SmsReviewEvent.OnDescriptionChanged -> edit(event.id) {
                copy(description = event.description)
            }

            is SmsReviewEvent.OnAccountSelected -> edit(event.id) {
                copy(account = event.accountId.toAccountId())
            }

            is SmsReviewEvent.OnAmountEdited -> edit(event.id) { copy(amount = event.amount) }
            is SmsReviewEvent.OnCounterpartyEdited -> edit(event.id) {
                copy(counterparty = event.counterparty)
            }

            is SmsReviewEvent.OnTimeEdited -> edit(event.id) {
                copy(time = Instant.ofEpochMilli(event.epochMillis))
            }

            is SmsReviewEvent.OnKeepDespiteDuplicate -> edit(event.id) {
                copy(duplicateAcknowledged = true)
            }

            is SmsReviewEvent.OnConfirm -> issue(Command.Confirm(event.id))
            is SmsReviewEvent.OnDismiss ->
                issue(Command.Dismiss(event.id, alsoRemoveFee = event.alsoRemoveFee))

            SmsReviewEvent.OnClose -> navigation.back()
        }
    }

    /**
     * Applies an edit to the draft and re-renders the affected row immediately.
     *
     * The row is rebuilt here rather than waiting for a reload, because a reload is a database
     * round trip and the user is watching a text field they are typing into.
     */
    private fun edit(id: String, change: Draft.() -> Draft) {
        val draft = drafts[id] ?: Draft()
        drafts = drafts + (id to draft.change())
        items = items.map { if (it.id == id) it.applyDraft(drafts.getValue(id)) else it }
            .toImmutableList()
    }

    /**
     * Refuses a confirm for an item we already know has no account, so a stale click cannot file
     * money against an account nobody chose (FR-027a).
     *
     * An item we know nothing about is still passed on: `ConfirmCapturedTransactionUseCase`
     * refuses it for the same reason and holds the authoritative answer.
     */
    private fun issue(next: Command) {
        if (next is Command.Confirm && needsAccount(next.id)) return
        command = next.withSequence(++issued)
    }

    private fun needsAccount(id: String): Boolean =
        items.any { it.id == id && it.status is ReviewStatusUi.NeedsAccount }

    private suspend fun execute(current: Command?) {
        when (current) {
            null -> loadPickers()
            is Command.Confirm -> current.id.toCapturedId()?.let { id ->
                val draft = drafts[current.id] ?: Draft()
                confirmCapturedTransaction.confirm(
                    id = id,
                    account = draft.account,
                    category = draft.category,
                    description = draft.description?.let(NotBlankTrimmedString::from)
                        ?.getOrNull(),
                    amount = draft.amount?.toPositiveDouble(),
                    counterparty = draft.counterparty?.let(NotBlankTrimmedString::from)
                        ?.getOrNull(),
                    time = draft.time,
                ).onLeft { Timber.w("Confirm refused: %s", it::class.simpleName) }
            }

            is Command.Dismiss -> current.id.toCapturedId()?.let { id ->
                dismissCapturedTransaction.dismiss(id, alsoRemoveFee = current.alsoRemoveFee)
                    .onLeft { Timber.w("Dismiss refused: %s", it::class.simpleName) }
            }
        }
        reload()
    }

    /** Read once per screen: neither list changes because a message arrived. */
    private suspend fun loadPickers() {
        categories = categoryRepository.findAll()
            .map {
                CategoryPickUi(
                    id = it.id.value.toString(),
                    name = it.name.value,
                    colorArgb = it.color.value,
                )
            }
            .toImmutableList()
        accounts = accountRepository.findAll()
            .map { AccountPickUi(id = it.id.value.toString(), name = it.name.value) }
            .toImmutableList()
    }

    /**
     * Re-reads the whole pending list rather than mutating it in place, so what is on screen is
     * always what the database actually holds — including when a confirm silently failed.
     *
     * Drafts for rows that are gone are dropped with them; keeping them would let an edit made to
     * a confirmed item reappear on a later item that reused the id's position on screen.
     */
    private suspend fun reload() {
        val pending = capturedTransactionRepository.findAllPending()
        val accountNames = accountNames()
        items = pending.map { it.toUi(accountNames) }.toImmutableList()
        val live = items.map { it.id }.toSet()
        drafts = drafts.filterKeys { it in live }
        expandedItemId = expandedItemId?.takeIf { it in live }
        loading = false
    }

    private suspend fun accountNames(): Map<AccountId, String> =
        accountRepository.findAll().associate { it.id to it.name.value }

    private suspend fun CapturedEntry.toUi(
        accountNames: Map<AccountId, String>,
    ): CapturedItemUi {
        val captured = principal
        val draft = drafts[captured.id.value.toString()] ?: Draft()
        // Read before any user input, so the first thing the user sees for a payee they have
        // filed before is the category they chose last time (FR-024, Acceptance 3.2).
        val suggested = captured.category ?: suggestCategory.suggest(captured.counterparty)
        return CapturedItemUi(
            id = captured.id.value.toString(),
            amountFormatted = captured.formatAmount(),
            amountEditable = captured.amount.value.toEditable(),
            counterparty = captured.counterparty?.value.orEmpty(),
            // The message's own timestamp, never the moment it was captured (FR-014).
            timeFormatted = with(timeFormatter) {
                captured.time.formatLocal(TimeFormatter.Style.DateAndTime(includeWeekDay = false))
            },
            timeEpochMillis = captured.time.toEpochMilli(),
            reference = captured.reference?.value,
            accountName = captured.account?.let(accountNames::get),
            categoryName = suggested?.let(::categoryName),
            description = captured.description?.value.orEmpty(),
            // Rendered without a sign: the row is already labelled as a cost, and a second minus
            // next to the payment's own reads as if two amounts were being subtracted.
            feeFormatted = fee?.formatUnsigned(),
            status = captured.reviewStatus().toUi(),
        ).applyDraft(draft)
    }

    /**
     * Overlays whatever the user has changed on top of what was captured.
     *
     * Applied at the very end of mapping, so an edit outranks both the parsed value and the
     * suggestion — including an account choice, which is what turns `NeedsAccount` into a row
     * that can be confirmed (FR-027a).
     */
    private fun CapturedItemUi.applyDraft(draft: Draft): CapturedItemUi = copy(
        amountEditable = draft.amount ?: amountEditable,
        counterparty = draft.counterparty ?: counterparty,
        timeFormatted = draft.time?.let {
            with(timeFormatter) {
                it.formatLocal(TimeFormatter.Style.DateAndTime(includeWeekDay = false))
            }
        } ?: timeFormatted,
        timeEpochMillis = draft.time?.toEpochMilli() ?: timeEpochMillis,
        accountName = draft.account?.let(::accountName) ?: accountName,
        categoryName = if (draft.categoryChosen) draft.category?.let(::categoryName) else categoryName,
        description = draft.description ?: description,
        status = status.withDraft(draft, accountChosen = draft.account != null),
    )

    /**
     * A chosen account clears `NeedsAccount`; acknowledging the duplicate clears the warning.
     * Neither is written anywhere until the item is confirmed.
     */
    private fun ReviewStatusUi.withDraft(
        draft: Draft,
        accountChosen: Boolean,
    ): ReviewStatusUi = when (this) {
        ReviewStatusUi.NeedsAccount ->
            if (accountChosen) ReviewStatusUi.ReadyToConfirm else this

        is ReviewStatusUi.PossibleDuplicate ->
            if (draft.duplicateAcknowledged) ReviewStatusUi.ReadyToConfirm else this

        ReviewStatusUi.ReadyToConfirm -> this
    }

    private fun categoryName(id: CategoryId): String? =
        categories.firstOrNull { it.id == id.value.toString() }?.name

    private fun accountName(id: AccountId): String? =
        accounts.firstOrNull { it.id == id.value.toString() }?.name

    /**
     * The asset code is prefixed here rather than asked of [FormatMoneyUseCase], which formats the
     * number alone. Money out carries a minus sign so the direction is legible at a glance without
     * a second badge to look at.
     */
    private suspend fun CapturedTransaction.formatAmount(): String {
        val sign = if (kind.isMoneyOut()) "-" else ""
        return "$sign${formatUnsigned()}"
    }

    private suspend fun CapturedTransaction.formatUnsigned(): String =
        "${asset.code} ${formatMoney.format(amount.value, shortenAmount = false)}"

    private fun CapturedKind.isMoneyOut(): Boolean = when (this) {
        is CapturedKind.Principal -> direction == MoneyDirection.MoneyOut
        is CapturedKind.Fee -> true
    }

    private fun ReviewStatus.toUi(): ReviewStatusUi = when (this) {
        ReviewStatus.NeedsAccount -> ReviewStatusUi.NeedsAccount
        is ReviewStatus.PossibleDuplicate -> ReviewStatusUi.PossibleDuplicate(DUPLICATE_SUMMARY)
        ReviewStatus.ReadyToConfirm -> ReviewStatusUi.ReadyToConfirm
    }

    /** A malformed id can only come from a caller we do not control; ignored, never thrown on. */
    private fun String.toCapturedId(): CapturedTransactionId? =
        runCatching { CapturedTransactionId(UUID.fromString(this)) }.getOrNull()

    private fun String.toCategoryId(): CategoryId? =
        runCatching { CategoryId(UUID.fromString(this)) }.getOrNull()

    private fun String.toAccountId(): AccountId? =
        runCatching { AccountId(UUID.fromString(this)) }.getOrNull()

    /**
     * A field the user is still typing into is routinely not a number yet. Null means "no
     * correction I can act on", which leaves the captured amount in place rather than refusing
     * the confirm outright.
     *
     * Only a comma **between two digits** is treated as a thousands separator. Stripping every
     * comma would read the half-typed `"1,"` as `1.0` and quietly commit a payment of one
     * shilling instead of the fifteen hundred the user was in the middle of typing.
     */
    private fun String.toPositiveDouble(): PositiveDouble? = trim()
        .replace(THOUSANDS_SEPARATOR, "")
        .toDoubleOrNull()
        ?.let { PositiveDouble.from(it).getOrNull() }

    /** `1350.0` rather than `1,350.00`: the editor wants a number back, not a rendering. */
    private fun Double.toEditable(): String =
        if (this % 1.0 == 0.0) toLong().toString() else toString()

    /**
     * One user's in-progress corrections to one row.
     *
     * [categoryChosen] is tracked separately from a non-null [category] so that clearing a
     * suggestion is representable: without it, "the user removed the suggested category" and "the
     * user has not touched the category" are the same value.
     */
    @Immutable
    private data class Draft(
        val category: CategoryId? = null,
        val categoryChosen: Boolean = false,
        val account: AccountId? = null,
        val description: String? = null,
        val amount: String? = null,
        val counterparty: String? = null,
        val time: Instant? = null,
        val duplicateAcknowledged: Boolean = false,
    )

    /**
     * One unit of asynchronous work.
     *
     * The sequence number exists so that confirming, failing, and confirming the same item again
     * produces a different value and therefore re-runs the effect. Without it the second attempt
     * would look identical to the first and silently do nothing.
     */
    @Immutable
    private sealed interface Command {
        val id: String
        val sequence: Int

        fun withSequence(value: Int): Command

        data class Confirm(
            override val id: String,
            override val sequence: Int = 0,
        ) : Command {
            override fun withSequence(value: Int) = copy(sequence = value)
        }

        data class Dismiss(
            override val id: String,
            val alsoRemoveFee: Boolean,
            override val sequence: Int = 0,
        ) : Command {
            override fun withSequence(value: Int) = copy(sequence = value)
        }
    }

    private companion object {
        /** A comma with a digit on each side. Anything else is not a thousands separator. */
        private val THOUSANDS_SEPARATOR = Regex("(?<=\\d),(?=\\d)")

        /**
         * Deliberately not a rendering of the matched transaction: showing the amount and date of
         * something the user may not remember entering invites them to trust the match. This is a
         * prompt to look, not a verdict (FR-029).
         */
        private const val DUPLICATE_SUMMARY = "A similar transaction is already recorded"
    }
}
