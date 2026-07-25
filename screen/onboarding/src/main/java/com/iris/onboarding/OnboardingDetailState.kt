package com.iris.onboarding

import androidx.compose.runtime.Immutable
import com.iris.data.model.Category
import com.iris.legacy.data.model.AccountBalance
import com.iris.wallet.domain.data.IrisCurrency
import com.iris.wallet.domain.deprecated.logic.model.CreateAccountData
import com.iris.wallet.domain.deprecated.logic.model.CreateCategoryData
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class OnboardingDetailState(
    val currency: IrisCurrency,
    val accounts: ImmutableList<AccountBalance>,
    val accountSuggestions: ImmutableList<CreateAccountData>,
    val categories: ImmutableList<Category>,
    val categorySuggestions: ImmutableList<CreateCategoryData>
)
