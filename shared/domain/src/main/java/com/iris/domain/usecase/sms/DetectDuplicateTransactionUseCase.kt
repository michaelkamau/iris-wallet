package com.iris.domain.usecase.sms

import com.iris.data.model.AccountId
import com.iris.data.model.Transaction
import com.iris.data.model.TransactionId
import com.iris.data.model.getFromAccount
import com.iris.data.model.getFromValue
import com.iris.data.model.primitive.AssetCode
import com.iris.data.model.primitive.PositiveDouble
import com.iris.data.repository.TransactionRepository
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Answers "does the ledger already contain this same event?" for a message about to be captured
 * (FR-029).
 *
 * It only ever *flags*. A message and a manual entry that look identical are still two facts about
 * which only the user knows the truth, so the answer is surfaced as
 * [com.iris.data.model.sms.ReviewStatus.PossibleDuplicate] and never acted on automatically.
 *
 * "Same day" is deliberately the same **Nairobi calendar day**, not a rolling 24 hours: a message
 * timestamped 23:50 and a manual entry made the next morning are different days to the person who
 * wrote them down, whatever the instant arithmetic says.
 */
@Singleton
class DetectDuplicateTransactionUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
) {

    suspend fun detect(
        account: AccountId,
        amount: PositiveDouble,
        asset: AssetCode,
        time: Instant,
    ): TransactionId? {
        val day = time.atZone(NAIROBI).toLocalDate()
        val from = day.atStartOfDay(NAIROBI).toInstant()
        val to = from.plus(ONE_DAY)

        return transactionRepository
            .findAllByAccountAndBetween(accountId = account, startDate = from, endDate = to)
            .firstOrNull { it.matches(account, amount, asset, day) }
            ?.id
    }

    /**
     * A transfer is compared on the side money left, which is the side an SMS about an outgoing
     * payment would have produced.
     */
    private fun Transaction.matches(
        account: AccountId,
        amount: PositiveDouble,
        asset: AssetCode,
        day: LocalDate,
    ): Boolean {
        val value = getFromValue()
        return getFromAccount() == account &&
            value.asset == asset &&
            value.amount.value == amount.value &&
            time.atZone(NAIROBI).toLocalDate() == day
    }

    private companion object {
        /** Every supported provider reports local time; the window must be local too. */
        private val NAIROBI: ZoneId = ZoneId.of("Africa/Nairobi")
        private val ONE_DAY: Duration = Duration.ofDays(1)
    }
}
