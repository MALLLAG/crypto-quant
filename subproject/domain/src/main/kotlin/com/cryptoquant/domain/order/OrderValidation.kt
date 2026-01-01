package com.cryptoquant.domain.order

import arrow.core.raise.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.ensureNotNull
import arrow.core.raise.context.raise
import arrow.core.raise.context.withError
import arrow.core.raise.recover
import com.cryptoquant.domain.account.OrderChance
import com.cryptoquant.domain.common.Amount
import com.cryptoquant.domain.common.Price
import com.cryptoquant.domain.common.TickSize
import com.cryptoquant.domain.common.TradingPair
import com.cryptoquant.domain.common.Volume
import java.math.BigDecimal

/**
 * UnvalidatedOrderRequest를 ValidatedOrderRequest로 변환합니다.
 * 검증 실패 시 OrderError를 raise합니다.
 */
context(_: Raise<OrderError>)
fun UnvalidatedOrderRequest.validate(): ValidatedOrderRequest {
    val pair = withError<OrderError, _, _>({ OrderError.ValidationFailed(it.message) }) {
        TradingPair(this@validate.pair)
    }

    val side = OrderSide.entries.find { it.name == this.side.uppercase() }
        ?: raise(OrderError.InvalidOrderRequest("올바르지 않은 주문 방향: ${this.side}"))

    val orderType = when (this.orderType.lowercase()) {
        "limit" -> {
            ensureNotNull(this.volume) {
                OrderError.InvalidOrderRequest("지정가 주문은 수량이 필요합니다")
            }
            ensureNotNull(this.price) {
                OrderError.InvalidOrderRequest("지정가 주문은 가격이 필요합니다")
            }
            val volume = withError<OrderError, _, _>({ OrderError.ValidationFailed(it.message) }) {
                Volume(this@validate.volume)
            }
            val price = withError<OrderError, _, _>({ OrderError.ValidationFailed(it.message) }) {
                Price(this@validate.price)
            }
            OrderType.Limit(volume, price)
        }
        "price" -> {
            ensure(side == OrderSide.BID) {
                OrderError.InvalidOrderRequest("시장가 매수는 BID만 가능합니다")
            }
            ensureNotNull(this.price) {
                OrderError.InvalidOrderRequest("시장가 매수는 총액이 필요합니다")
            }
            val priceValue = this@validate.price.toBigDecimalOrNull()
                ?: raise(OrderError.ValidationFailed("숫자 형식이 아닙니다: ${this@validate.price}"))
            val totalPrice = withError<OrderError, _, _>({ OrderError.ValidationFailed(it.message) }) {
                Amount(priceValue)
            }
            OrderType.MarketBuy(totalPrice)
        }
        "market" -> {
            ensure(side == OrderSide.ASK) {
                OrderError.InvalidOrderRequest("시장가 매도는 ASK만 가능합니다")
            }
            ensureNotNull(this.volume) {
                OrderError.InvalidOrderRequest("시장가 매도는 수량이 필요합니다")
            }
            val volume = withError<OrderError, _, _>({ OrderError.ValidationFailed(it.message) }) {
                Volume(this@validate.volume)
            }
            OrderType.MarketSell(volume)
        }
        "best" -> {
            ensureNotNull(this.volume) {
                OrderError.InvalidOrderRequest("최유리 주문은 수량이 필요합니다")
            }
            val volume = withError<OrderError, _, _>({ OrderError.ValidationFailed(it.message) }) {
                Volume(this@validate.volume)
            }
            OrderType.Best(volume)
        }
        else -> raise(OrderError.InvalidOrderRequest("지원하지 않는 주문 타입: ${this.orderType}"))
    }

    return ValidatedOrderRequest(pair, side, orderType)
}

/**
 * 최소 주문 금액 검증.
 *
 * @param orderChance 주문 가능 정보 (최소 주문금액 포함)
 * @param currentPrice 현재가 (시장가 매도/Best 주문 시 필수)
 */
context(_: Raise<OrderError>)
fun ValidatedOrderRequest.validateMinimumOrderAmount(
    orderChance: OrderChance,
    currentPrice: Price? = null,
) {
    val minimumAmount = orderChance.minOrderAmount

    val orderAmount: Amount = when (val type = orderType) {
        is OrderType.Limit -> {
            recover({ Amount(type.volume.value * type.price.value) }) {
                raise(OrderError.ValidationFailed("주문 금액 계산 실패"))
            }
        }
        is OrderType.MarketBuy -> type.totalPrice
        is OrderType.MarketSell -> {
            val price = ensureNotNull(currentPrice) {
                OrderError.CurrentPriceRequired("시장가 매도 주문의 최소금액 검증에는 현재가가 필요합니다")
            }
            recover({ Amount(type.volume.value * price.value) }) {
                raise(OrderError.ValidationFailed("주문 금액 계산 실패"))
            }
        }
        is OrderType.Best -> {
            val price = ensureNotNull(currentPrice) {
                OrderError.CurrentPriceRequired("최유리 주문의 최소금액 검증에는 현재가가 필요합니다")
            }
            recover({ Amount(type.volume.value * price.value) }) {
                raise(OrderError.ValidationFailed("주문 금액 계산 실패"))
            }
        }
    }

    ensure(orderAmount >= minimumAmount) {
        OrderError.MinimumOrderAmountNotMet(minimumAmount, orderAmount)
    }
}

/**
 * 호가단위 검증.
 * 지정가 주문의 가격이 해당 마켓의 호가단위에 맞는지 검증합니다.
 */
context(_: Raise<OrderError>)
fun ValidatedOrderRequest.validateTickSize() {
    val price = when (val type = orderType) {
        is OrderType.Limit -> type.price
        else -> return
    }

    val tickSize = TickSize.forMarket(pair.market, price.value)
    val remainder = price.value.remainder(tickSize.value)

    ensure(remainder.compareTo(BigDecimal.ZERO) == 0) {
        OrderError.InvalidPriceUnit(price, tickSize)
    }
}
