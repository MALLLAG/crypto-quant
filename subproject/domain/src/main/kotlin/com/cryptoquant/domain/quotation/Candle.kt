package com.cryptoquant.domain.quotation

import arrow.core.raise.Raise
import arrow.core.raise.context.ensure
import com.cryptoquant.domain.common.Amount
import com.cryptoquant.domain.common.DomainError
import com.cryptoquant.domain.common.InvalidCandle
import com.cryptoquant.domain.common.Price
import com.cryptoquant.domain.common.TradingPair
import com.cryptoquant.domain.common.Volume
import java.time.Instant

/**
 * 캔들 (OHLCV 데이터).
 *
 * 불변식:
 * - 고가 >= 저가
 * - 고가 >= 시가
 * - 고가 >= 종가
 * - 저가 <= 시가
 * - 저가 <= 종가
 */
@ConsistentCopyVisibility
data class Candle private constructor(
    val pair: TradingPair,
    val unit: CandleUnit,
    val timestamp: Instant,
    val openingPrice: Price,
    val highPrice: Price,
    val lowPrice: Price,
    val closingPrice: Price,
    val volume: Volume,
    val amount: Amount,
) {
    companion object {
        context(_: Raise<DomainError>)
        operator fun invoke(
            pair: TradingPair,
            unit: CandleUnit,
            timestamp: Instant,
            openingPrice: Price,
            highPrice: Price,
            lowPrice: Price,
            closingPrice: Price,
            volume: Volume,
            amount: Amount,
        ): Candle {
            ensure(highPrice.value >= lowPrice.value) {
                InvalidCandle("고가는 저가보다 크거나 같아야 합니다")
            }
            ensure(highPrice.value >= openingPrice.value) {
                InvalidCandle("고가는 시가보다 크거나 같아야 합니다")
            }
            ensure(highPrice.value >= closingPrice.value) {
                InvalidCandle("고가는 종가보다 크거나 같아야 합니다")
            }
            ensure(lowPrice.value <= openingPrice.value) {
                InvalidCandle("저가는 시가보다 작거나 같아야 합니다")
            }
            ensure(lowPrice.value <= closingPrice.value) {
                InvalidCandle("저가는 종가보다 작거나 같아야 합니다")
            }
            return Candle(pair, unit, timestamp, openingPrice, highPrice, lowPrice, closingPrice, volume, amount)
        }
    }
}
