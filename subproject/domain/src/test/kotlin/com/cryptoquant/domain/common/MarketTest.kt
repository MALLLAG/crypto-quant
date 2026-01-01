package com.cryptoquant.domain.common

import arrow.core.raise.context.either
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class MarketTest : DescribeSpec({

    describe("Market.from") {

        context("유효한 마켓 코드로 변환할 때") {
            it("KRW 마켓을 반환한다") {
                val result = either { Market.from("KRW") }
                result.shouldBeRight() shouldBe Market.KRW
            }

            it("BTC 마켓을 반환한다") {
                val result = either { Market.from("btc") }
                result.shouldBeRight() shouldBe Market.BTC
            }

            it("USDT 마켓을 반환한다") {
                val result = either { Market.from("Usdt") }
                result.shouldBeRight() shouldBe Market.USDT
            }
        }

        context("지원하지 않는 마켓 코드로 변환할 때") {
            it("InvalidMarket 에러를 반환한다") {
                val result = either { Market.from("ETH") }
                result.shouldBeLeft() shouldBe InvalidMarket("지원하지 않는 마켓: ETH")
            }
        }
    }
})
