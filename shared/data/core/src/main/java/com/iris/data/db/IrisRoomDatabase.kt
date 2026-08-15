package com.iris.data.db

import android.content.Context
import androidx.room.*
import androidx.room.migration.AutoMigrationSpec
import com.iris.data.db.dao.read.AccountDao
import com.iris.data.db.dao.read.BudgetDao
import com.iris.data.db.dao.read.CategoryDao
import com.iris.data.db.dao.read.ExchangeRatesDao
import com.iris.data.db.dao.read.LoanDao
import com.iris.data.db.dao.read.LoanRecordDao
import com.iris.data.db.dao.read.PlannedPaymentRuleDao
import com.iris.data.db.dao.read.SettingsDao
import com.iris.data.db.dao.read.SyncChangeLogDao
import com.iris.data.db.dao.read.SyncStateDao
import com.iris.data.db.dao.read.TagDao
import com.iris.data.db.dao.read.TagAssociationDao
import com.iris.data.db.dao.read.TransactionDao
import com.iris.data.db.dao.read.UserDao
import com.iris.data.db.dao.write.WriteAccountDao
import com.iris.data.db.dao.write.WriteBudgetDao
import com.iris.data.db.dao.write.WriteCategoryDao
import com.iris.data.db.dao.write.WriteExchangeRatesDao
import com.iris.data.db.dao.write.WriteLoanDao
import com.iris.data.db.dao.write.WriteLoanRecordDao
import com.iris.data.db.dao.write.WritePlannedPaymentRuleDao
import com.iris.data.db.dao.write.WriteSettingsDao
import com.iris.data.db.dao.write.WriteSyncChangeLogDao
import com.iris.data.db.dao.write.WriteSyncStateDao
import com.iris.data.db.dao.write.WriteTagDao
import com.iris.data.db.dao.write.WriteTagAssociationDao
import com.iris.data.db.dao.write.WriteTransactionDao
import com.iris.data.db.entity.AccountEntity
import com.iris.data.db.entity.BudgetEntity
import com.iris.data.db.entity.CategoryEntity
import com.iris.data.db.entity.ExchangeRateEntity
import com.iris.data.db.entity.LoanEntity
import com.iris.data.db.entity.LoanRecordEntity
import com.iris.data.db.entity.PlannedPaymentRuleEntity
import com.iris.data.db.entity.SettingsEntity
import com.iris.data.db.entity.SyncChangeLogEntity
import com.iris.data.db.entity.SyncStateEntity
import com.iris.data.db.entity.TagEntity
import com.iris.data.db.entity.TagAssociationEntity
import com.iris.data.db.entity.TransactionEntity
import com.iris.data.db.entity.UserEntity
import com.iris.data.db.migration.Migration123to124_LoanIncludeDateTime
import com.iris.data.db.migration.Migration124to125_LoanEditDateTime
import com.iris.data.db.migration.Migration126to127_LoanRecordType
import com.iris.data.db.migration.Migration127to128_PaidForDateRecord
import com.iris.data.db.migration.Migration128to129_DeleteIsDeleted
import com.iris.data.db.migration.Migration129to130_LoanIncludeNote
import com.iris.data.db.migration.Migration130to131_SyncChangeLog
import com.iris.data.db.sync.SyncDatabaseCallback
import com.iris.domain.db.RoomTypeConverters
import com.iris.domain.db.migration.Migration105to106_TrnRecurringRules
import com.iris.domain.db.migration.Migration106to107_Wishlist
import com.iris.domain.db.migration.Migration107to108_Sync
import com.iris.domain.db.migration.Migration108to109_Users
import com.iris.domain.db.migration.Migration109to110_PlannedPayments
import com.iris.domain.db.migration.Migration110to111_PlannedPaymentRule
import com.iris.domain.db.migration.Migration111to112_User_testUser
import com.iris.domain.db.migration.Migration112to113_ExchangeRates
import com.iris.domain.db.migration.Migration113to114_Multi_Currency
import com.iris.domain.db.migration.Migration114to115_Category_Account_Icons
import com.iris.domain.db.migration.Migration115to116_Account_Include_In_Balance
import com.iris.domain.db.migration.Migration116to117_SalteEdgeIntgration
import com.iris.domain.db.migration.Migration117to118_Budgets
import com.iris.domain.db.migration.Migration118to119_Loans
import com.iris.domain.db.migration.Migration119to120_LoanTransactions
import com.iris.domain.db.migration.Migration120to121_DropWishlistItem
import com.iris.domain.db.migration.Migration122to123_ExchangeRates
import com.iris.domain.db.migration.Migration125to126_Tags

@Database(
    entities = [
        AccountEntity::class, TransactionEntity::class, CategoryEntity::class,
        SettingsEntity::class, PlannedPaymentRuleEntity::class,
        UserEntity::class, ExchangeRateEntity::class, BudgetEntity::class,
        LoanEntity::class, LoanRecordEntity::class, TagEntity::class, TagAssociationEntity::class,
        SyncChangeLogEntity::class, SyncStateEntity::class
    ],
    autoMigrations = [
        AutoMigration(
            from = 121,
            to = 122,
            spec = IrisRoomDatabase.DeleteSEMigration::class
        )
    ],
    version = 131,
    exportSchema = true
)
@TypeConverters(RoomTypeConverters::class)
abstract class IrisRoomDatabase : RoomDatabase() {
    abstract val accountDao: AccountDao
    abstract val transactionDao: TransactionDao
    abstract val categoryDao: CategoryDao
    abstract val budgetDao: BudgetDao
    abstract val plannedPaymentRuleDao: PlannedPaymentRuleDao
    abstract val settingsDao: SettingsDao
    abstract val userDao: UserDao
    abstract val exchangeRatesDao: ExchangeRatesDao
    abstract val loanDao: LoanDao
    abstract val loanRecordDao: LoanRecordDao
    abstract val tagDao: TagDao
    abstract val tagAssociationDao: TagAssociationDao
    abstract val syncChangeLogDao: SyncChangeLogDao
    abstract val syncStateDao: SyncStateDao

    abstract val writeAccountDao: WriteAccountDao
    abstract val writeTransactionDao: WriteTransactionDao
    abstract val writeCategoryDao: WriteCategoryDao
    abstract val writeBudgetDao: WriteBudgetDao
    abstract val writePlannedPaymentRuleDao: WritePlannedPaymentRuleDao
    abstract val writeSettingsDao: WriteSettingsDao
    abstract val writeExchangeRatesDao: WriteExchangeRatesDao
    abstract val writeLoanDao: WriteLoanDao
    abstract val writeLoanRecordDao: WriteLoanRecordDao
    abstract val writeTagDao: WriteTagDao
    abstract val writeTagAssociationDao: WriteTagAssociationDao
    abstract val writeSyncChangeLogDao: WriteSyncChangeLogDao
    abstract val writeSyncStateDao: WriteSyncStateDao

    companion object {
        const val DB_NAME = "iriswallet.db"

        fun migrations() = arrayOf(
            Migration105to106_TrnRecurringRules(),
            Migration106to107_Wishlist(),
            Migration107to108_Sync(),
            Migration108to109_Users(),
            Migration109to110_PlannedPayments(),
            Migration110to111_PlannedPaymentRule(),
            Migration111to112_User_testUser(),
            Migration112to113_ExchangeRates(),
            Migration113to114_Multi_Currency(),
            Migration114to115_Category_Account_Icons(),
            Migration115to116_Account_Include_In_Balance(),
            Migration116to117_SalteEdgeIntgration(),
            Migration117to118_Budgets(),
            Migration118to119_Loans(),
            Migration119to120_LoanTransactions(),
            Migration120to121_DropWishlistItem(),
            Migration122to123_ExchangeRates(),
            Migration123to124_LoanIncludeDateTime(),
            Migration124to125_LoanEditDateTime(),
            Migration125to126_Tags(),
            Migration126to127_LoanRecordType(),
            Migration127to128_PaidForDateRecord(),
            Migration128to129_DeleteIsDeleted(),
            Migration129to130_LoanIncludeNote(),
            Migration130to131_SyncChangeLog()
        )

        @Suppress("SpreadOperator")
        fun create(applicationContext: Context): IrisRoomDatabase {
            return Room
                .databaseBuilder(
                    applicationContext,
                    IrisRoomDatabase::class.java,
                    DB_NAME
                )
                .addMigrations(*migrations())
                .addCallback(SyncDatabaseCallback())
                .build()
        }
    }

    @DeleteColumn(tableName = "accounts", columnName = "seAccountId")
    @DeleteColumn(tableName = "transactions", columnName = "seTransactionId")
    @DeleteColumn(tableName = "transactions", columnName = "seAutoCategoryId")
    @DeleteColumn(tableName = "categories", columnName = "seCategoryName")
    class DeleteSEMigration : AutoMigrationSpec
}
