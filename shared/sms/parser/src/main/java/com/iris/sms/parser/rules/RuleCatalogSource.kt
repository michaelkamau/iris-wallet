package com.iris.sms.parser.rules

import com.iris.sms.parser.model.SenderRuleSet
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The seam behind which the rule catalog lives.
 *
 * Only [CompiledRuleCatalogSource] ships in this feature; the interface exists now so a future
 * asset-backed, updatable catalog is an additive change (research.md D2).
 */
interface RuleCatalogSource {
    fun ruleSets(): ImmutableList<SenderRuleSet>
}

/**
 * The rule sets compiled into the app, contributed one `@Provides @IntoSet` at a time.
 *
 * Ordered by id so sender resolution is deterministic no matter how the multibinding set is
 * iterated.
 */
@Singleton
class CompiledRuleCatalogSource @Inject constructor(
    ruleSets: Set<@JvmSuppressWildcards SenderRuleSet>,
) : RuleCatalogSource {

    private val ordered: ImmutableList<SenderRuleSet> =
        ruleSets.sortedBy { it.id.value }.toImmutableList()

    override fun ruleSets(): ImmutableList<SenderRuleSet> = ordered
}
