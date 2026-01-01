package com.cryptoquant.infrastructure.upbit.client

import arrow.core.raise.Raise
import arrow.core.raise.context.raise
import com.cryptoquant.domain.gateway.GatewayError
import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import java.time.Duration

/**
 * Upbit API Rate Limit 관리.
 *
 * API 그룹별 Rate Limit:
 * - QUOTATION: 10/sec (IP 단위) - 시세 조회 API
 * - EXCHANGE_DEFAULT: 30/sec (계정 단위) - 일반 거래 API
 * - ORDER: 8/sec (계정 단위) - 주문 관련 API
 *
 * @see <a href="https://docs.upbit.com/kr/reference/rest-api-guide.md">Upbit REST API 안내</a>
 */
class UpbitRateLimiter {

    /**
     * API 그룹.
     *
     * 각 그룹은 독립적인 Rate Limit을 가집니다.
     */
    enum class ApiGroup(val limitPerSecond: Long) {
        /** 시세 조회 API (10/sec, IP 단위) */
        QUOTATION(10),

        /** 일반 거래 API (30/sec, 계정 단위) */
        EXCHANGE_DEFAULT(30),

        /** 주문 관련 API (8/sec, 계정 단위) */
        ORDER(8),
    }

    private val buckets: Map<ApiGroup, Bucket> = ApiGroup.entries.associateWith { group ->
        createBucket(group.limitPerSecond)
    }

    private fun createBucket(limit: Long): Bucket =
        Bucket.builder()
            .addLimit(Bandwidth.simple(limit, Duration.ofSeconds(1)))
            .build()

    /**
     * Rate Limit 확인 후 작업 실행.
     *
     * 토큰이 사용 가능하면 즉시 소비하고 작업을 실행합니다.
     * 제한 초과 시 [GatewayError.RateLimitError]를 발생시킵니다.
     *
     * @param group API 그룹
     * @param block 실행할 작업
     * @return 작업 결과
     */
    context(_: Raise<GatewayError>)
    suspend fun <T> withRateLimit(group: ApiGroup, block: suspend () -> T): T {
        val bucket = buckets[group] ?: error("Unknown API group: $group")

        if (!bucket.tryConsume(1)) {
            raise(
                GatewayError.RateLimitError(
                    code = "RATE_LIMIT_EXCEEDED",
                    message = "API 요청 제한 초과: $group (${group.limitPerSecond}/sec)",
                ),
            )
        }

        return block()
    }

    /**
     * 현재 사용 가능한 토큰 수 조회.
     *
     * 테스트 및 모니터링 용도로 사용됩니다.
     *
     * @param group API 그룹
     * @return 사용 가능한 토큰 수
     */
    fun availableTokens(group: ApiGroup): Long {
        val bucket = buckets[group] ?: error("Unknown API group: $group")
        return bucket.availableTokens
    }
}
