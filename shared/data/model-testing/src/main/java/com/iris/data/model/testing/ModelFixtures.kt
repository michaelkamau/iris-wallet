package com.iris.data.model.testing

import com.iris.data.model.AccountId
import com.iris.data.model.CategoryId
import com.iris.data.model.TransactionId
import java.util.UUID

object ModelFixtures {
    val AccountId = AccountId(UUID.randomUUID())
    val CategoryId = CategoryId(UUID.randomUUID())
    val TransactionId = TransactionId(UUID.randomUUID())
}
