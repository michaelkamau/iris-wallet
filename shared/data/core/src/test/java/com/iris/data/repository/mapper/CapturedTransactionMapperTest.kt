package com.iris.data.repository.mapper

import com.iris.data.db.entity.CapturedTransactionEntity
import com.iris.data.model.AccountId
import com.iris.data.model.CategoryId
import com.iris.data.model.TransactionId
import com.iris.data.model.primitive.AssetCode
import com.iris.data.model.primitive.NotBlankTrimmedString
import com.iris.data.model.primitive.PositiveDouble
import com.iris.data.model.sms.CaptureOrigin
import com.iris.data.model.sms.CapturedKind
import com.iris.data.model.sms.CapturedTransaction
import com.iris.data.model.sms.CapturedTransactionId
import com.iris.data.model.sms.MoneyDirection
import com.iris.data.model.sms.ProviderReference
import com.iris.data.model.sms.SenderId
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.UUID

class CapturedTransactionMapperTest {
    private lateinit var mapper: CapturedTransactionMapper

    @Before
    fun setup() {
        mapper = CapturedTransactionMapper()
    }

    @Test
    fun `round-trips a money-out principal with every field set`() {
        // given
        val principal = captured(kind = CapturedKind.Principal(MoneyDirection.MoneyOut))

        // when
        val res = with(mapper) { principal.toEntity().toDomain() }

        // then
        res.shouldBeRight() shouldBe principal
    }

    @Test
    fun `round-trips a money-in principal`() {
        // given
        val principal = captured(kind = CapturedKind.Principal(MoneyDirection.MoneyIn))

        // when
        val res = with(mapper) { principal.toEntity().toDomain() }

        // then
        res.shouldBeRight() shouldBe principal
    }

    @Test
    fun `round-trips a fee that has a parent and a tax component`() {
        // given
        val fee = captured(
            id = FEE_ID,
            kind = CapturedKind.Fee(parent = PRINCIPAL_ID, tax = PositiveDouble.unsafe(3.0)),
        )

        // when
        val res = with(mapper) { fee.toEntity().toDomain() }

        // then
        res.shouldBeRight() shouldBe fee
    }

    @Test
    fun `round-trips a standalone fee with no tax component`() {
        // given
        val fee = captured(id = FEE_ID, kind = CapturedKind.Fee(parent = null, tax = null))

        // when
        val res = with(mapper) { fee.toEntity().toDomain() }

        // then
        res.shouldBeRight() shouldBe fee
    }

    @Test
    fun `round-trips a historically imported row whose optional fields are all null`() {
        // given
        val principal = captured(
            counterparty = null,
            reference = null,
            account = null,
            category = null,
            description = null,
            duplicateOf = null,
            origin = CaptureOrigin.HistoricalImport,
        )

        // when
        val res = with(mapper) { principal.toEntity().toDomain() }

        // then
        res.shouldBeRight() shouldBe principal
    }

    @Test
    fun `writes the kind, direction and origin as the literal column values`() {
        // given
        val principal = captured(kind = CapturedKind.Principal(MoneyDirection.MoneyIn))

        // when
        val res = with(mapper) { principal.toEntity() }

        // then
        res shouldBe PrincipalRow.copy(direction = "MONEY_IN")
    }

    @Test
    fun `writes a fee with no direction and the tax in its own column`() {
        // given
        val fee = captured(
            id = FEE_ID,
            kind = CapturedKind.Fee(parent = PRINCIPAL_ID, tax = PositiveDouble.unsafe(3.0)),
        )

        // when
        val res = with(mapper) { fee.toEntity() }

        // then
        res shouldBe PrincipalRow.copy(
            kind = "FEE",
            direction = null,
            parentId = PRINCIPAL_ID.value,
            taxAmount = 3.0,
            id = FEE_ID.value,
        )
    }

    @Test
    fun `rejects a principal row that has no direction`() {
        // given
        val corrupted = PrincipalRow.copy(direction = null)

        // when
        val res = with(mapper) { corrupted.toDomain() }

        // then
        res.shouldBeLeft() shouldBe "Principal row '${PRINCIPAL_ID.value}' has no direction"
    }

    @Test
    fun `rejects a row with an unknown money direction`() {
        // given
        val corrupted = PrincipalRow.copy(direction = "SIDEWAYS")

        // when
        val res = with(mapper) { corrupted.toDomain() }

        // then
        res.shouldBeLeft() shouldBe "Unknown money direction 'SIDEWAYS'"
    }

    @Test
    fun `rejects a row with an unknown kind`() {
        // given
        val corrupted = PrincipalRow.copy(kind = "REFUND")

        // when
        val res = with(mapper) { corrupted.toDomain() }

        // then
        res.shouldBeLeft() shouldBe "Unknown captured kind 'REFUND' on row '${PRINCIPAL_ID.value}'"
    }

    @Test
    fun `rejects a row with an unknown origin`() {
        // given
        val corrupted = PrincipalRow.copy(origin = "TELEPATHY")

        // when
        val res = with(mapper) { corrupted.toDomain() }

        // then
        res.shouldBeLeft() shouldBe "Unknown capture origin 'TELEPATHY' on row '${PRINCIPAL_ID.value}'"
    }

    @Test
    fun `rejects a row whose amount is not positive`() {
        // given
        val corrupted = PrincipalRow.copy(amount = 0.0)

        // when
        val res = with(mapper) { corrupted.toDomain() }

        // then
        res.shouldBeLeft() shouldBe "PositiveDouble error: 0.0 is not > 0"
    }

    @Test
    fun `rejects a row with a blank sender`() {
        // given
        val corrupted = PrincipalRow.copy(senderId = "   ")

        // when
        val res = with(mapper) { corrupted.toDomain() }

        // then
        res.shouldBeLeft() shouldBe "SenderId error: NotBlankTrimmedString error: Cannot be blank"
    }

    @Test
    fun `rejects a row with a blank asset code`() {
        // given
        val corrupted = PrincipalRow.copy(assetCode = "")

        // when
        val res = with(mapper) { corrupted.toDomain() }

        // then
        res.shouldBeLeft() shouldBe "AssetCode error: NotBlankTrimmedString error: Cannot be blank"
    }

    @Test
    fun `degrades a blank counterparty to null instead of failing the whole row`() {
        // given
        val corrupted = PrincipalRow.copy(counterparty = "   ")

        // when
        val res = with(mapper) { corrupted.toDomain() }

        // then
        res.shouldBeRight().counterparty shouldBe null
    }

    @Test
    fun `degrades an unusable reference to null instead of failing the whole row`() {
        // given
        val corrupted = PrincipalRow.copy(reference = "not a reference!")

        // when
        val res = with(mapper) { corrupted.toDomain() }

        // then
        res.shouldBeRight().reference shouldBe null
    }

    @Test
    fun `ignores columns the kind cannot carry`() {
        // given a principal row that also carries fee-only columns
        val corrupted = PrincipalRow.copy(parentId = FEE_ID.value, taxAmount = 5.0)

        // when
        val res = with(mapper) { corrupted.toDomain() }

        // then
        res.shouldBeRight().kind shouldBe CapturedKind.Principal(MoneyDirection.MoneyOut)
    }

    @Test
    fun `degrades a non-positive tax to null instead of failing the fee row`() {
        // given
        val corrupted = PrincipalRow.copy(kind = "FEE", direction = null, taxAmount = -1.0)

        // when
        val res = with(mapper) { corrupted.toDomain() }

        // then
        res.shouldBeRight().kind shouldBe CapturedKind.Fee(parent = null, tax = null)
    }

    companion object {
        private val PRINCIPAL_ID = CapturedTransactionId(UUID.randomUUID())
        private val FEE_ID = CapturedTransactionId(UUID.randomUUID())
        private val ACCOUNT_ID = AccountId(UUID.randomUUID())
        private val CATEGORY_ID = CategoryId(UUID.randomUUID())
        private val DUPLICATE_OF = TransactionId(UUID.randomUUID())
        private val TIME: Instant = Instant.parse("2026-07-25T16:50:00Z")
        private val CAPTURED_AT: Instant = Instant.parse("2026-07-25T16:51:00Z")

        @Suppress("LongParameterList")
        private fun captured(
            id: CapturedTransactionId = PRINCIPAL_ID,
            kind: CapturedKind = CapturedKind.Principal(MoneyDirection.MoneyOut),
            counterparty: NotBlankTrimmedString? = NotBlankTrimmedString.unsafe("James Mwangi"),
            reference: ProviderReference? = ProviderReference.unsafe("UGP7B0ITE4"),
            account: AccountId? = ACCOUNT_ID,
            category: CategoryId? = CATEGORY_ID,
            description: NotBlankTrimmedString? = NotBlankTrimmedString.unsafe("Lunch"),
            duplicateOf: TransactionId? = DUPLICATE_OF,
            origin: CaptureOrigin = CaptureOrigin.LiveBroadcast,
        ) = CapturedTransaction(
            id = id,
            sender = SenderId.unsafe("MPESA"),
            kind = kind,
            amount = PositiveDouble.unsafe(1350.0),
            asset = AssetCode.unsafe("KES"),
            time = TIME,
            counterparty = counterparty,
            reference = reference,
            account = account,
            category = category,
            description = description,
            duplicateOf = duplicateOf,
            capturedAt = CAPTURED_AT,
            origin = origin,
        )

        /** The flat form of [captured] with its defaults — the starting point for corrupt rows. */
        private val PrincipalRow = CapturedTransactionEntity(
            senderId = "MPESA",
            kind = "PRINCIPAL",
            direction = "MONEY_OUT",
            parentId = null,
            taxAmount = null,
            amount = 1350.0,
            assetCode = "KES",
            dateTime = TIME,
            counterparty = "James Mwangi",
            reference = "UGP7B0ITE4",
            accountId = ACCOUNT_ID.value,
            categoryId = CATEGORY_ID.value,
            description = "Lunch",
            duplicateOfTransactionId = DUPLICATE_OF.value,
            capturedAt = CAPTURED_AT,
            origin = "LIVE_BROADCAST",
            id = PRINCIPAL_ID.value,
        )
    }
}
