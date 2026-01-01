package com.cryptoquant.domain.common

import arrow.core.raise.context.either
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class AvgBuyPriceTest : DescribeSpec({

    describe("AvgBuyPrice") {

        context("유효한 값으로 생성할 때") {
            it("0으로 생성된다 (아직 매수한 적 없음)") {
                val result = either { AvgBuyPrice(BigDecimal.ZERO) }
                val price = result.shouldBeRight()
                price.value shouldBe BigDecimal.ZERO
                price.isZero shouldBe true
            }

            it("양수로 생성된다") {
                val result = either { AvgBuyPrice(BigDecimal("50000000")) }
                val price = result.shouldBeRight()
                price.value shouldBe BigDecimal("50000000")
                price.isZero shouldBe false
            }

            it("문자열로 생성된다") {
                val result = either { AvgBuyPrice("100000") }
                result.shouldBeRight().value shouldBe BigDecimal("100000")
            }
        }

        context("유효하지 않은 값으로 생성할 때") {
            it("음수면 에러를 반환한다") {
                val result = either { AvgBuyPrice(BigDecimal("-100")) }
                result.shouldBeLeft() shouldBe InvalidAvgBuyPrice("평균 매수가는 0 이상이어야 합니다: -100")
            }

            it("숫자가 아닌 문자열이면 에러를 반환한다") {
                val result = either { AvgBuyPrice("invalid") }
                result.shouldBeLeft() shouldBe InvalidAvgBuyPrice("숫자 형식이 아닙니다: invalid")
            }
        }

        context("ZERO 상수") {
            it("AvgBuyPrice.ZERO가 존재한다") {
                AvgBuyPrice.ZERO.value shouldBe BigDecimal.ZERO
                AvgBuyPrice.ZERO.isZero shouldBe true
            }
        }
    }
})
