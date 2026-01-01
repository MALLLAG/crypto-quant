package com.cryptoquant.domain.order

import com.cryptoquant.domain.common.TradingPair

/**
 * 검증되지 않은 주문 요청.
 *
 * 외부에서 들어온 원시 주문 데이터입니다.
 * validate() 함수를 통해 ValidatedOrderRequest로 변환됩니다.
 */
data class UnvalidatedOrderRequest(
    val pair: String,
    val side: String,
    val orderType: String,
    val volume: String?,
    val price: String?,
)

/**
 * 검증된 주문 요청.
 *
 * 비즈니스 규칙 검증을 통과한 주문 요청입니다.
 * 거래소 API로 전송할 준비가 된 상태입니다.
 */
data class ValidatedOrderRequest(
    val pair: TradingPair,
    val side: OrderSide,
    val orderType: OrderType,
)
