package com.cryptoquant.domain.order

import com.cryptoquant.domain.common.Amount
import com.cryptoquant.domain.common.Price
import com.cryptoquant.domain.common.TradingPair
import com.cryptoquant.domain.common.Volume
import java.time.Instant

/**
 * 주문 이벤트.
 *
 * 설계 노트:
 * - occurredAt은 기본값 없이 명시적으로 전달받습니다 (테스트 용이성).
 * - 편의를 위해 now() 팩토리 함수를 제공합니다.
 * - OrderExecuted는 개별 체결 건을 나타냅니다 (부분 체결 시 여러 이벤트 발생).
 */
sealed interface OrderEvent {
    val orderId: OrderId
    val occurredAt: Instant

    /**
     * 주문 생성 이벤트.
     */
    data class OrderCreated(
        override val orderId: OrderId,
        val pair: TradingPair,
        val side: OrderSide,
        val orderType: OrderType,
        override val occurredAt: Instant,
    ) : OrderEvent {
        companion object {
            fun now(
                orderId: OrderId,
                pair: TradingPair,
                side: OrderSide,
                orderType: OrderType,
            ) = OrderCreated(orderId, pair, side, orderType, Instant.now())
        }
    }

    /**
     * 주문 체결 이벤트.
     *
     * 개별 체결 건을 나타냅니다. 부분 체결 시 동일 orderId로 여러 이벤트가 발생합니다.
     * tradeId로 개별 체결을 구분하여 멱등성 처리에 활용할 수 있습니다.
     *
     * @property tradeId 개별 체결 ID (멱등성 처리용)
     * @property executedVolume 이번 체결에서 체결된 수량 (누적 아님)
     * @property executedPrice 체결 가격
     */
    data class OrderExecuted(
        override val orderId: OrderId,
        val tradeId: TradeId,
        val executedVolume: Volume,
        val executedPrice: Price,
        val fee: Amount,
        override val occurredAt: Instant,
    ) : OrderEvent {
        companion object {
            fun now(
                orderId: OrderId,
                tradeId: TradeId,
                executedVolume: Volume,
                executedPrice: Price,
                fee: Amount,
            ) = OrderExecuted(orderId, tradeId, executedVolume, executedPrice, fee, Instant.now())
        }
    }

    /**
     * 주문 취소 이벤트.
     */
    data class OrderCancelled(
        override val orderId: OrderId,
        override val occurredAt: Instant,
    ) : OrderEvent {
        companion object {
            fun now(orderId: OrderId) = OrderCancelled(orderId, Instant.now())
        }
    }
}
