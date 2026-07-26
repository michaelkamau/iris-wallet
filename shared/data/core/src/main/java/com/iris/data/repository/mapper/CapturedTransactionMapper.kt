package com.iris.data.repository.mapper

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
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
import javax.inject.Inject

/**
 * Rebuilds the `CapturedKind` ADT from the flat row and back.
 *
 * The flat form has states the ADT does not (a `PRINCIPAL` with a `taxAmount`, a `FEE` with a
 * `direction`); `toDomain` drops those surplus columns and rejects only rows it cannot rebuild at
 * all, so nothing downstream has to consider them. A rejected row is dropped at the repository
 * boundary, exactly as `CategoryMapper`'s deleted rows are.
 */
class CapturedTransactionMapper @Inject constructor() {

    fun CapturedTransactionEntity.toDomain(): Either<String, CapturedTransaction> = either {
        CapturedTransaction(
            id = CapturedTransactionId(id),
            sender = SenderId.from(senderId).bind(),
            kind = kind().bind(),
            amount = PositiveDouble.from(amount).bind(),
            asset = AssetCode.from(assetCode).bind(),
            time = dateTime,
            counterparty = counterparty?.let(NotBlankTrimmedString::from)?.getOrNull(),
            reference = reference?.let(ProviderReference::from)?.getOrNull(),
            account = accountId?.let(::AccountId),
            category = categoryId?.let(::CategoryId),
            description = description?.let(NotBlankTrimmedString::from)?.getOrNull(),
            duplicateOf = duplicateOfTransactionId?.let(::TransactionId),
            capturedAt = capturedAt,
            origin = origin().bind(),
        )
    }

    fun CapturedTransaction.toEntity(): CapturedTransactionEntity = CapturedTransactionEntity(
        senderId = sender.value,
        kind = when (kind) {
            is CapturedKind.Principal -> PRINCIPAL
            is CapturedKind.Fee -> FEE
        },
        direction = (kind as? CapturedKind.Principal)?.direction?.let(::directionColumn),
        parentId = (kind as? CapturedKind.Fee)?.parent?.value,
        taxAmount = (kind as? CapturedKind.Fee)?.tax?.value,
        amount = amount.value,
        assetCode = asset.code,
        dateTime = time,
        counterparty = counterparty?.value,
        reference = reference?.value,
        accountId = account?.value,
        categoryId = category?.value,
        description = description?.value,
        duplicateOfTransactionId = duplicateOf?.value,
        capturedAt = capturedAt,
        origin = when (origin) {
            CaptureOrigin.LiveBroadcast -> LIVE_BROADCAST
            CaptureOrigin.HistoricalImport -> HISTORICAL_IMPORT
        },
        id = id.value,
    )

    private fun CapturedTransactionEntity.kind(): Either<String, CapturedKind> = either {
        when (kind) {
            PRINCIPAL -> {
                val raw = direction
                ensure(raw != null) { "Principal row '$id' has no direction" }
                CapturedKind.Principal(direction(raw).bind())
            }

            FEE -> CapturedKind.Fee(
                parent = parentId?.let(::CapturedTransactionId),
                tax = taxAmount?.let(PositiveDouble::from)?.getOrNull(),
            )

            else -> raise("Unknown captured kind '$kind' on row '$id'")
        }
    }

    private fun direction(raw: String): Either<String, MoneyDirection> = either {
        when (raw) {
            MONEY_OUT -> MoneyDirection.MoneyOut
            MONEY_IN -> MoneyDirection.MoneyIn
            else -> raise("Unknown money direction '$raw'")
        }
    }

    private fun directionColumn(value: MoneyDirection): String = when (value) {
        MoneyDirection.MoneyOut -> MONEY_OUT
        MoneyDirection.MoneyIn -> MONEY_IN
    }

    private fun CapturedTransactionEntity.origin(): Either<String, CaptureOrigin> = either {
        when (origin) {
            LIVE_BROADCAST -> CaptureOrigin.LiveBroadcast
            HISTORICAL_IMPORT -> CaptureOrigin.HistoricalImport
            else -> raise("Unknown capture origin '$origin' on row '$id'")
        }
    }

    companion object {
        /** Matched literally by `CapturedTransactionDao.pendingCount()`. */
        const val PRINCIPAL = "PRINCIPAL"
        const val FEE = "FEE"

        private const val MONEY_OUT = "MONEY_OUT"
        private const val MONEY_IN = "MONEY_IN"
        private const val LIVE_BROADCAST = "LIVE_BROADCAST"
        private const val HISTORICAL_IMPORT = "HISTORICAL_IMPORT"
    }
}
