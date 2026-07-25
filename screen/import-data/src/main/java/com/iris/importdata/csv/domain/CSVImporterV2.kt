package com.iris.importdata.csv.domain

import androidx.compose.ui.graphics.toArgb
import arrow.core.raise.either
import com.iris.base.legacy.Transaction
import com.iris.base.model.TransactionType
import com.iris.base.time.TimeConverter
import com.iris.data.backup.CSVRow
import com.iris.data.backup.ImportResult
import com.iris.data.db.dao.read.AccountDao
import com.iris.data.db.dao.read.SettingsDao
import com.iris.data.model.Category
import com.iris.data.model.CategoryId
import com.iris.data.model.primitive.ColorInt
import com.iris.data.model.primitive.IconAsset
import com.iris.data.model.primitive.NotBlankTrimmedString
import com.iris.data.repository.AccountRepository
import com.iris.data.repository.CategoryRepository
import com.iris.data.repository.CurrencyRepository
import com.iris.data.repository.TransactionRepository
import com.iris.data.repository.mapper.TransactionMapper
import com.iris.design.IRIS_COLOR_PICKER_COLORS_FREE
import com.iris.importdata.csv.ImportantFields
import com.iris.importdata.csv.OptionalFields
import com.iris.importdata.csv.TransferFields
import com.iris.legacy.datamodel.Account
import com.iris.legacy.datamodel.temp.toLegacyDomain
import com.iris.legacy.datamodel.toEntity
import com.iris.legacy.utils.toLowerCaseLocal
import com.iris.wallet.domain.data.IrisCurrency
import com.iris.wallet.domain.pure.util.nextOrderNum
import com.iris.wallet.ui.theme.Green
import com.iris.wallet.ui.theme.IrisDark
import kotlinx.collections.immutable.toImmutableList
import java.util.UUID
import javax.inject.Inject
import kotlin.math.absoluteValue
import com.iris.importdata.csv.CSVRow as CSVRowNew

class CSVImporterV2 @Inject constructor(
    private val settingsDao: SettingsDao,
    private val transactionRepository: TransactionRepository,
    private val transactionMapper: TransactionMapper,
    private val accountDao: AccountDao,
    private val categoryRepository: CategoryRepository,
    private val currencyRepository: CurrencyRepository,
    private val accountRepository: AccountRepository,
    private val timeConverter: TimeConverter,
) {

    lateinit var accounts: List<Account>
    lateinit var categories: List<Category>

    private var newCategoryColorIndex = 0
    private var newAccountColorIndex = 0

    suspend fun import(
        csv: List<CSVRowNew>,
        importantFields: ImportantFields,
        transferFields: TransferFields,
        optionalFields: OptionalFields,
        onProgress: suspend (progressPercent: Double) -> Unit,
    ): ImportResult {
        val rows = csv.drop(1) // drop the header
        val rowsCount = rows.size

        newCategoryColorIndex = 0
        newAccountColorIndex = 0

        accounts = accountDao.findAll().map { it.toLegacyDomain() }
        val initialAccountsCount = accounts.size

        categories = categoryRepository.findAll()
        val initialCategoriesCount = categories.size

        val baseCurrency = settingsDao.findFirst().currency

        val failedRows = mutableListOf<CSVRow>()

        val transactions = rows.mapIndexedNotNull { index, row ->
            val progressPercent = if (rowsCount > 0) {
                index / rowsCount.toDouble()
            } else {
                0.0
            }
            onProgress(progressPercent / 2)

            val transaction = mapToTransaction(
                baseCurrency = baseCurrency,
                importantFields = importantFields,
                transferFields = transferFields,
                optionalFields = optionalFields,
                row = row,
            )

            if (transaction == null) {
                failedRows.add(
                    CSVRow(
                        index = index + 2, // + 1 because we skip Header and +1 because they don't start from zero
                        content = row.values
                    )
                )
            }
            transaction
        }

        for ((index, transaction) in transactions.withIndex()) {
            val progressPercent = if (rowsCount > 0) {
                index / transactions.size.toDouble()
            } else {
                0.0
            }
            onProgress(0.5 + progressPercent / 2)
            with(transactionMapper) {
                transaction.toEntity().toDomain().getOrNull()?.let {
                    transactionRepository.save(it)
                }
            }
        }

        return ImportResult(
            rowsFound = rowsCount,
            transactionsImported = transactions.size,
            accountsImported = accounts.size - initialAccountsCount,
            categoriesImported = categories.size - initialCategoriesCount,
            failedRows = failedRows.toImmutableList()
        )
    }

    private suspend fun mapToTransaction(
        baseCurrency: String,
        row: CSVRowNew,
        importantFields: ImportantFields,
        transferFields: TransferFields,
        optionalFields: OptionalFields,
    ): Transaction? {
        val type = parseTransactionType(
            value = row.extractValue(importantFields.type),
            metadata = importantFields.type.metadata,
        ) ?: return null

        val toAccount = if (type == TransactionType.TRANSFER) {
            mapAccount(
                baseCurrency = baseCurrency,
                accountNameString = parseToAccount(
                    value = row.extractValue(transferFields.toAccount),
                    metadata = transferFields.toAccount.metadata
                ),
                currencyRawString = parseToAccountCurrency(
                    value = row.extractValue(transferFields.toAccountCurrency),
                    metadata = transferFields.toAccountCurrency.metadata
                ) ?: parseAccountCurrency(
                    value = row.extractValue(importantFields.accountCurrency),
                    metadata = importantFields.accountCurrency.metadata,
                ),
                color = null,
                icon = null,
                orderNum = null,
            )
        } else {
            null
        }

        val csvAmount = if (type != TransactionType.TRANSFER) {
            parseAmount(
                value = row.extractValue(importantFields.amount),
                metadata = importantFields.amount.metadata
            )
        } else {
            parseAmount(
                value = row.extractValue(transferFields.toAmount),
                metadata = transferFields.toAmount.metadata
            )
        } ?: return null
        val amount = csvAmount.absoluteValue

        if (amount <= 0) {
            // Cannot save transactions with zero amount
            return null
        }

        val toAmount = if (type == TransactionType.TRANSFER) {
            parseAmount(
                value = row.extractValue(transferFields.toAmount),
                metadata = transferFields.toAmount.metadata
            )
        } else {
            null
        }

        val dateTime = parseDate(
            row.extractValue(importantFields.date),
            importantFields.date.metadata
        ) ?: return null

        val account = mapAccount(
            baseCurrency = baseCurrency,
            accountNameString = parseAccount(
                value = row.extractValue(importantFields.account),
                metadata = importantFields.account.metadata
            ),
            currencyRawString = parseAccountCurrency(
                value = row.extractValue(importantFields.accountCurrency),
                metadata = importantFields.accountCurrency.metadata
            ),
            color = null,
            icon = null,
            orderNum = null,
        ) ?: return null

        val category = mapCategory(
            categoryNameString = parseCategory(
                value = row.extractValue(optionalFields.category),
                metadata = optionalFields.category.metadata
            ),
            color = null,
            icon = null,
            orderNum = null,
        )
        val title = parseTitle(
            row.extractValue(optionalFields.title),
            optionalFields.title.metadata
        )
        val description = parseTitle(
            row.extractValue(optionalFields.description),
            optionalFields.description.metadata
        )

        return Transaction(
            id = UUID.randomUUID(),
            type = type,
            amount = amount.toBigDecimal(),
            accountId = account.id,
            toAccountId = toAccount?.id,
            toAmount = toAmount?.toBigDecimal() ?: amount.toBigDecimal(),
            dateTime = with(timeConverter) { dateTime.toUTC() },
            dueDate = null,
            categoryId = category?.id?.value,
            title = title,
            description = description
        )
    }

    private suspend fun mapAccount(
        baseCurrency: String,
        accountNameString: String?,
        color: Int?,
        icon: String?,
        orderNum: Double?,
        currencyRawString: String?,
    ): Account? {
        if (accountNameString == null || accountNameString.isBlank()) return null

        val existingAccount = accounts.firstOrNull {
            accountNameString.toLowerCaseLocal() == it.name.toLowerCaseLocal()
        }
        if (existingAccount != null) {
            return existingAccount
        }

        // create new account
        val colorArgb = color ?: when {
            accountNameString.toLowerCaseLocal().contains("cash") -> {
                Green
            }

            accountNameString.toLowerCaseLocal().contains("revolut") -> {
                IrisDark
            }

            else -> IRIS_COLOR_PICKER_COLORS_FREE.getOrElse(newAccountColorIndex++) {
                newAccountColorIndex = 0
                IRIS_COLOR_PICKER_COLORS_FREE.first()
            }
        }.toArgb()

        val newAccount = Account(
            name = accountNameString,
            currency = mapCurrency(
                baseCurrency = baseCurrency,
                currencyCode = currencyRawString
            ),
            color = colorArgb,
            icon = icon,
            orderNum = orderNum ?: accountDao.findMaxOrderNum().nextOrderNum()
        )
        val domainAccount = newAccount.toDomainAccount(currencyRepository).getOrNull()
            ?: return null
        accountRepository.save(domainAccount)
        accounts = accountDao.findAll().map { it.toLegacyDomain() }

        return newAccount
    }

    private fun mapCurrency(
        baseCurrency: String,
        currencyCode: String?
    ): String {
        return try {
            if (currencyCode != null && currencyCode.isNotBlank()) {
                IrisCurrency.fromCode(currencyCode)?.code ?: baseCurrency
            } else {
                baseCurrency
            }
        } catch (e: Exception) {
            baseCurrency
        }
    }

    private suspend fun mapCategory(
        categoryNameString: String?,
        color: Int?,
        icon: String?,
        orderNum: Double?
    ): Category? {
        if (categoryNameString == null || categoryNameString.isBlank()) return null

        val existingCategory = categories.firstOrNull {
            categoryNameString.toLowerCaseLocal() == it.name.value.toLowerCaseLocal()
        }
        if (existingCategory != null) {
            return existingCategory
        }

        // create new category
        val colorArgb = color ?: IRIS_COLOR_PICKER_COLORS_FREE.getOrElse(newCategoryColorIndex++) {
            newCategoryColorIndex = 0
            IRIS_COLOR_PICKER_COLORS_FREE.first()
        }.toArgb()

        val newCategory: Category? = either {
            Category(
                id = CategoryId(UUID.randomUUID()),
                name = NotBlankTrimmedString.from(categoryNameString).bind(),
                color = ColorInt(colorArgb),
                icon = icon?.let(IconAsset::from)?.getOrNull(),
                orderNum = orderNum ?: categoryRepository.findMaxOrderNum().nextOrderNum(),
            )
        }.getOrNull()

        if (newCategory != null) {
            categoryRepository.save(newCategory)
            categories = categoryRepository.findAll()
        }

        return newCategory
    }
}
