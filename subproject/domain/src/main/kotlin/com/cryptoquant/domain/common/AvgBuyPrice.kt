package com.cryptoquant.domain.common

import arrow.core.raise.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.raise
import java.math.BigDecimal

/**
 * 평균 매수가.
 *
 * Price와 달리 0을 허용합니다 (아직 매수한 적 없는 경우).
 */
@JvmInline
value class AvgBuyPrice private constructor(val value: BigDecimal) {
    val isZero: Boolean get() = value.compareTo(BigDecimal.ZERO) == 0

    companion object {
        val ZERO: AvgBuyPrice = AvgBuyPrice(BigDecimal.ZERO)

        context(_: Raise<DomainError>)
        operator fun invoke(value: BigDecimal): AvgBuyPrice {
            ensure(value >= BigDecimal.ZERO) { InvalidAvgBuyPrice("평균 매수가는 0 이상이어야 합니다: $value") }
            return AvgBuyPrice(value)
        }

        context(_: Raise<DomainError>)
        operator fun invoke(value: String): AvgBuyPrice = invoke(
            value.toBigDecimalOrNull()
                ?: raise(InvalidAvgBuyPrice("숫자 형식이 아닙니다: $value")),
        )
    }
}
