package com.iris.legacy

import com.iris.base.legacy.stringRes
import com.iris.data.model.LoanType
import com.iris.legacy.datamodel.Loan
import com.iris.ui.R

fun Loan.humanReadableType(): String {
    return if (type == LoanType.BORROW) {
        stringRes(R.string.borrowed_uppercase)
    } else {
        stringRes(R.string.lent_uppercase)
    }
}
