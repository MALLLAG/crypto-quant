package com.cryptoquant.domain.order

/**
 * 주문 상태.
 *
 * - WAIT: 체결 대기 (미체결 또는 부분 체결)
 * - WATCH: 예약 주문 대기
 * - DONE: 전체 체결 완료
 * - CANCEL: 취소됨
 */
enum class OrderState {
    WAIT,
    WATCH,
    DONE,
    CANCEL,
}
