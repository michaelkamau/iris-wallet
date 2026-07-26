package com.iris.data.repository.mapper

import com.iris.data.db.entity.FinancialSenderEntity
import com.iris.data.model.AccountId
import com.iris.data.model.primitive.NotBlankTrimmedString
import com.iris.data.model.sms.FinancialSender
import com.iris.data.model.sms.RuleSetId
import com.iris.data.model.sms.SenderId
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import org.junit.Before
import org.junit.Test
import java.util.UUID

class FinancialSenderMapperTest {
    private lateinit var mapper: FinancialSenderMapper

    @Before
    fun setup() {
        mapper = FinancialSenderMapper()
    }

    @Test
    fun `round-trips an enabled sender with every field set`() {
        // given
        val sender = sender()

        // when
        val res = with(mapper) { sender.toEntity().toDomain() }

        // then
        res.shouldBeRight() shouldBe sender
    }

    @Test
    fun `round-trips a disabled sender with no rule set and no account`() {
        // given
        val sender = sender(ruleSet = null, account = null, enabled = false)

        // when
        val res = with(mapper) { sender.toEntity().toDomain() }

        // then
        res.shouldBeRight() shouldBe sender
    }

    @Test
    fun `writes the sender to its flat form`() {
        // when
        val res = with(mapper) { sender().toEntity() }

        // then
        res shouldBe ValidRow
    }

    @Test
    fun `keeps the sender but drops an unrecognised rule set to null`() {
        // given
        val corrupted = ValidRow.copy(ruleSetId = "NOT A SLUG")

        // when
        val res = with(mapper) { corrupted.toDomain() }

        // then
        res.shouldBeRight() shouldBe sender(ruleSet = null)
    }

    @Test
    fun `rejects a row with a blank sender id`() {
        // given
        val corrupted = ValidRow.copy(senderId = "   ")

        // when
        val res = with(mapper) { corrupted.toDomain() }

        // then
        res.shouldBeLeft() shouldBe "SenderId error: NotBlankTrimmedString error: Cannot be blank"
    }

    @Test
    fun `rejects a row with a blank display name`() {
        // given
        val corrupted = ValidRow.copy(displayName = "")

        // when
        val res = with(mapper) { corrupted.toDomain() }

        // then
        res.shouldBeLeft() shouldBe "NotBlankTrimmedString error: Cannot be blank"
    }

    companion object {
        private val ACCOUNT_ID = AccountId(UUID.randomUUID())

        private fun sender(
            ruleSet: RuleSetId? = RuleSetId.unsafe("mpesa"),
            account: AccountId? = ACCOUNT_ID,
            enabled: Boolean = true,
        ) = FinancialSender(
            id = SenderId.unsafe("MPESA"),
            displayName = NotBlankTrimmedString.unsafe("M-PESA"),
            ruleSet = ruleSet,
            account = account,
            enabled = enabled,
        )

        private val ValidRow = FinancialSenderEntity(
            displayName = "M-PESA",
            ruleSetId = "mpesa",
            accountId = ACCOUNT_ID.value,
            enabled = true,
            senderId = "MPESA",
        )
    }
}
