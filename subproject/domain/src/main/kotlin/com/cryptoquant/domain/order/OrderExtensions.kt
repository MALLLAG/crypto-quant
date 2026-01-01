package com.cryptoquant.domain.order

import arrow.core.raise.Raise
import arrow.core.raise.context.raise
import com.cryptoquant.domain.common.Amount
import com.cryptoquant.domain.common.Price
import com.cryptoquant.domain.common.Volume

/**
 * 주문 타입별 속성 접근 함수들.
 *
 * 타입 안전성을 위해 확장 프로퍼티 대신 Raise 컨텍스트를 사용하는 함수로 정의합니다.
 * 잘못된 주문 타입에서 호출 시 예외 대신 OrderError를 raise합니다.
 */

/**
 * 지정가 주문의 주문 수량을 반환합니다.
 * 지정가 주문이 아닌 경우 OrderError를 raise합니다.
 */
context(_: Raise<OrderError>)
fun Order.limitVolume(): Volume = when (val type = orderType) {
    is OrderType.Limit -> type.volume
    else -> raise(OrderError.InvalidOrderRequest("지정가 주문이 아닙니다: ${orderType::class.simpleName}"))
}

/**
 * 지정가 주문의 주문 가격을 반환합니다.
 * 지정가 주문이 아닌 경우 OrderError를 raise합니다.
 */
context(_: Raise<OrderError>)
fun Order.limitPrice(): Price = when (val type = orderType) {
    is OrderType.Limit -> type.price
    else -> raise(OrderError.InvalidOrderRequest("지정가 주문이 아닙니다: ${orderType::class.simpleName}"))
}

/**
 * 시장가 매수 주문의 총 주문금액을 반환합니다.
 * 시장가 매수 주문이 아닌 경우 OrderError를 raise합니다.
 */
context(_: Raise<OrderError>)
fun Order.marketBuyTotalPrice(): Amount = when (val type = orderType) {
    is OrderType.MarketBuy -> type.totalPrice
    else -> raise(OrderError.InvalidOrderRequest("시장가 매수 주문이 아닙니다: ${orderType::class.simpleName}"))
}

/**
 * 매도 주문의 매도 수량을 반환합니다.
 * 매도 주문이 아닌 경우 OrderError를 raise합니다.
 */
context(_: Raise<OrderError>)
fun Order.sellVolume(): Volume = when (val type = orderType) {
    is OrderType.MarketSell -> type.volume
    is OrderType.Best -> type.volume
    is OrderType.Limit -> if (side == OrderSide.ASK) {
        type.volume
    } else {
        raise(OrderError.InvalidOrderRequest("매도 주문이 아닙니다"))
    }
    else -> raise(OrderError.InvalidOrderRequest("매도 수량을 가져올 수 없는 주문 타입: ${orderType::class.simpleName}"))
}
