package com.iris.loans.loan.data

import com.iris.legacy.datamodel.Account
import com.iris.legacy.datamodel.LoanRecord

data class DisplayLoanRecord(
    val loanRecord: LoanRecord,
    val account: Account? = null,
    val loanRecordCurrencyCode: String = "",
    val loanCurrencyCode: String = "",
    val loanRecordTransaction: Boolean = false,
)
