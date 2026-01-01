package com.cryptoquant.domain.common

import arrow.core.raise.Raise
import arrow.core.raise.context.ensure

@ConsistentCopyVisibility
data class TradingPair private constructor(
    val market: Market,
    val ticker: String,
) {
    val value: String get() = "${market.name}-$ticker"

    companion object {
        private val TICKER_REGEX = Regex("^[A-Z0-9]+$")

        context(_: Raise<DomainError>)
        operator fun invoke(value: String): TradingPair {
            ensure(value.contains("-")) { InvalidTradingPair("페어 형식이 올바르지 않습니다: $value") }
            val parts = value.uppercase().split("-", limit = 2)
            val market = Market.from(parts[0])
            val ticker = parts[1]
            ensure(ticker.isNotBlank()) { InvalidTradingPair("티커가 비어있습니다") }
            ensure(!ticker.contains("-")) { InvalidTradingPair("티커에 '-'가 포함될 수 없습니다: $ticker") }
            ensure(TICKER_REGEX.matches(ticker)) { InvalidTradingPair("티커는 영문과 숫자만 허용됩니다: $ticker") }
            return TradingPair(market, ticker)
        }

        context(_: Raise<DomainError>)
        operator fun invoke(market: Market, ticker: String): TradingPair {
            val upperTicker = ticker.uppercase()
            ensure(upperTicker.isNotBlank()) { InvalidTradingPair("티커가 비어있습니다") }
            ensure(!upperTicker.contains("-")) { InvalidTradingPair("티커에 '-'가 포함될 수 없습니다: $upperTicker") }
            ensure(TICKER_REGEX.matches(upperTicker)) { InvalidTradingPair("티커는 영문과 숫자만 허용됩니다: $upperTicker") }
            return TradingPair(market, upperTicker)
        }
    }
}
