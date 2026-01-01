package com.cryptoquant.domain.common

import arrow.core.raise.context.either
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class ChangeRateTest : DescribeSpec({

    describe("ChangeRate") {

        context("유효한 값으로 생성할 때") {
            it("양수로 생성된다 (상승)") {
                val result = either { ChangeRate(BigDecimal("0.05")) }
                val rate = result.shouldBeRight()
                rate.value shouldBe BigDecimal("0.05")
                rate.isPositive shouldBe true
                rate.isNegative shouldBe false
            }

            it("0으로 생성된다 (보합)") {
                val result = either { ChangeRate(BigDecimal.ZERO) }
                val rate = result.shouldBeRight()
                rate.isZero shouldBe true
            }

            it("음수로 생성된다 (하락)") {
                val result = either { ChangeRate(BigDecimal("-0.03")) }
                val rate = result.shouldBeRight()
                rate.isNegative shouldBe true
            }

            it("-1로 생성된다 (-100% 하락)") {
                val result = either { ChangeRate(BigDecimal("-1")) }
                result.shouldBeRight().value shouldBe BigDecimal("-1")
            }

            it("문자열로 생성된다") {
                val result = either { ChangeRate("0.1") }
                result.shouldBeRight().value shouldBe BigDecimal("0.1")
            }
        }

        context("유효하지 않은 값으로 생성할 때") {
            it("-1 미만이면 에러를 반환한다") {
                val result = either { ChangeRate(BigDecimal("-1.5")) }
                result.shouldBeLeft() shouldBe InvalidChangeRate("변동률은 -100% 이상이어야 합니다: -1.5")
            }
        }

        context("toPercent") {
            it("비율을 퍼센트로 변환한다") {
                val result = either { ChangeRate(BigDecimal("0.0523")) }
                result.shouldBeRight().toPercent() shouldBe BigDecimal("5.23")
            }

            it("음수도 변환된다") {
                val result = either { ChangeRate(BigDecimal("-0.03")) }
                result.shouldBeRight().toPercent() shouldBe BigDecimal("-3.00")
            }
        }

        context("ZERO 상수") {
            it("ChangeRate.ZERO가 존재한다") {
                ChangeRate.ZERO.value shouldBe BigDecimal.ZERO
                ChangeRate.ZERO.isZero shouldBe true
            }
        }
    }
})
