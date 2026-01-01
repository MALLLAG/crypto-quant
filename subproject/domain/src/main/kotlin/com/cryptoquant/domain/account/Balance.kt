package com.cryptoquant.domain.account

import arrow.core.raise.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.recover
import com.cryptoquant.domain.common.Amount
import com.cryptoquant.domain.common.AvgBuyPrice
import com.cryptoquant.domain.common.DecimalConfig
import com.cryptoquant.domain.common.DomainError
import com.cryptoquant.domain.common.InvalidBalance
import com.cryptoquant.domain.common.Price
import com.cryptoquant.domain.common.Volume
import java.math.BigDecimal

/**
 * 계정의 특정 통화 잔고를 나타냅니다.
 *
 * 불변식:
 * - locked <= balance (주문에 묶인 수량은 보유 수량을 초과할 수 없음)
 *
 * @property balance 보유 수량 (예: 0.5 BTC)
 * @property locked 주문에 묶인 수량
 * @property avgBuyPrice 평균 매수가 (quote currency 기준, 예: KRW)
 */
@ConsistentCopyVisibility
data class Balance private constructor(
    val currency: Currency,
    val balance: Volume,
    val locked: Volume,
    val avgBuyPrice: AvgBuyPrice,
    val avgBuyPriceModified: Boolean,
) {
    /**
     * 사용 가능한 수량 (balance - locked).
     * 불변식이 보장되므로 항상 0 이상입니다.
     */
    val available: Volume get() = (balance - locked) ?: Volume.ZERO

    companion object {
        context(_: Raise<DomainError>)
        operator fun invoke(
            currency: Currency,
            balance: Volume,
            locked: Volume,
            avgBuyPrice: AvgBuyPrice,
            avgBuyPriceModified: Boolean,
        ): Balance {
            ensure(locked <= balance) {
                InvalidBalance("locked(${locked.value})가 balance(${balance.value})를 초과할 수 없습니다")
            }
            return Balance(currency, balance, locked, avgBuyPrice, avgBuyPriceModified)
        }
    }

    /**
     * 현재 가격 기준 총 평가금액을 계산합니다.
     * Volume * Price = Amount (예: 0.5 BTC * 100,000,000 KRW/BTC = 50,000,000 KRW)
     */
    fun totalValue(currentPrice: Price): Amount {
        val value = balance.value * currentPrice.value
        return recover({ Amount(value) }) { Amount.ZERO }
    }

    /**
     * 현재 가격 기준 평가손익을 계산합니다.
     * (현재가 - 평균매수가) * 수량
     */
    fun profitLoss(currentPrice: Price): BigDecimal {
        val currentValue = balance.value * currentPrice.value
        val buyValue = balance.value * avgBuyPrice.value
        return currentValue - buyValue
    }

    /**
     * 현재 가격 기준 수익률(%)을 계산합니다.
     * ((현재가 - 평균매수가) / 평균매수가) * 100
     */
    fun profitLossRate(currentPrice: Price): BigDecimal {
        if (avgBuyPrice.isZero) return BigDecimal.ZERO
        return (currentPrice.value - avgBuyPrice.value)
            .divide(avgBuyPrice.value, DecimalConfig.PERCENT_SCALE + 2, DecimalConfig.ROUNDING_MODE)
            .multiply(BigDecimal(100))
            .setScale(DecimalConfig.PERCENT_SCALE, DecimalConfig.ROUNDING_MODE)
    }
}
