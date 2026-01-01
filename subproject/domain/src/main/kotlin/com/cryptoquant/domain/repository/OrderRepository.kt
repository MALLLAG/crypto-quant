package com.cryptoquant.domain.repository

import com.cryptoquant.domain.common.TradingPair
import com.cryptoquant.domain.gateway.PageRequest
import com.cryptoquant.domain.gateway.PageResponse
import com.cryptoquant.domain.order.Order
import com.cryptoquant.domain.order.OrderId

/**
 * 주문 저장소 인터페이스.
 *
 * 주문 엔티티의 영속화를 위한 저장소 인터페이스입니다.
 * 인프라 계층에서 구현체를 제공합니다.
 */
interface OrderRepository {

    /**
     * 주문을 저장합니다.
     *
     * 새 주문이면 삽입, 기존 주문이면 업데이트합니다.
     *
     * @param order 저장할 주문
     */
    suspend fun save(order: Order)

    /**
     * 주문 ID로 주문을 조회합니다.
     *
     * @param orderId 조회할 주문 ID
     * @return 주문 (없으면 null)
     */
    suspend fun findById(orderId: OrderId): Order?

    /**
     * 미체결 주문 목록을 조회합니다.
     *
     * @param pair 조회할 마켓 (null이면 전체)
     * @param page 페이지네이션 파라미터
     * @return 미체결 주문 목록
     */
    suspend fun findOpenOrders(
        pair: TradingPair? = null,
        page: PageRequest = PageRequest(),
    ): PageResponse<Order>
}
