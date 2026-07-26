package com.iris.data.repository.mapper

import arrow.core.Either
import arrow.core.raise.either
import com.iris.data.db.entity.FinancialSenderEntity
import com.iris.data.model.AccountId
import com.iris.data.model.primitive.NotBlankTrimmedString
import com.iris.data.model.sms.FinancialSender
import com.iris.data.model.sms.RuleSetId
import com.iris.data.model.sms.SenderId
import javax.inject.Inject

class FinancialSenderMapper @Inject constructor() {

    /**
     * An unrecognised `ruleSetId` degrades to `null` rather than failing the row: the sender is
     * still the user's choice and still owns an account mapping, it simply has no parser yet.
     * Dropping the row instead would silently un-enrol a sender on a rules change.
     */
    fun FinancialSenderEntity.toDomain(): Either<String, FinancialSender> = either {
        FinancialSender(
            id = SenderId.from(senderId).bind(),
            displayName = NotBlankTrimmedString.from(displayName).bind(),
            ruleSet = ruleSetId?.let(RuleSetId::from)?.getOrNull(),
            account = accountId?.let(::AccountId),
            enabled = enabled,
        )
    }

    fun FinancialSender.toEntity(): FinancialSenderEntity = FinancialSenderEntity(
        displayName = displayName.value,
        ruleSetId = ruleSet?.value,
        accountId = account?.value,
        enabled = enabled,
        senderId = id.value,
    )
}
