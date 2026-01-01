package com.cryptoquant.domain.quotation

/**
 * 전일 대비 변동 방향.
 */
enum class Change {
    /** 상승 */
    RISE,

    /** 보합 */
    EVEN,

    /** 하락 */
    FALL,
}
