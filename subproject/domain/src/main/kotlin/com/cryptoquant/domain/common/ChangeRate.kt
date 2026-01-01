package com.cryptoquant.domain.common

import arrow.core.raise.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.raise
import java.math.BigDecimal

/**
 * 변동률 (음수 허용).
 *
 * 비율을 나타내며 -1.0 ~ 무제한 범위입니다.
 * (예: 0.05 = 5% 상승, -0.03 = 3% 하락)
 *
 * 참고: 상승률은 이론상 무제한이지만, 하락률은 -100%(-1.0)가 최대입니다.
 */
@JvmInline
value class ChangeRate private constructor(val value: BigDecimal) {
    val isPositive: Boolean get() = value > BigDecimal.ZERO
    val isNegative: Boolean get() = value < BigDecimal.ZERO
    val isZero: Boolean get() = value.compareTo(BigDecimal.ZERO) == 0

    /** 퍼센트로 변환 (예: 0.05 → 5.00) */
    fun toPercent(): BigDecimal = value
        .multiply(BigDecimal(100))
        .setScale(DecimalConfig.PERCENT_SCALE, DecimalConfig.ROUNDING_MODE)

    companion object {
        val ZERO: ChangeRate = ChangeRate(BigDecimal.ZERO)

        context(_: Raise<DomainError>)
        operator fun invoke(value: BigDecimal): ChangeRate {
            ensure(value >= BigDecimal("-1")) {
                InvalidChangeRate("변동률은 -100% 이상이어야 합니다: $value")
            }
            return ChangeRate(value)
        }

        context(_: Raise<DomainError>)
        operator fun invoke(value: String): ChangeRate = invoke(
            value.toBigDecimalOrNull()
                ?: raise(InvalidChangeRate("숫자 형식이 아닙니다: $value")),
        )
    }
}
