package com.iris.home.customerjourney

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import com.iris.design.l0_system.Gradient
import com.iris.domain.RootScreen
import com.iris.legacy.IrisWalletCtx
import com.iris.navigation.Navigation

@Immutable
data class CustomerJourneyCardModel(
    val id: String,
    val condition: (trnCount: Long, plannedPaymentsCount: Long, irisContext: IrisWalletCtx) -> Boolean,

    val title: String,
    val description: String,
    val cta: String?,
    @DrawableRes val ctaIcon: Int,

    val hasDismiss: Boolean = true,

    val background: Gradient,
    val onAction: (Navigation, IrisWalletCtx, RootScreen) -> Unit
)
