package com.iris.sms.review

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.iris.data.model.AccountId
import com.iris.data.model.sms.CapturedEntry
import com.iris.data.model.sms.CapturedKind
import com.iris.data.model.sms.CapturedTransaction
import com.iris.data.model.sms.CapturedTransactionId
import com.iris.data.model.sms.MoneyDirection
import com.iris.data.model.sms.ReviewStatus
import com.iris.data.model.sms.reviewStatus
import com.iris.data.repository.AccountRepository
import com.iris.data.repository.CapturedTransactionRepository
import com.iris.domain.usecase.sms.ConfirmCapturedTransactionUseCase
import com.iris.domain.usecase.sms.DismissCapturedTransactionUseCase
import com.iris.navigation.Navigation
import com.iris.ui.ComposeViewModel
import com.iris.ui.FormatMoneyUseCase
import com.iris.ui.time.TimeFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import timber.log.Timber
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
 */
@Stable
@HiltViewModel
class SmsReviewViewModel @Inject constructor(
    private val capturedTransactionRepository: CapturedTransactionRepository,
    private val accountRepository: AccountRepository,
    private val confirmCapturedTransaction: ConfirmCapturedTransactionUseCase,
    private val dismissCapturedTransaction: DismissCapturedTransactionUseCase,
    private val formatMoney: FormatMoneyUseCase,
    private val timeFormatter: TimeFormatter,
    private val navigation: Navigation,
) : ComposeViewModel<SmsReviewState, SmsReviewEvent>() {

    private var loading by mutableStateOf(true)
    private var items by mutableStateOf<ImmutableList<CapturedItemUi>>(persistentListOf())
    private var expandedItemId by mutableStateOf<String?>(null)
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
            )
        }
    }

    override fun onEvent(event: SmsReviewEvent) {
        when (event) {
            is SmsReviewEvent.OnItemExpanded -> expandedItemId = event.id
            is SmsReviewEvent.OnConfirm -> issue(Command.Confirm(event.id))
            is SmsReviewEvent.OnDismiss ->
                issue(Command.Dismiss(event.id, alsoRemoveFee = event.alsoRemoveFee))
            SmsReviewEvent.OnClose -> navigation.back()
        }
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
            null -> Unit
            is Command.Confirm -> current.id.toCapturedId()?.let { id ->
                confirmCapturedTransaction.confirm(id)
                    .onLeft { Timber.w("Confirm refused: %s", it::class.simpleName) }
            }

            is Command.Dismiss -> current.id.toCapturedId()?.let { id ->
                dismissCapturedTransaction.dismiss(id, alsoRemoveFee = current.alsoRemoveFee)
                    .onLeft { Timber.w("Dismiss refused: %s", it::class.simpleName) }
            }
        }
        reload()
    }

    /**
     * Re-reads the whole pending list rather than mutating it in place, so what is on screen is
     * always what the database actually holds — including when a confirm silently failed.
     */
    private suspend fun reload() {
        val pending = capturedTransactionRepository.findAllPending()
        val accountNames = accountNames(pending)
        items = pending.map { it.toUi(accountNames) }.toImmutableList()
        expandedItemId = expandedItemId?.takeIf { open -> items.any { it.id == open } }
        loading = false
    }

    private suspend fun accountNames(pending: List<CapturedEntry>): Map<AccountId, String> {
        if (pending.none { it.principal.account != null }) return emptyMap()
        return accountRepository.findAll().associate { it.id to it.name.value }
    }

    private suspend fun CapturedEntry.toUi(accountNames: Map<AccountId, String>): CapturedItemUi {
        val captured = principal
        return CapturedItemUi(
            id = captured.id.value.toString(),
            amountFormatted = captured.formatAmount(),
            counterparty = captured.counterparty?.value.orEmpty(),
            // The message's own timestamp, never the moment it was captured (FR-014).
            timeFormatted = with(timeFormatter) {
                captured.time.formatLocal(TimeFormatter.Style.DateAndTime(includeWeekDay = false))
            },
            reference = captured.reference?.value,
            accountName = captured.account?.let(accountNames::get),
            categoryName = null,
            description = captured.description?.value.orEmpty(),
            // Rendered without a sign: the row is already labelled as a cost, and a second minus
            // next to the payment's own reads as if two amounts were being subtracted.
            feeFormatted = fee?.formatUnsigned(),
            status = captured.reviewStatus().toUi(),
        )
    }

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
        /**
         * Deliberately not a rendering of the matched transaction: showing the amount and date of
         * something the user may not remember entering invites them to trust the match. This is a
         * prompt to look, not a verdict (FR-029).
         */
        private const val DUPLICATE_SUMMARY = "A similar transaction is already recorded"
    }
}
