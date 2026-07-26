package com.iris.sms.parser.rules

import com.iris.sms.parser.model.SenderRuleSet
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ElementsIntoSet
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/**
 * The only wiring a new provider needs: one `@Provides @IntoSet fun xyz(): SenderRuleSet` line
 * beside the others. The engine is never edited (research.md D2).
 *
 * ```
 * @Provides @IntoSet fun mpesa(): SenderRuleSet = MpesaRules.ruleSet
 * ```
 */
@Module
@InstallIn(SingletonComponent::class)
object SmsRuleCatalogModule {

    @Provides
    @Singleton
    fun provideRuleCatalogSource(source: CompiledRuleCatalogSource): RuleCatalogSource = source

    /**
     * Seeds the multibinding so the catalog is injectable before any provider ships. Contributed
     * rule sets are added on top of it, never in place of it.
     */
    @Provides
    @ElementsIntoSet
    fun provideNoRuleSets(): Set<SenderRuleSet> = emptySet()

    @Provides
    @IntoSet
    fun mpesa(): SenderRuleSet = MpesaRules.ruleSet

    @Provides
    @IntoSet
    fun dtb(): SenderRuleSet = DtbRules.ruleSet

    @Provides
    @IntoSet
    fun kcb(): SenderRuleSet = KcbRules.ruleSet
}
