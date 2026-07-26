package com.iris.sms.parser

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the engine to its interface.
 *
 * Kept apart from `SmsRuleCatalogModule`, which is about *what* the parser knows: this is about
 * *which* parser there is. Callers depend on [SmsParser] alone so the engine can be replaced —
 * by an asset-backed or remote-rules implementation — without touching a single call site.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SmsParserModule {

    @Binds
    @Singleton
    abstract fun bindSmsParser(impl: RuleDrivenSmsParser): SmsParser
}
