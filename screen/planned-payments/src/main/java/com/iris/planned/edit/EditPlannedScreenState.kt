package com.iris.planned.edit

import com.iris.base.model.TransactionType
import com.iris.data.model.Category
import com.iris.data.model.IntervalType
import com.iris.legacy.datamodel.Account
import com.iris.wallet.ui.theme.modal.RecurringRuleModalData
import com.iris.wallet.ui.theme.modal.edit.AccountModalData
import com.iris.wallet.ui.theme.modal.edit.CategoryModalData
import kotlinx.collections.immutable.ImmutableList
import java.time.LocalDateTime
import javax.annotation.concurrent.Immutable

@Immutable
data class EditPlannedScreenState(
    val currency: String,
    val transactionType: TransactionType,
    val startDate: LocalDateTime?,
    val intervalN: Int?,
    val intervalType: IntervalType?,
    val oneTime: Boolean,
    val initialTitle: String?,
    val description: String?,
    val categories: ImmutableList<Category>,
    val accounts: ImmutableList<Account>,
    val account: Account?,
    val category: Category?,
    val amount: Double,
    val categoryModalVisible: Boolean,
    val descriptionModalVisible: Boolean,
    val deleteTransactionModalVisible: Boolean,
    val categoryModalData: CategoryModalData?,
    val accountModalData: AccountModalData?,
    val recurringRuleModalData: RecurringRuleModalData?,
    val transactionTypeModalVisible: Boolean,
    val amountModalVisible: Boolean
)
