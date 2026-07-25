package com.iris.legacy.datamodel

import com.iris.base.legacy.Transaction
import com.iris.data.db.entity.TransactionEntity

fun Transaction.toEntity(): TransactionEntity = TransactionEntity(
    accountId = accountId,
    type = type,
    amount = amount.toDouble(),
    toAccountId = toAccountId,
    toAmount = toAmount.toDouble(),
    title = title,
    description = description,
    dateTime = dateTime,
    categoryId = categoryId,
    dueDate = dueDate,
    recurringRuleId = recurringRuleId,
    paidForDateTime = paidFor,
    attachmentUrl = attachmentUrl,
    loanId = loanId,
    loanRecordId = loanRecordId,
    id = id,
    isSynced = isSynced,
    isDeleted = isDeleted
)
