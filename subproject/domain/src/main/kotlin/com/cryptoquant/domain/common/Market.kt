package com.cryptoquant.domain.common

import arrow.core.raise.Raise
import arrow.core.raise.context.raise

enum class Market {
    KRW,
    BTC,
    USDT,
    ;

    companion object {
        context(_: Raise<DomainError>)
        fun from(value: String): Market {
            return entries.find { it.name == value.uppercase() }
                ?: raise(InvalidMarket("지원하지 않는 마켓: $value"))
        }
    }
}
