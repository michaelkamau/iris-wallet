package com.iris.domain.usecase.sms

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import arrow.core.Either
import arrow.core.raise.either
import com.iris.data.datastore.DatastoreKeys
import com.iris.data.model.Category
import com.iris.data.model.CategoryId
import com.iris.data.model.primitive.ColorInt
import com.iris.data.model.primitive.IconAsset
import com.iris.data.model.primitive.NotBlankTrimmedString
import com.iris.data.repository.CategoryRepository
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Answers "which category do captured transaction costs go in?", creating it the first time and
 * never again (FR-017).
 *
 * The id is remembered, not the name. That is the whole design: what comes back is an ordinary
 * [Category] the user may rename, recolour or reorder like any other, and the next fee still lands
 * in it because the lookup never mentions the word "Transaction costs" (research.md D11).
 *
 * It is called lazily, on the first fee that actually reaches the ledger — never when the feature
 * is switched on. A user who enables capture and never confirms a fee gets no category they did
 * not ask for.
 */
@Singleton
class EnsureTransactionCostCategoryUseCase @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val categoryRepository: CategoryRepository,
) {

    /**
     * @return the stored category when it still exists, otherwise a freshly created one.
     *   `Left` carries a diagnostic string, never message content (FR-006).
     */
    suspend fun ensure(): Either<String, CategoryId> = either {
        remembered() ?: create().bind()
    }

    /**
     * A stored id that no longer resolves means the user deleted the category. Recreating it is
     * the only honest answer — the alternative is filing a fee against an id that points at
     * nothing, which no report could render.
     */
    private suspend fun remembered(): CategoryId? = dataStore.data.first()
        .get(DatastoreKeys.SMS_TRANSACTION_COST_CATEGORY_ID)
        // A value that is not a UUID cannot have come from here; it is replaced, not crashed on.
        ?.let { raw -> runCatching { CategoryId(UUID.fromString(raw)) }.getOrNull() }
        ?.let { id -> categoryRepository.findById(id)?.id }

    private suspend fun create(): Either<String, CategoryId> = Either
        .catch {
            val category = Category(
                id = CategoryId(UUID.randomUUID()),
                name = NAME,
                color = ColorInt(COLOR),
                icon = ICON,
                orderNum = categoryRepository.findMaxOrderNum() + 1.0,
            )
            categoryRepository.save(category)
            dataStore.edit {
                it[DatastoreKeys.SMS_TRANSACTION_COST_CATEGORY_ID] = category.id.value.toString()
            }
            category.id
        }
        .mapLeft { it::class.simpleName ?: "unknown" }

    private companion object {
        /** The user's to change from the moment it exists; only the id is ever relied on. */
        private val NAME = NotBlankTrimmedString.unsafe("Transaction costs")
        private val ICON = IconAsset.unsafe("bills")

        /** Opaque grey — a fee is not a spending choice, and should not shout like one. */
        private val COLOR = 0xFF6E7B8B.toInt()
    }
}
