package com.cryptoquant.domain.order

import arrow.core.raise.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.ensureNotNull
import arrow.core.raise.recover
import com.cryptoquant.domain.common.Amount
import com.cryptoquant.domain.common.DecimalConfig
import com.cryptoquant.domain.common.Price
import com.cryptoquant.domain.common.TradingPair
import com.cryptoquant.domain.common.Volume
import java.math.BigDecimal
import java.time.Instant

/**
 * 주문 도메인 모델.
 *
 * 설계 노트:
 * - orderType에 이미 volume/price 정보가 포함되어 있습니다.
 * - remainingVolume은 체결 진행 상태를 추적하기 위한 필드입니다.
 * - 주문 타입별 속성 접근은 limitVolume(), limitPrice() 등의
 *   Raise 컨텍스트 함수를 사용하세요.
 *
 * 불변식:
 * - side와 orderType의 정합성 (MarketBuy는 BID만, MarketSell은 ASK만)
 * - remainingVolume + executedVolume == 총 주문 수량 (Limit, MarketSell, Best)
 * - 완료된 주문(DONE)은 remainingVolume이 0이어야 함
 * - 종료된 주문(DONE, CANCEL)은 doneAt이 있어야 함
 *
 * @property orderType 주문 유형 (Limit, MarketBuy, MarketSell, Best) - 수량/가격 정보 포함
 * @property remainingVolume 미체결 잔량 (부분 체결 시 추적용)
 * @property executedVolume 체결된 수량
 * @property executedAmount 체결된 금액 (수량 * 체결가)
 */
@ConsistentCopyVisibility
data class Order private constructor(
    val id: OrderId,
    val pair: TradingPair,
    val side: OrderSide,
    val orderType: OrderType,
    val state: OrderState,
    val remainingVolume: Volume,
    val executedVolume: Volume,
    val executedAmount: Amount,
    val paidFee: Amount,
    val createdAt: Instant,
    val doneAt: Instant?,
) {
    companion object {
        context(_: Raise<OrderError>)
        operator fun invoke(
            id: OrderId,
            pair: TradingPair,
            side: OrderSide,
            orderType: OrderType,
            state: OrderState,
            remainingVolume: Volume,
            executedVolume: Volume,
            executedAmount: Amount,
            paidFee: Amount,
            createdAt: Instant,
            doneAt: Instant?,
        ): Order {
            // 불변식 검증: side와 orderType 정합성
            when (orderType) {
                is OrderType.MarketBuy -> ensure(side == OrderSide.BID) {
                    OrderError.InvalidOrderRequest("시장가 매수(MarketBuy)는 BID만 가능합니다")
                }
                is OrderType.MarketSell -> ensure(side == OrderSide.ASK) {
                    OrderError.InvalidOrderRequest("시장가 매도(MarketSell)는 ASK만 가능합니다")
                }
                is OrderType.Limit, is OrderType.Best -> Unit
            }

            // 불변식 검증: 완료된 주문은 잔량이 0이어야 함
            if (state == OrderState.DONE) {
                ensure(remainingVolume.isZero) {
                    OrderError.InvalidOrderRequest("완료된 주문의 미체결 잔량은 0이어야 합니다")
                }
            }

            // 불변식 검증: 종료된 주문(DONE, CANCEL)은 doneAt이 있어야 함
            if (state == OrderState.DONE || state == OrderState.CANCEL) {
                ensureNotNull(doneAt) {
                    OrderError.InvalidOrderRequest("종료된 주문은 완료 시각이 있어야 합니다")
                }
            }

            // 불변식 검증: 수량 기반 주문의 경우 remaining + executed == total
            when (orderType) {
                is OrderType.Limit -> {
                    val total = orderType.volume.value
                    val sum = remainingVolume.value + executedVolume.value
                    ensure(sum.compareTo(total) == 0) {
                        OrderError.InvalidOrderRequest(
                            "미체결 잔량(${remainingVolume.value}) + " +
                                "체결 수량(${executedVolume.value}) != 총 수량($total)"
                        )
                    }
                }
                is OrderType.MarketSell -> {
                    val total = orderType.volume.value
                    val sum = remainingVolume.value + executedVolume.value
                    ensure(sum.compareTo(total) == 0) {
                        OrderError.InvalidOrderRequest(
                            "미체결 잔량(${remainingVolume.value}) + " +
                                "체결 수량(${executedVolume.value}) != 총 수량($total)"
                        )
                    }
                }
                is OrderType.Best -> {
                    val total = orderType.volume.value
                    val sum = remainingVolume.value + executedVolume.value
                    ensure(sum.compareTo(total) == 0) {
                        OrderError.InvalidOrderRequest(
                            "미체결 잔량(${remainingVolume.value}) + " +
                                "체결 수량(${executedVolume.value}) != 총 수량($total)"
                        )
                    }
                }
                is OrderType.MarketBuy -> {
                    // 시장가 매수는 금액 기반이므로 수량 불변식 검증 생략
                    // remainingAmount()로 잔여 주문금액 조회 가능
                }
            }

            return Order(
                id, pair, side, orderType, state,
                remainingVolume, executedVolume, executedAmount,
                paidFee, createdAt, doneAt
            )
        }
    }

    /** 주문이 미체결 상태인지 (WAIT 또는 WATCH) */
    val isOpen: Boolean get() = state == OrderState.WAIT || state == OrderState.WATCH

    /** 주문을 취소할 수 있는지 */
    val isCancellable: Boolean get() = isOpen

    /** 주문이 종료되었는지 (DONE 또는 CANCEL) */
    val isClosed: Boolean get() = state == OrderState.DONE || state == OrderState.CANCEL

    /**
     * 시장가 매수 주문의 미체결 잔여 금액.
     * 시장가 매수가 아닌 경우 null 반환.
     */
    fun remainingAmount(): Amount? = when (val type = orderType) {
        is OrderType.MarketBuy -> {
            val remaining = type.totalPrice.value - executedAmount.value
            if (remaining >= BigDecimal.ZERO) {
                recover({ Amount(remaining) }) { null }
            } else {
                null
            }
        }
        else -> null
    }

    /**
     * 평균 체결가 계산.
     * 체결 수량이 0이면 null 반환.
     */
    fun averageExecutedPrice(): Price? {
        if (executedVolume.isZero) return null
        val avgPrice = executedAmount.value.divide(
            executedVolume.value,
            DecimalConfig.PRICE_SCALE,
            DecimalConfig.ROUNDING_MODE
        )
        return recover({ Price(avgPrice) }) { null }
    }

    /**
     * 주문 체결률(%)을 계산합니다.
     * orderType에서 직접 총 수량/금액을 가져와 계산합니다.
     */
    fun executionRate(): BigDecimal = when (val type = orderType) {
        is OrderType.Limit -> {
            val totalVolume = type.volume.value
            if (totalVolume == BigDecimal.ZERO) {
                BigDecimal.ZERO
            } else {
                executedVolume.value
                    .divide(totalVolume, DecimalConfig.PERCENT_SCALE + 2, DecimalConfig.ROUNDING_MODE)
                    .multiply(BigDecimal(100))
                    .setScale(DecimalConfig.PERCENT_SCALE, DecimalConfig.ROUNDING_MODE)
            }
        }
        is OrderType.MarketBuy -> {
            val totalPrice = type.totalPrice.value
            if (totalPrice == BigDecimal.ZERO) {
                BigDecimal.ZERO
            } else {
                executedAmount.value
                    .divide(totalPrice, DecimalConfig.PERCENT_SCALE + 2, DecimalConfig.ROUNDING_MODE)
                    .multiply(BigDecimal(100))
                    .setScale(DecimalConfig.PERCENT_SCALE, DecimalConfig.ROUNDING_MODE)
            }
        }
        is OrderType.MarketSell -> {
            val totalVolume = type.volume.value
            if (totalVolume == BigDecimal.ZERO) {
                BigDecimal.ZERO
            } else {
                executedVolume.value
                    .divide(totalVolume, DecimalConfig.PERCENT_SCALE + 2, DecimalConfig.ROUNDING_MODE)
                    .multiply(BigDecimal(100))
                    .setScale(DecimalConfig.PERCENT_SCALE, DecimalConfig.ROUNDING_MODE)
            }
        }
        is OrderType.Best -> {
            val totalVolume = type.volume.value
            if (totalVolume == BigDecimal.ZERO) {
                BigDecimal.ZERO
            } else {
                executedVolume.value
                    .divide(totalVolume, DecimalConfig.PERCENT_SCALE + 2, DecimalConfig.ROUNDING_MODE)
                    .multiply(BigDecimal(100))
                    .setScale(DecimalConfig.PERCENT_SCALE, DecimalConfig.ROUNDING_MODE)
            }
        }
    }
}
