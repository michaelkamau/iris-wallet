package com.iris.wallet.domain.action.viewmodel.home

import com.iris.data.model.Category
import com.iris.frp.action.FPAction
import com.iris.legacy.IrisWalletCtx
import javax.inject.Inject

class UpdateCategoriesCacheAct @Inject constructor(
    private val irisWalletCtx: IrisWalletCtx
) : FPAction<List<Category>, List<Category>>() {
    override suspend fun List<Category>.compose(): suspend () -> List<Category> = suspend {
        val categories = this

        irisWalletCtx.categoryMap.clear()
        irisWalletCtx.categoryMap.putAll(categories.map { it.id.value to it })

        categories
    }
}
