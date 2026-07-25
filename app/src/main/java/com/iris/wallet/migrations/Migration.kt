package com.iris.wallet.migrations

interface Migration {
    val key: String

    suspend fun migrate()
}
