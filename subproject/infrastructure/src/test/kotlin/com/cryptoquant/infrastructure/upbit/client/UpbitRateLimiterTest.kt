package com.cryptoquant.infrastructure.upbit.client

import arrow.core.raise.either
import com.cryptoquant.domain.gateway.GatewayError
import com.cryptoquant.infrastructure.upbit.client.UpbitRateLimiter.ApiGroup
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class UpbitRateLimiterTest : DescribeSpec({

    describe("UpbitRateLimiter") {
        context("초기 상태에서") {
            it("각 그룹의 토큰이 최대로 채워져 있다") {
                val rateLimiter = UpbitRateLimiter()

                rateLimiter.availableTokens(ApiGroup.QUOTATION) shouldBe 10
                rateLimiter.availableTokens(ApiGroup.EXCHANGE_DEFAULT) shouldBe 30
                rateLimiter.availableTokens(ApiGroup.ORDER) shouldBe 8
            }
        }

        context("Rate Limit 범위 내에서 요청하면") {
            it("작업이 정상적으로 실행된다") {
                val rateLimiter = UpbitRateLimiter()

                val result = either<GatewayError, Int> {
                    rateLimiter.withRateLimit(ApiGroup.QUOTATION) { 42 }
                }

                result.isRight() shouldBe true
                result.getOrNull() shouldBe 42
            }

            it("토큰이 하나 소비된다") {
                val rateLimiter = UpbitRateLimiter()
                val initialTokens = rateLimiter.availableTokens(ApiGroup.QUOTATION)

                either<GatewayError, Unit> {
                    rateLimiter.withRateLimit(ApiGroup.QUOTATION) { }
                }

                rateLimiter.availableTokens(ApiGroup.QUOTATION) shouldBe initialTokens - 1
            }
        }

        context("Rate Limit을 초과하면") {
            it("RateLimitError를 반환한다") {
                val rateLimiter = UpbitRateLimiter()

                // ORDER 그룹의 모든 토큰(8개) 소비
                repeat(8) {
                    either<GatewayError, Unit> {
                        rateLimiter.withRateLimit(ApiGroup.ORDER) { }
                    }
                }

                // 9번째 요청은 실패해야 함
                val result = either<GatewayError, Unit> {
                    rateLimiter.withRateLimit(ApiGroup.ORDER) { }
                }

                result.isLeft() shouldBe true
                result.leftOrNull().shouldBeInstanceOf<GatewayError.RateLimitError>()

                val error = result.leftOrNull() as GatewayError.RateLimitError
                error.code shouldBe "RATE_LIMIT_EXCEEDED"
                error.message shouldBe "API 요청 제한 초과: ORDER (8/sec)"
            }
        }

        context("각 그룹은 독립적으로 동작하므로") {
            it("한 그룹이 제한에 걸려도 다른 그룹은 영향받지 않는다") {
                val rateLimiter = UpbitRateLimiter()

                // ORDER 그룹의 모든 토큰 소비
                repeat(8) {
                    either<GatewayError, Unit> {
                        rateLimiter.withRateLimit(ApiGroup.ORDER) { }
                    }
                }

                // QUOTATION 그룹은 여전히 사용 가능
                val result = either<GatewayError, Int> {
                    rateLimiter.withRateLimit(ApiGroup.QUOTATION) { 42 }
                }

                result.isRight() shouldBe true
            }
        }
    }

    describe("ApiGroup") {
        it("각 그룹은 올바른 Rate Limit 값을 가진다") {
            ApiGroup.QUOTATION.limitPerSecond shouldBe 10
            ApiGroup.EXCHANGE_DEFAULT.limitPerSecond shouldBe 30
            ApiGroup.ORDER.limitPerSecond shouldBe 8
        }
    }
})
