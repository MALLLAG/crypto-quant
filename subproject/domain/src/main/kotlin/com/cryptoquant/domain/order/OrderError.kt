package com.cryptoquant.domain.order

import com.cryptoquant.domain.common.Amount
import com.cryptoquant.domain.common.Price
import com.cryptoquant.domain.common.TickSize

/**
 * 주문 도메인 오류.
 */
sealed interface OrderError {
    val message: String

    data class InvalidOrderRequest(val reason: String) : OrderError {
        override val message: String = reason
    }

    data class ValidationFailed(val reason: String) : OrderError {
        override val message: String = reason
    }

    data class InsufficientBalance(val required: Amount, val available: Amount) : OrderError {
        override val message: String = "잔고 부족: 필요 $required, 가용 $available"
    }

    data class MinimumOrderAmountNotMet(val minimum: Amount, val actual: Amount) : OrderError {
        override val message: String = "최소 주문금액 미충족: 최소 $minimum, 실제 $actual"
    }

    data class InvalidPriceUnit(val price: Price, val expectedTickSize: TickSize) : OrderError {
        override val message: String = "호가단위 불일치: 가격 ${price.value}, 호가단위 ${expectedTickSize.value}"
    }

    data class CurrentPriceRequired(val reason: String) : OrderError {
        override val message: String = reason
    }

    data class OrderNotFound(val id: OrderId) : OrderError {
        override val message: String = "주문을 찾을 수 없습니다: ${id.value}"
    }

    data class OrderNotCancellable(val id: OrderId, val state: OrderState) : OrderError {
        override val message: String = "취소할 수 없는 주문입니다: ${id.value}, 상태: $state"
    }
}
