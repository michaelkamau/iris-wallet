package com.iris.legacy.ui.component.transaction

import androidx.compose.runtime.Composable
import com.iris.data.model.Category
import com.iris.legacy.datamodel.Account
import com.iris.legacy.irisWalletCtx
import java.util.UUID

@Deprecated("Old design system. Use `:iris-design` and Material3")
@Composable
fun category(
    categoryId: UUID?,
    categories: List<Category>
): Category? {
    val targetId = categoryId ?: return null
    return irisWalletCtx().categoryMap[targetId] ?: categories.find { it.id.value == targetId }
}

@Deprecated("Old design system. Use `:iris-design` and Material3")
@Composable
fun account(
    accountId: UUID?,
    accounts: List<Account>
): Account? {
    val targetId = accountId ?: return null
    return irisWalletCtx().accountMap[targetId] ?: accounts.find { it.id == targetId }
}
