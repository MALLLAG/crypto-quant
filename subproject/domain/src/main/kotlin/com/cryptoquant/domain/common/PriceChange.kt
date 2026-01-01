package com.cryptoquant.domain.common

import arrow.core.raise.Raise
import arrow.core.raise.context.raise
import java.math.BigDecimal

/**
 * 가격 변동량 (음수 허용).
 *
 * 전일 대비 가격 변화량을 나타냅니다.
 * - 양수: 상승
 * - 음수: 하락
 * - 0: 보합
 */
@JvmInline
value class PriceChange(val value: BigDecimal) {
    val isPositive: Boolean get() = value > BigDecimal.ZERO
    val isNegative: Boolean get() = value < BigDecimal.ZERO
    val isZero: Boolean get() = value.compareTo(BigDecimal.ZERO) == 0

    fun abs(): BigDecimal = value.abs()

    companion object {
        val ZERO: PriceChange = PriceChange(BigDecimal.ZERO)

        operator fun invoke(value: BigDecimal): PriceChange = PriceChange(value)

        context(_: Raise<DomainError>)
        operator fun invoke(value: String): PriceChange = PriceChange(
            value.toBigDecimalOrNull()
                ?: raise(InvalidPriceChange("숫자 형식이 아닙니다: $value")),
        )
    }
}
