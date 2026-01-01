package com.cryptoquant.domain.quotation

import arrow.core.raise.Raise
import arrow.core.raise.context.ensure
import com.cryptoquant.domain.common.DomainError
import com.cryptoquant.domain.common.InvalidCandleUnit

/**
 * 캔들 단위.
 *
 * 업비트 API 지원 현황:
 * - REST API: 분봉(1,3,5,10,15,30,60,240분), 일봉, 주봉, 월봉
 * - WebSocket: 초봉(1초) 포함
 * - 연봉: 미지원
 */
sealed interface CandleUnit {
    val code: String

    /**
     * 초봉 단위.
     *
     * 주의: WebSocket API에서만 지원됩니다. REST API에서는 사용 불가.
     */
    @ConsistentCopyVisibility
    data class Seconds private constructor(val seconds: Int) : CandleUnit {
        override val code: String = "${seconds}s"

        companion object {
            private val SUPPORTED_UNITS = listOf(1)

            context(_: Raise<DomainError>)
            operator fun invoke(seconds: Int): Seconds {
                ensure(seconds in SUPPORTED_UNITS) {
                    InvalidCandleUnit("지원하지 않는 초봉 단위: $seconds (지원: $SUPPORTED_UNITS)")
                }
                return Seconds(seconds)
            }

            val ONE: Seconds = Seconds(1)
        }
    }

    @ConsistentCopyVisibility
    data class Minutes private constructor(val minutes: Int) : CandleUnit {
        override val code: String = "${minutes}m"

        companion object {
            private val SUPPORTED_UNITS = listOf(1, 3, 5, 10, 15, 30, 60, 240)

            context(_: Raise<DomainError>)
            operator fun invoke(minutes: Int): Minutes {
                ensure(minutes in SUPPORTED_UNITS) {
                    InvalidCandleUnit("지원하지 않는 분봉 단위: $minutes (지원: $SUPPORTED_UNITS)")
                }
                return Minutes(minutes)
            }
        }
    }

    data object Day : CandleUnit {
        override val code: String = "1d"
    }

    data object Week : CandleUnit {
        override val code: String = "1w"
    }

    data object Month : CandleUnit {
        override val code: String = "1M"
    }
}
