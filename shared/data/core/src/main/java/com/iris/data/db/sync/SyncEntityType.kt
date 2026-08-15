package com.iris.data.db.sync

/**
 * Tables whose changes are tracked for cloud sync.
 *
 * Device local or derived data (exchange rates cache, the legacy `users` table)
 * is intentionally not tracked.
 */
enum class SyncEntityType(
    val tableName: String,
    val idColumns: List<String>,
    /**
     * Rank of the table in the reference graph. Records are created in ascending
     * and deleted in descending order, so that a row is never applied before the
     * rows it refers to.
     */
    val dependencyOrder: Int
) {
    ACCOUNT("accounts", listOf("id"), dependencyOrder = 0),
    CATEGORY("categories", listOf("id"), dependencyOrder = 0),
    TAG("tags", listOf("id"), dependencyOrder = 0),
    SETTINGS("settings", listOf("id"), dependencyOrder = 0),
    BUDGET("budgets", listOf("id"), dependencyOrder = 1),
    LOAN("loans", listOf("id"), dependencyOrder = 1),
    TRANSACTION("transactions", listOf("id"), dependencyOrder = 2),
    LOAN_RECORD("loan_records", listOf("id"), dependencyOrder = 2),
    PLANNED_PAYMENT_RULE("planned_payment_rules", listOf("id"), dependencyOrder = 2),
    TAG_ASSOCIATION("tags_association", listOf("tagId", "associatedId"), dependencyOrder = 3);

    /**
     * SQL expression producing the entity id of a row of this table.
     *
     * [rowAlias] is `NEW` or `OLD` inside a trigger body, and `null` when
     * selecting directly from the table.
     */
    internal fun idExpression(rowAlias: String? = null): String {
        val prefix = rowAlias?.let { "$it." }.orEmpty()
        return idColumns.joinToString(separator = " || '$ID_SEPARATOR' || ") { "$prefix$it" }
    }

    companion object {
        /** Separator used to build the entity id of tables with a composite primary key. */
        const val ID_SEPARATOR = ":"

        fun fromTableName(tableName: String): SyncEntityType? =
            entries.firstOrNull { it.tableName == tableName }
    }
}
