package com.iris.legacy.datamodel.temp

import com.iris.data.db.entity.LoanRecordEntity
import com.iris.legacy.datamodel.LoanRecord

fun LoanRecordEntity.toLegacyDomain(): LoanRecord = LoanRecord(
    loanId = loanId,
    amount = amount,
    note = note,
    dateTime = dateTime,
    interest = interest,
    accountId = accountId,
    convertedAmount = convertedAmount,
    loanRecordType = loanRecordType,
    isSynced = isSynced,
    isDeleted = isDeleted,
    id = id
)
