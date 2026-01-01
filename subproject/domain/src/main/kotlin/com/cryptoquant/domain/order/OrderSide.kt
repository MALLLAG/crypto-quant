package com.cryptoquant.domain.order

/**
 * 주문 방향.
 *
 * - BID: 매수 (bid = 입찰)
 * - ASK: 매도 (ask = 요청)
 */
enum class OrderSide {
    BID,
    ASK,
}
