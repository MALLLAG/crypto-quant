package com.cryptoquant.domain.common

import arrow.core.raise.context.either
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class FeeRateTest : DescribeSpec({

    describe("FeeRate") {

        context("유효한 값으로 생성할 때") {
            it("0으로 생성된다") {
                val result = either { FeeRate(BigDecimal.ZERO) }
                result.shouldBeRight().value shouldBe BigDecimal.ZERO
            }

            it("0.0005로 생성된다") {
                val result = either { FeeRate(BigDecimal("0.0005")) }
                result.shouldBeRight().value shouldBe BigDecimal("0.0005")
            }

            it("1로 생성된다") {
                val result = either { FeeRate(BigDecimal.ONE) }
                result.shouldBeRight().value shouldBe BigDecimal.ONE
            }

            it("문자열로 생성된다") {
                val result = either { FeeRate("0.001") }
                result.shouldBeRight().value shouldBe BigDecimal("0.001")
            }
        }

        context("유효하지 않은 값으로 생성할 때") {
            it("음수면 에러를 반환한다") {
                val result = either { FeeRate(BigDecimal("-0.001")) }
                result.shouldBeLeft() shouldBe InvalidFeeRate("수수료율은 0 이상이어야 합니다: -0.001")
            }

            it("1 초과면 에러를 반환한다") {
                val result = either { FeeRate(BigDecimal("1.1")) }
                result.shouldBeLeft() shouldBe InvalidFeeRate("수수료율은 1 이하여야 합니다: 1.1")
            }
        }

        context("toPercent") {
            it("비율을 퍼센트로 변환한다") {
                val result = either { FeeRate(BigDecimal("0.0005")) }
                result.shouldBeRight().toPercent().compareTo(BigDecimal("0.05")) shouldBe 0
            }
        }

        context("DEFAULT 상수") {
            it("0.0005(0.05%)이다") {
                FeeRate.DEFAULT.value.compareTo(BigDecimal("0.0005")) shouldBe 0
                FeeRate.DEFAULT.toPercent().compareTo(BigDecimal("0.05")) shouldBe 0
            }
        }
    }
})
