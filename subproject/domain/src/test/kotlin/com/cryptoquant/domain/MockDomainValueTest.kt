package com.cryptoquant.domain

import arrow.core.raise.effect
import arrow.core.raise.toEither
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.math.BigDecimal

class MockDomainValueTest : DescribeSpec({

    describe("MockDomainValue") {

        context("유효한 값으로 생성할 때") {
            it("성공적으로 생성된다") {
                val result = MockDomainValue.create(BigDecimal("100")).toEither()

                result.isRight() shouldBe true
                result.getOrNull()?.value shouldBe BigDecimal("100")
            }
        }

        context("0 이하의 값으로 생성할 때") {
            it("InvalidValue 에러를 반환한다") {
                val result = MockDomainValue.create(BigDecimal.ZERO).toEither()

                result.isLeft() shouldBe true
                result.leftOrNull().shouldBeInstanceOf<MockDomainError.InvalidValue>()
            }
        }

        context("double 함수 호출 시") {
            it("값이 2배가 된다") {
                val result = effect {
                    val value = MockDomainValue.create(BigDecimal("50")).bind()
                    double(value)
                }.toEither()

                result.isRight() shouldBe true
                result.getOrNull()?.value shouldBe BigDecimal("100")
            }
        }
    }
})
