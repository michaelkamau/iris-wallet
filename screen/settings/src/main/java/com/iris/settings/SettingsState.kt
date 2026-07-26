package com.iris.settings

import com.iris.base.legacy.Theme

data class SettingsState(
    val currencyCode: String,
    val name: String,
    val currentTheme: Theme,
    val lockApp: Boolean,
    val showNotifications: Boolean,
    val hideCurrentBalance: Boolean,
    val hideIncome: Boolean,
    val treatTransfersAsIncomeExpense: Boolean,
    val startDateOfMonth: String,
    val progressState: Boolean,
    val languageOptionVisible: Boolean,
    /**
     * Captured transactions waiting to be reviewed, shown as a badge on the SMS capture row.
     *
     * Zero when the feature has never been switched on, which is every existing user until they
     * ask for it — so the row reads exactly as any other settings entry (FR-001, SC-010).
     */
    val smsPendingCount: Int
)
