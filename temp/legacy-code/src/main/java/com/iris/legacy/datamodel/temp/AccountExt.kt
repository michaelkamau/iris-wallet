package com.iris.legacy.datamodel.temp

import com.iris.data.db.entity.AccountEntity
import com.iris.legacy.datamodel.Account

fun AccountEntity.toLegacyDomain(): Account = Account(
    name = name,
    currency = currency,
    color = color,
    icon = icon,
    orderNum = orderNum,
    includeInBalance = includeInBalance,
    isSynced = isSynced,
    isDeleted = isDeleted,
    id = id
)
