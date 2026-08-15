package com.iris.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.iris.base.model.TransactionType
import com.iris.data.db.migration.Migration130to131_SyncChangeLog
import com.iris.data.db.sync.SyncEntityType
import com.iris.data.db.sync.SyncOperation
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class Migration130to131Test {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        IrisRoomDatabase::class.java,
        listOf(IrisRoomDatabase.DeleteSEMigration()),
        FrameworkSQLiteOpenHelperFactory()
    )

    private val migration = Migration130to131_SyncChangeLog()

    @Test
    fun backfillsExistingRowsAsPendingChanges() {
        val accountId = UUID.randomUUID()
        helper.createDatabase(TestDb, 130).apply {
            insertAccount(accountId)
            close()
        }

        val db = migrate()

        db.changeLogOf(SyncEntityType.ACCOUNT.tableName, accountId.toString()).let { logRow ->
            logRow shouldNotBe null
            logRow!!.operation shouldBe SyncOperation.UPSERT.name
            logRow.pendingSync shouldBe true
            logRow.updatedAt shouldNotBe 0L
        }
        db.close()
    }

    @Test
    fun logsInsertUpdateAndDeleteOfTrackedRows() {
        val accountId = UUID.randomUUID()
        val transactionId = UUID.randomUUID()
        helper.createDatabase(TestDb, 130).apply { close() }
        val db = migrate()

        db.insertAccount(accountId)
        db.insertTransaction(id = transactionId, accountId = accountId)
        db.changeLogOf(
            SyncEntityType.TRANSACTION.tableName,
            transactionId.toString()
        )!!.operation shouldBe SyncOperation.UPSERT.name

        db.execSQL("UPDATE transactions SET title = 'renamed' WHERE id = ?", arrayOf<Any>(transactionId.toString()))
        db.changeLogOf(SyncEntityType.TRANSACTION.tableName, transactionId.toString()).let { logRow ->
            logRow!!.operation shouldBe SyncOperation.UPSERT.name
            logRow.pendingSync shouldBe true
        }

        db.execSQL("DELETE FROM transactions WHERE id = ?", arrayOf<Any>(transactionId.toString()))
        db.changeLogOf(SyncEntityType.TRANSACTION.tableName, transactionId.toString()).let { tombstone ->
            tombstone!!.operation shouldBe SyncOperation.DELETE.name
            tombstone.pendingSync shouldBe true
        }
        db.close()
    }

    @Test
    fun doesNotLogWhileChangeLoggingIsDisabled() {
        val accountId = UUID.randomUUID()
        helper.createDatabase(TestDb, 130).apply { close() }
        val db = migrate()

        db.execSQL("UPDATE sync_state SET changeLoggingEnabled = 0")
        db.insertAccount(accountId)

        db.changeLogOf(SyncEntityType.ACCOUNT.tableName, accountId.toString()) shouldBe null
        db.close()
    }

    @Test
    fun stampsChangesWithTheDeviceId() {
        val accountId = UUID.randomUUID()
        helper.createDatabase(TestDb, 130).apply { close() }
        val db = migrate()

        db.insertAccount(accountId)

        val deviceId = db.query("SELECT deviceId FROM sync_state WHERE id = 0").use { cursor ->
            cursor.moveToFirst() shouldBe true
            cursor.getString(0)
        }
        deviceId shouldNotBe null
        db.changeLogOf(SyncEntityType.ACCOUNT.tableName, accountId.toString())!!
            .deviceId shouldBe deviceId
        db.close()
    }

    private fun migrate(): SupportSQLiteDatabase =
        helper.runMigrationsAndValidate(TestDb, 131, true, migration)

    private fun SupportSQLiteDatabase.insertAccount(id: UUID) {
        execSQL(
            """
            INSERT INTO accounts (name, currency, color, icon, orderNum, includeInBalance, isSynced, isDeleted, id)
            VALUES ('Account', 'EUR', 1, 'ic', 0.0, 1, 0, 0, ?)
            """.trimIndent(),
            arrayOf<Any>(id.toString())
        )
    }

    private fun SupportSQLiteDatabase.insertTransaction(id: UUID, accountId: UUID) {
        execSQL(
            """
            INSERT INTO transactions (accountId, type, amount, isSynced, isDeleted, id)
            VALUES (?, ?, 10.0, 0, 0, ?)
            """.trimIndent(),
            arrayOf<Any>(accountId.toString(), TransactionType.EXPENSE.name, id.toString())
        )
    }

    private fun SupportSQLiteDatabase.changeLogOf(
        entityType: String,
        entityId: String
    ): ChangeLogRow? = query(
        "SELECT operation, updatedAt, deviceId, pendingSync FROM sync_change_log " +
            "WHERE entityType = ? AND entityId = ?",
        arrayOf<Any>(entityType, entityId)
    ).use { cursor ->
        if (!cursor.moveToFirst()) {
            null
        } else {
            ChangeLogRow(
                operation = cursor.getString(0),
                updatedAt = cursor.getLong(1),
                deviceId = cursor.getString(2),
                pendingSync = cursor.getInt(3) == 1
            )
        }
    }

    private data class ChangeLogRow(
        val operation: String,
        val updatedAt: Long,
        val deviceId: String?,
        val pendingSync: Boolean
    )

    companion object {
        private const val TestDb = "migration-test-130-131"
    }
}
