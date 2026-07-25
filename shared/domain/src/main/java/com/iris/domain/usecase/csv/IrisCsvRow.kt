package com.iris.domain.usecase.csv

import com.iris.base.model.TransactionType
import com.iris.data.model.AccountId
import com.iris.data.model.CategoryId
import com.iris.data.model.TransactionId
import com.iris.data.model.primitive.AssetCode
import com.iris.data.model.primitive.NonNegativeDouble
import com.iris.data.model.primitive.NotBlankTrimmedString
import com.iris.data.model.primitive.PositiveDouble
import java.time.Instant

// TODO: Fix Iris Explicit detekt false-positives
@SuppressWarnings("DataClassTypedIDs")
data class IrisCsvRow(
    val date: Instant?,
    val title: NotBlankTrimmedString?,
    val category: CategoryId?,
    val account: AccountId,
    val amount: NonNegativeDouble,
    val currency: AssetCode,
    val type: TransactionType,
    val transferAmount: PositiveDouble?,
    val transferCurrency: AssetCode?,
    val toAccountId: AccountId?,
    val receiveAmount: PositiveDouble?,
    val receiveCurrency: AssetCode?,
    val description: NotBlankTrimmedString?,
    val dueData: Instant?,
    val id: TransactionId
) {
    companion object {
        val Columns = listOf(
            "Date",
            "Title",
            "Category",
            "Account",
            "Amount",
            "Currency",
            "Type",
            "Transfer Amount",
            "Transfer Currency",
            "To Account",
            "Receive Amount",
            "Receive Currency",
            "Description",
            "Due Date",
            "ID",
        )
    }
}