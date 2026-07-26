package com.iris.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds the four SMS-capture tables. **Strictly additive** — no existing table, column or index is
 * touched, which is how SC-010 ("nothing existing changes") is guaranteed at the schema level
 * rather than by testing every existing feature again.
 *
 * The DDL is written by hand and Room's `runMigrationsAndValidate` checks it against the exported
 * `131.json`, so a drift between this SQL and the entity definitions fails the build.
 */
@Suppress("MagicNumber", "ClassNaming")
class Migration130to131_SmsCapture : Migration(130, 131) {

    @Suppress("MaximumLineLength", "MaxLineLength")
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `$FINANCIAL_SENDERS` (`displayName` TEXT NOT NULL, `ruleSetId` TEXT, `accountId` TEXT, `enabled` INTEGER NOT NULL, `senderId` TEXT NOT NULL, PRIMARY KEY(`senderId`))"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `$CAPTURED_TRANSACTIONS` (`senderId` TEXT NOT NULL, `kind` TEXT NOT NULL, `direction` TEXT, `parentId` TEXT, `taxAmount` REAL, `amount` REAL NOT NULL, `assetCode` TEXT NOT NULL, `dateTime` INTEGER NOT NULL, `counterparty` TEXT, `reference` TEXT, `accountId` TEXT, `categoryId` TEXT, `description` TEXT, `duplicateOfTransactionId` TEXT, `capturedAt` INTEGER NOT NULL, `origin` TEXT NOT NULL, `id` TEXT NOT NULL, PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_${CAPTURED_TRANSACTIONS}_parentId` ON `$CAPTURED_TRANSACTIONS` (`parentId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_${CAPTURED_TRANSACTIONS}_dateTime` ON `$CAPTURED_TRANSACTIONS` (`dateTime`)"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `$PROCESSED_MESSAGES` (`senderId` TEXT NOT NULL, `outcome` TEXT NOT NULL, `processedAt` INTEGER NOT NULL, `fingerprint` TEXT NOT NULL, PRIMARY KEY(`fingerprint`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_${PROCESSED_MESSAGES}_processedAt` ON `$PROCESSED_MESSAGES` (`processedAt`)"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `$COUNTERPARTY_CATEGORIES` (`categoryId` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, `counterpartyKey` TEXT NOT NULL, PRIMARY KEY(`counterpartyKey`))"
        )
    }

    companion object {
        private const val FINANCIAL_SENDERS = "financial_senders"
        private const val CAPTURED_TRANSACTIONS = "captured_transactions"
        private const val PROCESSED_MESSAGES = "processed_messages"
        private const val COUNTERPARTY_CATEGORIES = "counterparty_categories"
    }
}
