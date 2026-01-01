package com.cryptoquant.domain.common

import arrow.core.raise.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.raise
import java.math.BigDecimal

@JvmInline
value class Volume private constructor(val value: BigDecimal) {
    val isZero: Boolean get() = value.compareTo(BigDecimal.ZERO) == 0
    val isPositive: Boolean get() = value > BigDecimal.ZERO

    operator fun plus(other: Volume): Volume = Volume(value + other.value)

    operator fun minus(other: Volume): Volume? {
        val result = value - other.value
        return if (result >= BigDecimal.ZERO) Volume(result) else null
    }

    operator fun compareTo(other: Volume): Int = value.compareTo(other.value)

    companion object {
        val ZERO: Volume = Volume(BigDecimal.ZERO)

        context(_: Raise<DomainError>)
        operator fun invoke(value: BigDecimal): Volume {
            ensure(value >= BigDecimal.ZERO) { InvalidVolume("수량은 0 이상이어야 합니다") }
            ensure(value.scale() <= 8) { InvalidVolume("소수점 8자리까지만 허용됩니다") }
            return Volume(value)
        }

        context(_: Raise<DomainError>)
        operator fun invoke(value: String): Volume = invoke(
            value.toBigDecimalOrNull()
                ?: raise(InvalidVolume("숫자 형식이 아닙니다: $value")),
        )
    }
}
