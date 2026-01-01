package com.cryptoquant.domain.quotation

import com.cryptoquant.domain.common.Price
import com.cryptoquant.domain.common.TradingPair
import com.cryptoquant.domain.common.Volume
import java.math.BigDecimal
import java.time.Instant

/**
 * 호가창 데이터.
 *
 * 참고: 업비트 API는 이미 정렬된 호가 데이터를 반환합니다.
 * - orderbookUnits[0]: 최우선 호가 (best ask/bid)
 * - ask: 오름차순 (낮은 가격이 best)
 * - bid: 내림차순 (높은 가격이 best)
 */
data class Orderbook(
    val pair: TradingPair,
    val timestamp: Instant,
    val totalAskSize: Volume,
    val totalBidSize: Volume,
    val orderbookUnits: List<OrderbookUnit>,
) {
    /** 최우선 매수 호가 (가장 높은 매수가) */
    val bestBidPrice: Price?
        get() = orderbookUnits.maxByOrNull { it.bidPrice.value }?.bidPrice

    /** 최우선 매도 호가 (가장 낮은 매도가) */
    val bestAskPrice: Price?
        get() = orderbookUnits.minByOrNull { it.askPrice.value }?.askPrice

    /** 스프레드 (최우선 매도가 - 최우선 매수가) */
    fun spread(): BigDecimal? {
        val ask = bestAskPrice?.value ?: return null
        val bid = bestBidPrice?.value ?: return null
        return ask - bid
    }
}
