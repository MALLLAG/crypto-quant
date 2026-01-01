package com.cryptoquant.domain.gateway

import arrow.core.raise.Raise
import com.cryptoquant.domain.common.TradeSequentialId
import com.cryptoquant.domain.common.TradingPair
import com.cryptoquant.domain.quotation.Candle
import com.cryptoquant.domain.quotation.CandleUnit
import com.cryptoquant.domain.quotation.Orderbook
import com.cryptoquant.domain.quotation.Ticker
import com.cryptoquant.domain.quotation.Trade
import java.time.Instant

/**
 * 시세 게이트웨이.
 *
 * 캔들, 호가, 체결 내역 등 시세 조회 API와의 인터페이스입니다.
 * 인증이 필요하지 않은 공개 API입니다.
 */
interface QuotationGateway {

    /**
     * 캔들 조회.
     *
     * @param pair 마켓
     * @param unit 캔들 단위 (Seconds는 WebSocket만 지원)
     * @param count 조회 개수 (최대 200)
     * @param to 마지막 캔들 시각 (null이면 최신)
     * @return 캔들 목록 (최신순)
     */
    context(_: Raise<GatewayError>)
    suspend fun getCandles(
        pair: TradingPair,
        unit: CandleUnit,
        count: Int = 200,
        to: Instant? = null,
    ): List<Candle>

    /**
     * 현재가(Ticker) 조회.
     *
     * @param pairs 조회할 마켓 목록
     * @return 현재가 목록
     */
    context(_: Raise<GatewayError>)
    suspend fun getTicker(pairs: List<TradingPair>): List<Ticker>

    /**
     * 호가창 조회.
     *
     * @param pairs 조회할 마켓 목록
     * @return 호가창 목록
     */
    context(_: Raise<GatewayError>)
    suspend fun getOrderbook(pairs: List<TradingPair>): List<Orderbook>

    /**
     * 체결 내역 조회.
     *
     * @param pair 마켓
     * @param count 조회 개수 (최대 500)
     * @param cursor 페이지 커서 (sequentialId 기준)
     * @return 체결 내역 목록
     */
    context(_: Raise<GatewayError>)
    suspend fun getTrades(
        pair: TradingPair,
        count: Int = 100,
        cursor: TradeSequentialId? = null,
    ): List<Trade>
}
