package com.iris.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iris.data.db.migration.Migration123to124_LoanIncludeDateTime
import com.iris.data.db.migration.Migration124to125_LoanEditDateTime
import com.iris.data.db.migration.Migration126to127_LoanRecordType
import com.iris.data.db.migration.Migration129to130_LoanIncludeNote
import com.iris.data.db.migration.Migration130to131_SmsCapture
import com.iris.data.model.LoanType
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class IrisRoomDatabaseMigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        IrisRoomDatabase::class.java,
        listOf(IrisRoomDatabase.DeleteSEMigration()),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate129to130_LoanIncludeNote() {
        helper.createDatabase(TestDb, 129).apply {
            val insertSql = """
                INSERT INTO loans (name, amount, type, color, icon, orderNum, accountId, isSynced, isDeleted, dateTime, id) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
            """.trimIndent()

            val preparedStatement = compileStatement(insertSql).apply {
                // Bind values
                bindString(1, "Loan 1")
                bindDouble(2, 123.50)
                bindString(3, LoanType.BORROW.name) // Assuming you store enum as name
                bindLong(4, 13)
                bindString(5, "ic")
                bindDouble(6, 3.14)
                bindString(7, UUID.randomUUID().toString())
                bindLong(8, 1)
                bindLong(9, 0)
                bindString(10, "")
                bindString(11, UUID.randomUUID().toString())
            }
            preparedStatement.executeInsert()
            close()
        }

        val newDb = helper.runMigrationsAndValidate(
            TestDb,
            130,
            true,
            Migration129to130_LoanIncludeNote()
        )

        newDb.query("SELECT * FROM loans").apply {
            moveToFirst() shouldBe true
            getString(0) shouldBe "Loan 1"
            getDouble(1) shouldBe 123.50
            getString(2) shouldBe LoanType.BORROW.name
        }
        newDb.close()
    }

    /**
     * The migration is strictly additive, so this test asserts two separate things:
     * a pre-existing `transactions` row survives untouched, and each of the four new tables
     * actually exists and accepts a row.
     *
     * `runMigrationsAndValidate` additionally checks the hand-written DDL against the exported
     * `131.json`, so entity/SQL drift fails here rather than on a user's device.
     */
    @Suppress("LongMethod")
    @Test
    fun migrate130to131_SmsCapture() {
        // given
        val transactionId = UUID.randomUUID().toString()
        val accountId = UUID.randomUUID().toString()
        helper.createDatabase(TestDb, 130).apply {
            val insertSql = """
                INSERT INTO transactions (accountId, type, amount, title, dateTime, isSynced, isDeleted, id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?);
            """.trimIndent()

            compileStatement(insertSql).apply {
                bindString(1, accountId)
                bindString(2, "EXPENSE")
                bindDouble(3, 1350.0)
                bindString(4, "Existing row")
                bindLong(5, Instant.parse("2026-07-25T16:50:00Z").toEpochMilli())
                bindLong(6, 1)
                bindLong(7, 0)
                bindString(8, transactionId)
            }.executeInsert()
            close()
        }

        // when
        val newDb = helper.runMigrationsAndValidate(
            TestDb,
            131,
            true,
            Migration130to131_SmsCapture()
        )

        // then: the pre-existing ledger row is untouched
        newDb.query("SELECT accountId, amount, title, id FROM transactions").apply {
            moveToFirst() shouldBe true
            count shouldBe 1
            getString(0) shouldBe accountId
            getDouble(1) shouldBe 1350.0
            getString(2) shouldBe "Existing row"
            getString(3) shouldBe transactionId
            close()
        }

        // and: each new table exists and accepts a row
        val senderId = "MPESA"
        newDb.execSQL(
            "INSERT INTO financial_senders (displayName, ruleSetId, accountId, enabled, senderId) " +
                "VALUES ('M-PESA', 'mpesa', '" + accountId + "', 1, '" + senderId + "')"
        )

        val capturedId = UUID.randomUUID().toString()
        newDb.execSQL(
            "INSERT INTO captured_transactions (senderId, kind, direction, parentId, taxAmount, " +
                "amount, assetCode, dateTime, counterparty, reference, accountId, categoryId, " +
                "description, duplicateOfTransactionId, capturedAt, origin, id) VALUES " +
                "('" + senderId + "', 'PRINCIPAL', 'MONEY_OUT', NULL, NULL, 1350.0, 'KES', " +
                "1784739000000, 'James Kinyua Mwangi', 'UGP7B0ITE4', NULL, NULL, NULL, NULL, " +
                "1784739060000, 'LIVE_BROADCAST', '" + capturedId + "')"
        )

        newDb.execSQL(
            "INSERT INTO processed_messages (senderId, outcome, processedAt, fingerprint) " +
                "VALUES ('" + senderId + "', 'CAPTURED', 1784739060000, 'mpesa:UGP7B0ITE4')"
        )

        newDb.execSQL(
            "INSERT INTO counterparty_categories (categoryId, updatedAt, counterpartyKey) " +
                "VALUES ('" + UUID.randomUUID().toString() + "', 1784739060000, 'JAMESKINYUAMWANGI')"
        )

        newDb.query("SELECT COUNT(*) FROM financial_senders").apply {
            moveToFirst() shouldBe true
            getInt(0) shouldBe 1
            close()
        }
        newDb.query("SELECT kind, direction, amount FROM captured_transactions").apply {
            moveToFirst() shouldBe true
            getString(0) shouldBe "PRINCIPAL"
            getString(1) shouldBe "MONEY_OUT"
            getDouble(2) shouldBe 1350.0
            close()
        }
        newDb.query("SELECT fingerprint FROM processed_messages").apply {
            moveToFirst() shouldBe true
            getString(0) shouldBe "mpesa:UGP7B0ITE4"
            close()
        }
        newDb.query("SELECT counterpartyKey FROM counterparty_categories").apply {
            moveToFirst() shouldBe true
            getString(0) shouldBe "JAMESKINYUAMWANGI"
            close()
        }
        newDb.close()
    }

    @Test
    fun migrate123to125_LoanDateTime() {
        // given
        helper.createDatabase(TestDb, 123).apply {
            // Database has schema version 1. Insert some data using SQL queries.
            // You can't use DAO classes because they expect the latest schema.
            val insertSql = """
                INSERT INTO loans (name, amount, type, color, icon, orderNum, accountId, isSynced, isDeleted, id) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
            """.trimIndent()

            // Assuming you have an instance of LoanEntity named loanEntity
            val preparedStatement = compileStatement(insertSql).apply {
                // Bind the values from your LoanEntity instance to the prepared statement
                bindString(1, "Loan 1")
                bindDouble(2, 123.50)
                bindString(3, LoanType.BORROW.name) // Assuming you store enum as name
                bindLong(4, 13)
                bindString(5, "ic")
                bindDouble(6, 3.14)
                bindString(7, UUID.randomUUID().toString())
                bindLong(8, 1)
                bindLong(9, 0)
                bindString(10, UUID.randomUUID().toString())
            }
            preparedStatement.executeInsert()
            close()
        }

        // when
        helper.runMigrationsAndValidate(
            TestDb,
            124,
            true,
            Migration123to124_LoanIncludeDateTime()
        )
        val newDb = helper.runMigrationsAndValidate(
            TestDb,
            125,
            true,
            Migration124to125_LoanEditDateTime()
        )

        // then
        newDb.query("SELECT * FROM loans").apply {
            moveToFirst() shouldBe true
            getString(0) shouldBe "Loan 1"
            getDouble(1) shouldBe 123.50
            getString(2) shouldBe LoanType.BORROW.name
        }
        newDb.close()
    }

    @Test
    fun migrate126to127_LoanRecordType() {
        // given
        val loanId = UUID.randomUUID().toString()
        val noteString = "here is your note"
        helper.createDatabase(TestDb, 126).apply {
            // Database has schema version 1. Insert some data using SQL queries.
            // You can't use DAO classes because they expect the latest schema.
            val insertSql = """
                INSERT INTO loan_records (loanId, amount, note, dateTime, interest, accountId, convertedAmount, isSynced, isDeleted, id) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
            """.trimIndent()
            // Assuming you have an instance of LoanRecordEntity named loanRecordEntity
            val preparedStatement = compileStatement(insertSql).apply {
                // Bind the values from your LoanRecordEntity instance to the prepared statement
                bindString(1, loanId)
                bindDouble(2, 123.50)
                bindString(3, noteString)
                bindString(4, "this will fail, LocalDateTimeNeeded")
                bindLong(5, 0) // interest
                bindString(6, UUID.randomUUID().toString())
                bindDouble(7, 3.14) // convertedAmount
                bindLong(8, 1)
                bindLong(9, 0)
                bindString(10, UUID.randomUUID().toString())
            }
            preparedStatement.executeInsert()
            close()
        }

        // when
        val newDb = helper.runMigrationsAndValidate(
            TestDb,
            127,
            true,
            Migration126to127_LoanRecordType()
        )

        // then
        newDb.query("SELECT * FROM loan_records").apply {
            moveToFirst() shouldBe true
            getString(0) shouldBe loanId
            getDouble(1) shouldBe 123.50
            getString(2) shouldBe noteString
            getString(10) shouldBe "DECREASE"
        }
        newDb.close()
    }

    @Test
    fun migrateAll() {
        // given:
        // Create earliest version of the database:
        // for Iris Wallet versions below 106 are broken :/
        helper.createDatabase(TestDb, 106).apply {
            close()
        }

        // then:
        // Open latest version of the database.
        // Room validates and executes all migrations.
        Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            IrisRoomDatabase::class.java,
            TestDb
        ).addMigrations(*IrisRoomDatabase.migrations()).build().apply {
            openHelper.writableDatabase.close()
        }
    }

    companion object {
        private const val TestDb = "migration-test"
    }
}