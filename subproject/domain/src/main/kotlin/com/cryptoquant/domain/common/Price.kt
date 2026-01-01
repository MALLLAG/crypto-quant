package com.cryptoquant.domain.common

import arrow.core.raise.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.raise
import java.math.BigDecimal
import java.math.RoundingMode

@JvmInline
value class Price private constructor(val value: BigDecimal) {
    companion object {
        context(_: Raise<DomainError>)
        operator fun invoke(value: BigDecimal): Price {
            ensure(value > BigDecimal.ZERO) { InvalidPrice("가격은 0보다 커야 합니다") }
            return Price(value)
        }

        context(_: Raise<DomainError>)
        operator fun invoke(value: String): Price = invoke(
            value.toBigDecimalOrNull()
                ?: raise(InvalidPrice("숫자 형식이 아닙니다: $value")),
        )
    }

    context(_: Raise<DomainError>)
    fun adjustToTickSize(tickSize: TickSize): Price {
        val quotient = value.divide(tickSize.value, 0, RoundingMode.DOWN)
        val adjusted = quotient.multiply(tickSize.value)
        return if (adjusted > BigDecimal.ZERO) {
            Price(adjusted)
        } else {
            Price(tickSize.value)
        }
    }

    context(_: Raise<DomainError>)
    fun validateTickSize(tickSize: TickSize) {
        val remainder = value.remainder(tickSize.value)
        ensure(remainder.compareTo(BigDecimal.ZERO) == 0) {
            InvalidTickSize("가격 $value 은(는) 호가단위 ${tickSize.value}에 맞지 않습니다")
        }
    }
}
