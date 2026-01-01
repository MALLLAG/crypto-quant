package com.cryptoquant.domain.order

import arrow.core.raise.Raise
import arrow.core.raise.context.ensure
import com.cryptoquant.domain.common.Amount
import com.cryptoquant.domain.common.Price
import com.cryptoquant.domain.common.Volume

/**
 * 주문 유형.
 *
 * 업비트 주문 유형:
 * - limit: 지정가 주문
 * - price: 시장가 매수 (총액 지정)
 * - market: 시장가 매도 (수량 지정)
 * - best: 최유리 주문 (시장 최우선 호가로 주문)
 */
sealed interface OrderType {

    /**
     * 지정가 주문.
     *
     * @property volume 주문 수량
     * @property price 주문 가격
     */
    @ConsistentCopyVisibility
    data class Limit private constructor(
        val volume: Volume,
        val price: Price,
    ) : OrderType {
        companion object {
            context(_: Raise<OrderError>)
            operator fun invoke(volume: Volume, price: Price): Limit {
                ensure(volume.isPositive) {
                    OrderError.InvalidOrderRequest("지정가 주문 수량은 0보다 커야 합니다")
                }
                return Limit(volume, price)
            }
        }
    }

    /**
     * 시장가 매수.
     *
     * 매수할 총액을 지정합니다. 실제 체결 수량은 시장가에 따라 결정됩니다.
     *
     * @property totalPrice 매수 총액
     */
    @ConsistentCopyVisibility
    data class MarketBuy private constructor(
        val totalPrice: Amount,
    ) : OrderType {
        companion object {
            context(_: Raise<OrderError>)
            operator fun invoke(totalPrice: Amount): MarketBuy {
                ensure(totalPrice.isPositive) {
                    OrderError.InvalidOrderRequest("시장가 매수 총액은 0보다 커야 합니다")
                }
                return MarketBuy(totalPrice)
            }
        }
    }

    /**
     * 시장가 매도.
     *
     * 매도할 수량을 지정합니다. 실제 체결 가격은 시장가에 따라 결정됩니다.
     *
     * @property volume 매도 수량
     */
    @ConsistentCopyVisibility
    data class MarketSell private constructor(
        val volume: Volume,
    ) : OrderType {
        companion object {
            context(_: Raise<OrderError>)
            operator fun invoke(volume: Volume): MarketSell {
                ensure(volume.isPositive) {
                    OrderError.InvalidOrderRequest("시장가 매도 수량은 0보다 커야 합니다")
                }
                return MarketSell(volume)
            }
        }
    }

    /**
     * 최유리 주문.
     *
     * 현재 시장의 최우선 호가로 주문합니다.
     *
     * @property volume 주문 수량
     */
    @ConsistentCopyVisibility
    data class Best private constructor(
        val volume: Volume,
    ) : OrderType {
        companion object {
            context(_: Raise<OrderError>)
            operator fun invoke(volume: Volume): Best {
                ensure(volume.isPositive) {
                    OrderError.InvalidOrderRequest("최유리 주문 수량은 0보다 커야 합니다")
                }
                return Best(volume)
            }
        }
    }
}
