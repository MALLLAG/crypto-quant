package com.cryptoquant.domain.common

import arrow.core.raise.Raise
import arrow.core.raise.context.ensure

/**
 * 체결 순서 ID.
 *
 * 업비트에서 체결 내역의 고유 순서를 나타내는 ID입니다.
 * 시간순으로 증가하며, 페이징 조회에 사용됩니다.
 */
@JvmInline
value class TradeSequentialId private constructor(val value: Long) {
    companion object {
        context(_: Raise<DomainError>)
        operator fun invoke(value: Long): TradeSequentialId {
            ensure(value > 0) { InvalidTradeSequentialId("체결 순서 ID는 양수여야 합니다: $value") }
            return TradeSequentialId(value)
        }
    }
}
