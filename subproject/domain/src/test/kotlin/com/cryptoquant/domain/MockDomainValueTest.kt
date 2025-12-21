package com.cryptoquant.domain

import arrow.core.raise.either
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.math.BigDecimal

class MockDomainValueTest : DescribeSpec({

    describe("MockDomainValue") {

        context("유효한 값으로 생성할 때") {
            it("성공적으로 생성된다") {
                val result = MockDomainValue.create(BigDecimal("100"))

                result.isRight() shouldBe true
                result.getOrNull()?.value shouldBe BigDecimal("100")
            }
        }

        context("0 이하의 값으로 생성할 때") {
            it("InvalidValue 에러를 반환한다") {
                val result = MockDomainValue.create(BigDecimal.ZERO)

                result.isLeft() shouldBe true
                result.leftOrNull().shouldBeInstanceOf<MockDomainError.InvalidValue>()
            }
        }

        context("double 함수 호출 시") {
            it("값이 2배가 된다") {
                val value = MockDomainValue.create(BigDecimal("50")).getOrNull()!!
                val doubled = value.double()

                doubled.value shouldBe BigDecimal("100")
            }
        }
    }
})
