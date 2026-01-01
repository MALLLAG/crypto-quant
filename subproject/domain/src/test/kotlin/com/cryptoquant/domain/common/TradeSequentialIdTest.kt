package com.cryptoquant.domain.common

import arrow.core.raise.context.either
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class TradeSequentialIdTest : DescribeSpec({

    describe("TradeSequentialId") {

        context("유효한 값으로 생성할 때") {
            it("양수로 생성된다") {
                val result = either { TradeSequentialId(12345678L) }
                result.shouldBeRight().value shouldBe 12345678L
            }
        }

        context("유효하지 않은 값으로 생성할 때") {
            it("0이면 에러를 반환한다") {
                val result = either { TradeSequentialId(0L) }
                result.shouldBeLeft() shouldBe InvalidTradeSequentialId("체결 순서 ID는 양수여야 합니다: 0")
            }

            it("음수면 에러를 반환한다") {
                val result = either { TradeSequentialId(-1L) }
                result.shouldBeLeft() shouldBe InvalidTradeSequentialId("체결 순서 ID는 양수여야 합니다: -1")
            }
        }
    }
})
