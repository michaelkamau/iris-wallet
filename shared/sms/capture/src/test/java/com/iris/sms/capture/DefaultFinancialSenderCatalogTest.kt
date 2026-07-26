package com.iris.sms.capture

import com.iris.data.model.primitive.NotBlankTrimmedString
import com.iris.data.model.sms.FinancialSender
import com.iris.data.model.sms.RuleSetId
import com.iris.data.model.sms.SenderId
import com.iris.data.repository.FinancialSenderRepository
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Seeding is the only reason a fresh install matches anything at all, and it is also the easiest
 * place to quietly undo a user's settings. Both halves are pinned here.
 */
class DefaultFinancialSenderCatalogTest {

    private val senderRepository = mockk<FinancialSenderRepository>(relaxUnitFun = true)
    private val catalog = DefaultFinancialSenderCatalog(senderRepository)

    @Test
    fun `seeds every sender the parser has rules for when none are enrolled`() = runTest {
        // given
        coEvery { senderRepository.findAll() } returns emptyList()

        // when
        catalog.seed()

        // then
        val saved = mutableListOf<FinancialSender>()
        coVerify(exactly = 3) { senderRepository.save(capture(saved)) }
        saved.map { it.id.value }
            .shouldContainExactlyInAnyOrder("MPESA", "DTB-KENYA", "KCB")
    }

    @Test
    fun `seeded senders carry a rule set but never an account`() = runTest {
        // given
        coEvery { senderRepository.findAll() } returns emptyList()

        // when
        catalog.seed()

        // then an account guessed here would be a silent mis-filing (FR-027a)
        val saved = mutableListOf<FinancialSender>()
        coVerify { senderRepository.save(capture(saved)) }
        saved.all { it.account == null } shouldBe true
        saved.all { it.ruleSet != null } shouldBe true
        saved.all { it.enabled } shouldBe true
    }

    @Test
    fun `a sender the user already changed is left exactly as they left it`() = runTest {
        // given the user switched M-PESA off and renamed it
        val userOwned = FinancialSender(
            id = SenderId.unsafe("MPESA"),
            displayName = NotBlankTrimmedString.unsafe("My Safaricom line"),
            ruleSet = RuleSetId.unsafe("mpesa"),
            account = null,
            enabled = false,
        )
        coEvery { senderRepository.findAll() } returns listOf(userOwned)

        // when
        catalog.seed()

        // then
        coVerify(exactly = 0) { senderRepository.save(userOwned) }
        val saved = mutableListOf<FinancialSender>()
        coVerify(exactly = 2) { senderRepository.save(capture(saved)) }
        saved.map { it.id.value }.shouldContainExactlyInAnyOrder("DTB-KENYA", "KCB")
    }

    @Test
    fun `seeding twice is the same as seeding once`() = runTest {
        // given everything is already enrolled
        coEvery { senderRepository.findAll() } returns listOf(
            sender("MPESA", "mpesa"),
            sender("DTB-KENYA", "dtb"),
            sender("KCB", "kcb"),
        )

        // when
        catalog.seed()

        // then
        coVerify(exactly = 0) { senderRepository.save(any()) }
    }

    private fun sender(id: String, ruleSet: String) = FinancialSender(
        id = SenderId.unsafe(id),
        displayName = NotBlankTrimmedString.unsafe(id),
        ruleSet = RuleSetId.unsafe(ruleSet),
        account = null,
        enabled = true,
    )
}
