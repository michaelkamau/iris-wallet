package com.iris.data.model

import com.iris.data.model.primitive.AssetCode
import com.iris.data.model.primitive.ColorInt
import com.iris.data.model.primitive.IconAsset
import com.iris.data.model.primitive.NotBlankTrimmedString
import com.iris.data.model.sync.Identifiable
import com.iris.data.model.sync.UniqueId
import java.util.UUID

@JvmInline
value class AccountId(override val value: UUID) : UniqueId

data class Account(
    override val id: AccountId,
    val name: NotBlankTrimmedString,
    val asset: AssetCode,
    val color: ColorInt,
    val icon: IconAsset?,
    val includeInBalance: Boolean,
    override val orderNum: Double,
) : Identifiable<AccountId>, Reorderable
