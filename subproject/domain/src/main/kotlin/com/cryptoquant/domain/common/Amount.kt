package com.cryptoquant.domain.common

import arrow.core.raise.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.raise
import java.math.BigDecimal

@JvmInline
value class Amount private constructor(val value: BigDecimal) {
    val isZero: Boolean get() = value.compareTo(BigDecimal.ZERO) == 0
    val isPositive: Boolean get() = value > BigDecimal.ZERO

    operator fun plus(other: Amount): Amount = Amount(value + other.value)

    operator fun minus(other: Amount): Amount? {
        val result = value - other.value
        return if (result >= BigDecimal.ZERO) Amount(result) else null
    }

    operator fun compareTo(other: Amount): Int = value.compareTo(other.value)

    companion object {
        val ZERO: Amount = Amount(BigDecimal.ZERO)

        context(_: Raise<DomainError>)
        operator fun invoke(value: BigDecimal): Amount {
            ensure(value >= BigDecimal.ZERO) { InvalidAmount("금액은 0 이상이어야 합니다: $value") }
            return Amount(value)
        }

        context(_: Raise<DomainError>)
        operator fun invoke(value: String): Amount = invoke(
            value.toBigDecimalOrNull()
                ?: raise(InvalidAmount("숫자 형식이 아닙니다: $value")),
        )
    }
}
