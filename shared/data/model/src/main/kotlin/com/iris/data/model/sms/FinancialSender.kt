package com.iris.data.model.sms

import com.iris.data.model.AccountId
import com.iris.data.model.primitive.NotBlankTrimmedString

/**
 * A message sender the user has identified as a source of transaction confirmations.
 * Only messages whose sender matches a [FinancialSender] are ever inspected (FR-004).
 *
 * Deliberately not `Identifiable<SenderId>`: `Identifiable` constrains its id to `UniqueId`,
 * which is UUID-backed, whereas a sender is keyed by its normalised originating address.
 */
data class FinancialSender(
    val id: SenderId,
    val displayName: NotBlankTrimmedString,
    /** Which parser rule set handles this sender; null => sender known but unparseable yet. */
    val ruleSet: RuleSetId?,
    /** null => captured items from this sender need an account chosen at review time (FR-027a). */
    val account: AccountId?,
    /** Per-sender kill switch (FR-003). */
    val enabled: Boolean,
)
