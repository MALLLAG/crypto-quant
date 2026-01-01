package com.cryptoquant.domain.gateway

import com.cryptoquant.domain.common.TradingPair
import com.cryptoquant.domain.order.OrderEvent
import com.cryptoquant.domain.quotation.Orderbook
import com.cryptoquant.domain.quotation.Ticker
import com.cryptoquant.domain.quotation.Trade
import kotlinx.coroutines.flow.Flow

/**
 * 실시간 데이터 스트림.
 *
 * WebSocket을 통한 실시간 데이터 수신 인터페이스입니다.
 * 각 메서드는 무한 Flow를 반환하며, 연결이 끊어지면 재연결을 시도합니다.
 */
interface RealtimeStream {

    /**
     * 실시간 현재가(Ticker) 구독.
     *
     * @param pairs 구독할 마켓 목록
     * @return 현재가 Flow
     */
    fun subscribeTicker(pairs: List<TradingPair>): Flow<Ticker>

    /**
     * 실시간 호가창 구독.
     *
     * @param pairs 구독할 마켓 목록
     * @return 호가창 Flow
     */
    fun subscribeOrderbook(pairs: List<TradingPair>): Flow<Orderbook>

    /**
     * 실시간 체결 내역 구독.
     *
     * @param pairs 구독할 마켓 목록
     * @return 체결 내역 Flow
     */
    fun subscribeTrade(pairs: List<TradingPair>): Flow<Trade>

    /**
     * 내 주문 이벤트 구독.
     *
     * 인증이 필요하며, 본인의 주문 체결/취소 등 이벤트를 수신합니다.
     *
     * @return 주문 이벤트 Flow
     */
    fun subscribeMyOrder(): Flow<OrderEvent>
}
