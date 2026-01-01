package com.cryptoquant.domain.order

import arrow.core.raise.Raise
import arrow.core.raise.context.ensure
import com.cryptoquant.domain.common.DomainError
import com.cryptoquant.domain.common.InvalidOrderId

/**
 * 주문 ID.
 *
 * 업비트에서 발급하는 주문 고유 식별자입니다.
 */
@JvmInline
value class OrderId private constructor(val value: String) {
    companion object {
        context(_: Raise<DomainError>)
        operator fun invoke(value: String): OrderId {
            ensure(value.isNotBlank()) { InvalidOrderId("주문 ID는 비어있을 수 없습니다") }
            return OrderId(value)
        }
    }
}
