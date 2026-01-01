package com.cryptoquant.domain.common

import arrow.core.raise.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.raise
import java.math.BigDecimal

@JvmInline
value class FeeRate private constructor(val value: BigDecimal) {
    fun toPercent(): BigDecimal = value * BigDecimal(100)

    companion object {
        context(_: Raise<DomainError>)
        operator fun invoke(value: BigDecimal): FeeRate {
            ensure(value >= BigDecimal.ZERO) { InvalidFeeRate("수수료율은 0 이상이어야 합니다: $value") }
            ensure(value <= BigDecimal.ONE) { InvalidFeeRate("수수료율은 1 이하여야 합니다: $value") }
            return FeeRate(value)
        }

        context(_: Raise<DomainError>)
        operator fun invoke(value: String): FeeRate = invoke(
            value.toBigDecimalOrNull()
                ?: raise(InvalidFeeRate("숫자 형식이 아닙니다: $value")),
        )

        @Suppress("MemberVisibilityCanBePrivate")
        val DEFAULT: FeeRate = FeeRate(BigDecimal("0.0005"))
    }
}
