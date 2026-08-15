package com.iris.data.di

import android.content.Context
import com.iris.data.db.IrisRoomDatabase
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
import com.iris.data.db.dao.read.TagAssociationDao
import com.iris.data.db.dao.read.TagDao
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
import com.iris.data.db.dao.write.WriteTagAssociationDao
import com.iris.data.db.dao.write.WriteTagDao
import com.iris.data.db.dao.write.WriteTransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RoomDbModule {

    @Provides
    @Singleton
    fun provideIrisRoomDatabase(
        @ApplicationContext appContext: Context,
    ): IrisRoomDatabase {
        return IrisRoomDatabase.create(
            applicationContext = appContext,
        )
    }

    @Provides
    fun provideUserDao(db: IrisRoomDatabase): UserDao {
        return db.userDao
    }

    @Provides
    fun provideAccountDao(db: IrisRoomDatabase): AccountDao {
        return db.accountDao
    }

    @Provides
    fun provideTransactionDao(db: IrisRoomDatabase): TransactionDao {
        return db.transactionDao
    }

    @Provides
    fun provideCategoryDao(db: IrisRoomDatabase): CategoryDao {
        return db.categoryDao
    }

    @Provides
    fun provideBudgetDao(db: IrisRoomDatabase): BudgetDao {
        return db.budgetDao
    }

    @Provides
    fun provideSettingsDao(db: IrisRoomDatabase): SettingsDao {
        return db.settingsDao
    }

    @Provides
    fun provideLoanDao(db: IrisRoomDatabase): LoanDao {
        return db.loanDao
    }

    @Provides
    fun provideLoanRecordDao(db: IrisRoomDatabase): LoanRecordDao {
        return db.loanRecordDao
    }

    @Provides
    fun providePlannedPaymentRuleDao(db: IrisRoomDatabase): PlannedPaymentRuleDao {
        return db.plannedPaymentRuleDao
    }

    @Provides
    fun provideTagDao(db: IrisRoomDatabase): TagDao {
        return db.tagDao
    }

    @Provides
    fun provideTagAssociationDao(db: IrisRoomDatabase): TagAssociationDao {
        return db.tagAssociationDao
    }

    @Provides
    fun provideExchangeRatesDao(
        roomDatabase: IrisRoomDatabase
    ): ExchangeRatesDao {
        return roomDatabase.exchangeRatesDao
    }

    @Provides
    fun provideWriteAccountDao(db: IrisRoomDatabase): WriteAccountDao {
        return db.writeAccountDao
    }

    @Provides
    fun provideWriteTransactionDao(db: IrisRoomDatabase): WriteTransactionDao {
        return db.writeTransactionDao
    }

    @Provides
    fun provideWriteCategoryDao(db: IrisRoomDatabase): WriteCategoryDao {
        return db.writeCategoryDao
    }

    @Provides
    fun provideWriteBudgetDao(db: IrisRoomDatabase): WriteBudgetDao {
        return db.writeBudgetDao
    }

    @Provides
    fun provideWriteSettingsDao(db: IrisRoomDatabase): WriteSettingsDao {
        return db.writeSettingsDao
    }

    @Provides
    fun provideWriteLoanDao(db: IrisRoomDatabase): WriteLoanDao {
        return db.writeLoanDao
    }

    @Provides
    fun provideWriteLoanRecordDao(db: IrisRoomDatabase): WriteLoanRecordDao {
        return db.writeLoanRecordDao
    }

    @Provides
    fun provideWritePlannedPaymentRuleDao(db: IrisRoomDatabase): WritePlannedPaymentRuleDao {
        return db.writePlannedPaymentRuleDao
    }

    @Provides
    fun provideWriteExchangeRatesDao(db: IrisRoomDatabase): WriteExchangeRatesDao {
        return db.writeExchangeRatesDao
    }

    @Provides
    fun provideWriteTagDao(db: IrisRoomDatabase): WriteTagDao {
        return db.writeTagDao
    }

    @Provides
    fun provideWriteTagAssociationDao(db: IrisRoomDatabase): WriteTagAssociationDao {
        return db.writeTagAssociationDao
    }

    @Provides
    fun provideSyncChangeLogDao(db: IrisRoomDatabase): SyncChangeLogDao {
        return db.syncChangeLogDao
    }

    @Provides
    fun provideSyncStateDao(db: IrisRoomDatabase): SyncStateDao {
        return db.syncStateDao
    }

    @Provides
    fun provideWriteSyncChangeLogDao(db: IrisRoomDatabase): WriteSyncChangeLogDao {
        return db.writeSyncChangeLogDao
    }

    @Provides
    fun provideWriteSyncStateDao(db: IrisRoomDatabase): WriteSyncStateDao {
        return db.writeSyncStateDao
    }
}
