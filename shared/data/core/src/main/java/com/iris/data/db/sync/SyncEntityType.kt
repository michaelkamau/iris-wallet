package com.iris.data.db.sync

/**
 * Tables whose changes are tracked for cloud sync.
 *
 * Device local or derived data (exchange rates cache, the legacy `users` table)
 * is intentionally not tracked.
 */
enum class SyncEntityType(
    val tableName: String,
    private val idColumns: List<String>
) {
    ACCOUNT("accounts", listOf("id")),
    TRANSACTION("transactions", listOf("id")),
    CATEGORY("categories", listOf("id")),
    BUDGET("budgets", listOf("id")),
    LOAN("loans", listOf("id")),
    LOAN_RECORD("loan_records", listOf("id")),
    PLANNED_PAYMENT_RULE("planned_payment_rules", listOf("id")),
    SETTINGS("settings", listOf("id")),
    TAG("tags", listOf("id")),
    TAG_ASSOCIATION("tags_association", listOf("tagId", "associatedId"));

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
