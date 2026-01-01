package com.cryptoquant.domain.common

import arrow.core.raise.context.either
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class AmountTest : DescribeSpec({

    describe("Amount") {

        context("유효한 값으로 생성할 때") {
            it("0으로 생성된다") {
                val result = either { Amount(BigDecimal.ZERO) }
                result.shouldBeRight().value shouldBe BigDecimal.ZERO
            }

            it("양수로 생성된다") {
                val result = either { Amount(BigDecimal("1000000")) }
                result.shouldBeRight().value shouldBe BigDecimal("1000000")
            }

            it("문자열로 생성된다") {
                val result = either { Amount("50000") }
                result.shouldBeRight().value shouldBe BigDecimal("50000")
            }
        }

        context("유효하지 않은 값으로 생성할 때") {
            it("음수면 에러를 반환한다") {
                val result = either { Amount(BigDecimal("-100")) }
                result.shouldBeLeft() shouldBe InvalidAmount("금액은 0 이상이어야 합니다: -100")
            }

            it("숫자가 아닌 문자열이면 에러를 반환한다") {
                val result = either { Amount("abc") }
                result.shouldBeLeft() shouldBe InvalidAmount("숫자 형식이 아닙니다: abc")
            }
        }

        context("연산") {
            it("덧셈이 동작한다") {
                val result = either {
                    val a1 = Amount(BigDecimal("1000"))
                    val a2 = Amount(BigDecimal("500"))
                    a1 + a2
                }
                result.shouldBeRight().value shouldBe BigDecimal("1500")
            }

            it("뺄셈 결과가 0 이상이면 Amount를 반환한다") {
                val result = either {
                    val a1 = Amount(BigDecimal("1000"))
                    val a2 = Amount(BigDecimal("400"))
                    a1 - a2
                }
                result.shouldBeRight()?.value shouldBe BigDecimal("600")
            }

            it("뺄셈 결과가 음수면 null을 반환한다") {
                val result = either {
                    val a1 = Amount(BigDecimal("100"))
                    val a2 = Amount(BigDecimal("500"))
                    a1 - a2
                }
                result.shouldBeRight().shouldBeNull()
            }
        }

        context("ZERO 상수") {
            it("Amount.ZERO가 존재한다") {
                Amount.ZERO.value shouldBe BigDecimal.ZERO
                Amount.ZERO.isZero shouldBe true
            }
        }
    }
})
