package com.cryptoquant.domain

import arrow.core.raise.context.either
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class MockDomainValueTest : DescribeSpec({

    describe("MockDomainValue") {

        context("유효한 값으로 생성할 때") {
            it("성공적으로 생성된다") {
                val result = either { MockDomainValue(BigDecimal("100")) }

                result.shouldBeRight().value shouldBe BigDecimal("100")
            }
        }

        context("0 이하의 값으로 생성할 때") {
            it("InvalidValue 에러를 반환한다") {
                val result = either { MockDomainValue(BigDecimal.ZERO) }

                result.shouldBeLeft() shouldBe MockDomainError.InvalidValue("값은 0보다 커야 합니다")
            }
        }

        context("double 함수 호출 시") {
            it("값이 2배가 된다") {
                val result = either {
                    val value = MockDomainValue(BigDecimal("50"))
                    double(value)
                }

                result.shouldBeRight().value shouldBe BigDecimal("100")
            }
        }
    }
})
