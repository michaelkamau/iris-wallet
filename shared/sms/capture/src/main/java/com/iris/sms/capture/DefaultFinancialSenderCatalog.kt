package com.iris.sms.capture

import com.iris.data.model.primitive.NotBlankTrimmedString
import com.iris.data.model.sms.FinancialSender
import com.iris.data.model.sms.RuleSetId
import com.iris.data.model.sms.SenderId
import com.iris.data.repository.FinancialSenderRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The senders the app already knows how to read, seeded the first time capture is switched on.
 *
 * Without this, a build that ships only User Story 1 would match nothing at all: the parser has
 * rules for M-PESA, DTB and KCB, but `financial_senders` starts empty and the mapping UI that
 * would fill it does not arrive until User Story 4.
 *
 * Every seeded sender has **no account**. That is deliberate rather than a shortcut: an account
 * guessed here would be a silent mis-filing, so each captured item instead surfaces as
 * [com.iris.data.model.sms.ReviewStatus.NeedsAccount] and waits to be told where it belongs
 * (FR-027a).
 *
 * Seeding never overwrites. A sender the user has already switched off, renamed or mapped to an
 * account stays exactly as they left it.
 */
@Singleton
class DefaultFinancialSenderCatalog @Inject constructor(
    private val senderRepository: FinancialSenderRepository,
) {

    suspend fun seed() {
        val known = senderRepository.findAll().map { it.id }.toSet()
        defaults()
            .filterNot { it.id in known }
            .forEach { senderRepository.save(it) }
    }

    private fun defaults(): List<FinancialSender> = listOf(
        sender(id = "MPESA", displayName = "M-PESA", ruleSet = "mpesa"),
        sender(id = "DTB-KENYA", displayName = "Diamond Trust Bank", ruleSet = "dtb"),
        sender(id = "KCB", displayName = "KCB Bank", ruleSet = "kcb"),
    )

    private fun sender(id: String, displayName: String, ruleSet: String) = FinancialSender(
        id = SenderId.unsafe(id),
        displayName = NotBlankTrimmedString.unsafe(displayName),
        ruleSet = RuleSetId.unsafe(ruleSet),
        account = null,
        enabled = true,
    )
}
