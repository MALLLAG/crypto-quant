package com.cryptoquant.domain.common

import arrow.core.raise.context.either
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.math.BigDecimal

class PriceTest : DescribeSpec({

    describe("Price") {

        context("유효한 값으로 생성할 때") {
            it("BigDecimal로 생성된다") {
                val result = either { Price(BigDecimal("100000")) }
                result.shouldBeRight().value shouldBe BigDecimal("100000")
            }

            it("문자열로 생성된다") {
                val result = either { Price("50000.5") }
                result.shouldBeRight().value shouldBe BigDecimal("50000.5")
            }
        }

        context("유효하지 않은 값으로 생성할 때") {
            it("0이면 에러를 반환한다") {
                val result = either { Price(BigDecimal.ZERO) }
                result.shouldBeLeft() shouldBe InvalidPrice("가격은 0보다 커야 합니다")
            }

            it("음수면 에러를 반환한다") {
                val result = either { Price(BigDecimal("-100")) }
                result.shouldBeLeft().shouldBeInstanceOf<InvalidPrice>()
            }

            it("숫자가 아닌 문자열이면 에러를 반환한다") {
                val result = either { Price("invalid") }
                result.shouldBeLeft() shouldBe InvalidPrice("숫자 형식이 아닙니다: invalid")
            }
        }

        context("호가단위 조정") {
            it("호가단위에 맞게 내림 조정된다") {
                val result = either {
                    val price = Price(BigDecimal("1234567"))
                    val tickSize = TickSize.forKrwMarket(price.value)
                    price.adjustToTickSize(tickSize)
                }
                result.shouldBeRight().value.compareTo(BigDecimal("1234000")) shouldBe 0
            }

            it("이미 호가단위에 맞으면 그대로 유지된다") {
                val result = either {
                    val price = Price(BigDecimal("1000000"))
                    val tickSize = TickSize.forKrwMarket(price.value)
                    price.adjustToTickSize(tickSize)
                }
                result.shouldBeRight().value.compareTo(BigDecimal("1000000")) shouldBe 0
            }
        }

        context("호가단위 검증") {
            it("호가단위에 맞으면 성공한다") {
                val result = either {
                    val price = Price(BigDecimal("1000000"))
                    val tickSize = TickSize.forKrwMarket(price.value)
                    price.validateTickSize(tickSize)
                }
                result.shouldBeRight()
            }

            it("호가단위에 맞지 않으면 에러를 반환한다") {
                val result = either {
                    val price = Price(BigDecimal("1000001"))
                    val tickSize = TickSize.forKrwMarket(price.value)
                    price.validateTickSize(tickSize)
                }
                result.shouldBeLeft().shouldBeInstanceOf<InvalidTickSize>()
            }
        }
    }
})
