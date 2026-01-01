package com.cryptoquant.domain.common

import arrow.core.raise.context.either
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.math.BigDecimal

class VolumeTest : DescribeSpec({

    describe("Volume") {

        context("유효한 값으로 생성할 때") {
            it("0으로 생성된다") {
                val result = either { Volume(BigDecimal.ZERO) }
                result.shouldBeRight().value shouldBe BigDecimal.ZERO
            }

            it("양수로 생성된다") {
                val result = either { Volume(BigDecimal("0.12345678")) }
                result.shouldBeRight().value shouldBe BigDecimal("0.12345678")
            }

            it("문자열로 생성된다") {
                val result = either { Volume("1.5") }
                result.shouldBeRight().value shouldBe BigDecimal("1.5")
            }
        }

        context("유효하지 않은 값으로 생성할 때") {
            it("음수면 에러를 반환한다") {
                val result = either { Volume(BigDecimal("-1")) }
                result.shouldBeLeft() shouldBe InvalidVolume("수량은 0 이상이어야 합니다")
            }

            it("소수점 9자리 이상이면 에러를 반환한다") {
                val result = either { Volume(BigDecimal("0.123456789")) }
                result.shouldBeLeft() shouldBe InvalidVolume("소수점 8자리까지만 허용됩니다")
            }
        }

        context("연산") {
            it("덧셈이 동작한다") {
                val result = either {
                    val v1 = Volume(BigDecimal("1.5"))
                    val v2 = Volume(BigDecimal("2.5"))
                    v1 + v2
                }
                result.shouldBeRight().value shouldBe BigDecimal("4.0")
            }

            it("뺄셈 결과가 0 이상이면 Volume을 반환한다") {
                val result = either {
                    val v1 = Volume(BigDecimal("3.0"))
                    val v2 = Volume(BigDecimal("1.0"))
                    v1 - v2
                }
                result.shouldBeRight()?.value shouldBe BigDecimal("2.0")
            }

            it("뺄셈 결과가 음수면 null을 반환한다") {
                val result = either {
                    val v1 = Volume(BigDecimal("1.0"))
                    val v2 = Volume(BigDecimal("2.0"))
                    v1 - v2
                }
                result.shouldBeRight().shouldBeNull()
            }
        }

        context("프로퍼티") {
            it("isZero가 올바르게 동작한다") {
                either { Volume(BigDecimal.ZERO) }.shouldBeRight().isZero shouldBe true
                either { Volume(BigDecimal("1")) }.shouldBeRight().isZero shouldBe false
            }

            it("isPositive가 올바르게 동작한다") {
                either { Volume(BigDecimal.ZERO) }.shouldBeRight().isPositive shouldBe false
                either { Volume(BigDecimal("1")) }.shouldBeRight().isPositive shouldBe true
            }
        }

        context("ZERO 상수") {
            it("Volume.ZERO가 존재한다") {
                Volume.ZERO.value shouldBe BigDecimal.ZERO
                Volume.ZERO.isZero shouldBe true
            }
        }
    }
})
