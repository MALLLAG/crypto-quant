package com.cryptoquant.domain.order

import arrow.core.raise.Raise
import arrow.core.raise.context.ensure

/**
 * 체결 ID.
 *
 * 개별 체결 건을 고유하게 식별합니다.
 * 부분 체결 시 동일 주문에 여러 체결 ID가 발생할 수 있습니다.
 */
@JvmInline
value class TradeId private constructor(val value: String) {
    companion object {
        context(_: Raise<OrderError>)
        operator fun invoke(value: String): TradeId {
            ensure(value.isNotBlank()) {
                OrderError.InvalidOrderRequest("체결 ID는 비어있을 수 없습니다")
            }
            return TradeId(value)
        }
    }
}
