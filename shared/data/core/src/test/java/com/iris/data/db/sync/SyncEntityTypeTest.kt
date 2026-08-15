package com.iris.data.db.sync

import io.kotest.matchers.shouldBe
import org.junit.Test

class SyncEntityTypeTest {

    @Test
    fun `id expression of a single column primary key`() {
        SyncEntityType.TRANSACTION.idExpression("NEW") shouldBe "NEW.id"
        SyncEntityType.TRANSACTION.idExpression() shouldBe "id"
    }

    @Test
    fun `id expression of a composite primary key`() {
        SyncEntityType.TAG_ASSOCIATION.idExpression("OLD") shouldBe
            "OLD.tagId || ':' || OLD.associatedId"
        SyncEntityType.TAG_ASSOCIATION.idExpression() shouldBe
            "tagId || ':' || associatedId"
    }

    @Test
    fun `table names are unique and resolvable`() {
        val tableNames = SyncEntityType.entries.map { it.tableName }

        tableNames.distinct().size shouldBe tableNames.size
        tableNames.forEach { tableName ->
            SyncEntityType.fromTableName(tableName) shouldBe
                SyncEntityType.entries.first { it.tableName == tableName }
        }
    }

    @Test
    fun `device local tables are not tracked`() {
        SyncEntityType.fromTableName("exchange_rates") shouldBe null
        SyncEntityType.fromTableName("users") shouldBe null
    }
}
