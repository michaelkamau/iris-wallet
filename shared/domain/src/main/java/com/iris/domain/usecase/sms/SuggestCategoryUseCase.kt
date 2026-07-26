package com.iris.domain.usecase.sms

import com.iris.data.model.CategoryId
import com.iris.data.model.primitive.NotBlankTrimmedString
import com.iris.data.repository.CounterpartyCategoryRepository
import com.iris.sms.parser.primitive.CounterpartyNormalizer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Answers "what did the user file this payee under last time?" and nothing more (FR-024).
 *
 * A null is the honest answer for a counterparty nobody has categorised yet. Guessing — nearest
 * name, most-used category, anything inferred from the amount — would put a category the user
 * never chose in front of them pre-selected, which is worse than asking: a wrong suggestion that
 * looks like a decision gets confirmed without being read.
 *
 * The lookup goes through [CounterpartyNormalizer.key] rather than the raw name, so
 * `"Frank Inn Kikuyu"` and `"FRANK INN KIKUYU"` are one memory rather than two.
 */
@Singleton
class SuggestCategoryUseCase @Inject constructor(
    private val counterpartyNormalizer: CounterpartyNormalizer,
    private val counterpartyCategoryRepository: CounterpartyCategoryRepository,
) {

    /**
     * @param counterparty null for a message that named nobody; there is nothing to remember
     *   against, so nothing to suggest.
     */
    suspend fun suggest(counterparty: NotBlankTrimmedString?): CategoryId? =
        // A name that is pure punctuation has no key. Not an error worth surfacing: the user is
        // simply asked to choose, exactly as they would for a payee they have never seen.
        counterparty
            ?.let { counterpartyNormalizer.key(it).getOrNull() }
            ?.let { counterpartyCategoryRepository.findCategory(it) }
}
