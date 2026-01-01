package com.cryptoquant.domain.quotation

import com.cryptoquant.domain.common.Price
import com.cryptoquant.domain.common.TradeSequentialId
import com.cryptoquant.domain.common.TradingPair
import com.cryptoquant.domain.common.Volume
import java.time.Instant

/**
 * 체결 내역.
 *
 * 업비트 시세 API의 체결 내역(trades) 응답을 나타냅니다.
 *
 * @property sequentialId 체결 순서 ID (페이징 조회에 사용)
 * @property askBid 체결 방향 (ASK: 매도 체결, BID: 매수 체결)
 */
data class Trade(
    val pair: TradingPair,
    val tradePrice: Price,
    val tradeVolume: Volume,
    val askBid: AskBid,
    val prevClosingPrice: Price,
    val change: Change,
    val timestamp: Instant,
    val sequentialId: TradeSequentialId,
)

/**
 * 체결 방향.
 *
 * 체결 내역에서 해당 체결이 매수 주문에 의한 것인지 매도 주문에 의한 것인지를 나타냅니다.
 */
enum class AskBid {
    /** 매도 체결 (매도 주문이 체결됨) */
    ASK,

    /** 매수 체결 (매수 주문이 체결됨) */
    BID,
}
