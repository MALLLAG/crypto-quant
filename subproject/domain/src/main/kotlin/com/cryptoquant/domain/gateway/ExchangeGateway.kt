package com.cryptoquant.domain.gateway

import arrow.core.raise.Raise
import com.cryptoquant.domain.account.Balance
import com.cryptoquant.domain.account.OrderChance
import com.cryptoquant.domain.common.TradingPair
import com.cryptoquant.domain.order.Order
import com.cryptoquant.domain.order.OrderId
import com.cryptoquant.domain.order.ValidatedOrderRequest

/**
 * 거래소 게이트웨이.
 *
 * 주문, 잔고 조회 등 인증이 필요한 거래소 API와의 인터페이스입니다.
 * 인프라 계층에서 구현체를 제공합니다 (Hexagonal Architecture).
 */
interface ExchangeGateway {

    /**
     * 주문을 제출합니다.
     *
     * @param request 검증된 주문 요청
     * @return 생성된 주문
     */
    context(_: Raise<GatewayError>)
    suspend fun placeOrder(request: ValidatedOrderRequest): Order

    /**
     * 주문을 취소합니다.
     *
     * @param orderId 취소할 주문 ID
     * @return 취소된 주문
     */
    context(_: Raise<GatewayError>)
    suspend fun cancelOrder(orderId: OrderId): Order

    /**
     * 개별 주문을 조회합니다.
     *
     * @param orderId 조회할 주문 ID
     * @return 주문 정보
     */
    context(_: Raise<GatewayError>)
    suspend fun getOrder(orderId: OrderId): Order

    /**
     * 미체결 주문 목록을 조회합니다.
     *
     * @param pair 조회할 마켓 (null이면 전체)
     * @param page 페이지네이션 파라미터
     * @return 미체결 주문 목록
     */
    context(_: Raise<GatewayError>)
    suspend fun getOpenOrders(
        pair: TradingPair? = null,
        page: PageRequest = PageRequest(),
    ): PageResponse<Order>

    /**
     * 전체 잔고를 조회합니다.
     *
     * @return 잔고 목록
     */
    context(_: Raise<GatewayError>)
    suspend fun getBalances(): List<Balance>

    /**
     * 특정 마켓의 주문 가능 정보를 조회합니다.
     *
     * @param pair 조회할 마켓
     * @return 주문 가능 정보 (수수료율, 최소 주문금액 등)
     */
    context(_: Raise<GatewayError>)
    suspend fun getOrderChance(pair: TradingPair): OrderChance
}
