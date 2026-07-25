package com.iris.wallet.domain.data

import android.icu.util.Currency
import androidx.compose.runtime.Immutable
import com.iris.legacy.utils.getDefaultFIATCurrency

@Immutable
data class IrisCurrency(
    val code: String,
    val name: String,
    val isCrypto: Boolean
) {
    companion object {
        private const val CRYPTO_DECIMAL = 18
        private const val FIAT_DECIMAL = 2

        private val CRYPTO = setOf(
            IrisCurrency(
                code = "BTC",
                name = "Bitcoin",
                isCrypto = true
            ),
            IrisCurrency(
                code = "ETH",
                name = "Ethereum",
                isCrypto = true
            ),
            IrisCurrency(
                code = "USDT",
                name = "Tether USD",
                isCrypto = true
            ),
            IrisCurrency(
                code = "BNB",
                name = "Binance Coin",
                isCrypto = true
            ),
            IrisCurrency(
                code = "ADA",
                name = "Cardano",
                isCrypto = true
            ),
            IrisCurrency(
                code = "XRP",
                name = "Ripple",
                isCrypto = true
            ),
            IrisCurrency(
                code = "DOGE",
                name = "Dogecoin",
                isCrypto = true
            ),
            IrisCurrency(
                code = "USDC",
                name = "USD Coin",
                isCrypto = true
            ),
            IrisCurrency(
                code = "DOT",
                name = "Polkadot",
                isCrypto = true
            ),
            IrisCurrency(
                code = "UNI",
                name = "Uniswap",
                isCrypto = true
            ),
            IrisCurrency(
                code = "BUSD",
                name = "Binance USD",
                isCrypto = true
            ),
            IrisCurrency(
                code = "BCH",
                name = "Bitcoin Cash",
                isCrypto = true
            ),
            IrisCurrency(
                code = "SOL",
                name = "Solana",
                isCrypto = true
            ),
            IrisCurrency(
                code = "LTC",
                name = "Litecoin",
                isCrypto = true
            ),
            IrisCurrency(
                code = "LINK",
                name = "ChainLink Token",
                isCrypto = true
            ),
            IrisCurrency(
                code = "SHIB",
                name = "Shiba Inu coin",
                isCrypto = true
            ),
            IrisCurrency(
                code = "LUNA",
                name = "Terra",
                isCrypto = true
            ),
            IrisCurrency(
                code = "AVAX",
                name = "Avalanche",
                isCrypto = true
            ),
            IrisCurrency(
                code = "MATIC",
                name = "Polygon",
                isCrypto = true
            ),
            IrisCurrency(
                code = "CRO",
                name = "Cronos",
                isCrypto = true
            ),
            IrisCurrency(
                code = "WBTC",
                name = "Wrapped Bitcoin",
                isCrypto = true
            ),
            IrisCurrency(
                code = "ALGO",
                name = "Algorand",
                isCrypto = true
            ),
            IrisCurrency(
                code = "XLM",
                name = "Stellar",
                isCrypto = true
            ),
            IrisCurrency(
                code = "MANA",
                name = "Decentraland",
                isCrypto = true
            ),
            IrisCurrency(
                code = "AXS",
                name = "Axie Infinity",
                isCrypto = true
            ),
            IrisCurrency(
                code = "DAI",
                name = "Dai",
                isCrypto = true
            ),
            IrisCurrency(
                code = "ICP",
                name = "Internet Computer",
                isCrypto = true
            ),
            IrisCurrency(
                code = "ATOM",
                name = "Cosmos",
                isCrypto = true
            ),
            IrisCurrency(
                code = "FIL",
                name = "Filecoin",
                isCrypto = true
            ),
            IrisCurrency(
                code = "ETC",
                name = "Ethereum Classic",
                isCrypto = true
            ),
            IrisCurrency(
                code = "DASH",
                name = "Dash",
                isCrypto = true
            ),
            IrisCurrency(
                code = "TRX",
                name = "Tron",
                isCrypto = true
            ),
            IrisCurrency(
                code = "TON",
                name = "Tonchain",
                isCrypto = true
            ),
        )

        fun getAvailable(): List<IrisCurrency> {
            return Currency.getAvailableCurrencies()
                .map {
                    IrisCurrency(
                        code = it.currencyCode,
                        name = it.displayName,
                        isCrypto = false
                    )
                }
                .plus(CRYPTO)
        }

        fun fromCode(code: String): IrisCurrency? {
            if (code.isBlank()) return null

            val crypto = CRYPTO.find { it.code == code }
            if (crypto != null) {
                return crypto
            }

            return try {
                val fiat = Currency.getInstance(code)
                IrisCurrency(
                    fiatCurrency = fiat
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        fun getDefault(): IrisCurrency = IrisCurrency(
            fiatCurrency = getDefaultFIATCurrency()
        )

        fun getDecimalPlaces(assetCode: String): Int =
            if (fromCode(assetCode) in CRYPTO) CRYPTO_DECIMAL else FIAT_DECIMAL
    }

    constructor(fiatCurrency: Currency) : this(
        code = fiatCurrency.currencyCode,
        name = fiatCurrency.displayName,
        isCrypto = false
    )
}
