package com.iris.legacy.datamodel.temp

import com.iris.data.db.entity.CategoryEntity
import com.iris.legacy.datamodel.Category

fun CategoryEntity.toLegacyDomain(): Category = Category(
    name = name,
    color = color,
    icon = icon,
    orderNum = orderNum,
    isSynced = isSynced,
    isDeleted = isDeleted,
    id = id
)
