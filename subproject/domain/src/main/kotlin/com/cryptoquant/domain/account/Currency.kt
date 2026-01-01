package com.cryptoquant.domain.account

import arrow.core.raise.Raise
import arrow.core.raise.context.ensure
import com.cryptoquant.domain.common.DomainError
import com.cryptoquant.domain.common.InvalidCurrency

/**
 * 통화 코드.
 *
 * 업비트에서 사용하는 통화 코드입니다.
 */
@JvmInline
value class Currency private constructor(val value: String) {
    companion object {
        context(_: Raise<DomainError>)
        operator fun invoke(value: String): Currency {
            ensure(value.isNotBlank()) { InvalidCurrency("통화 코드는 비어있을 수 없습니다") }
            return Currency(value.uppercase())
        }

        val KRW = Currency("KRW")
        val BTC = Currency("BTC")
        val USDT = Currency("USDT")
    }
}
