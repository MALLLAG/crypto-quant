package com.cryptoquant.domain.common

import arrow.core.raise.context.either
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class TradingPairTest : DescribeSpec({

    describe("TradingPair") {

        context("문자열로 생성할 때") {
            it("유효한 페어 문자열로 생성된다") {
                val result = either { TradingPair("KRW-BTC") }
                val pair = result.shouldBeRight()
                pair.market shouldBe Market.KRW
                pair.ticker shouldBe "BTC"
                pair.value shouldBe "KRW-BTC"
            }

            it("소문자를 대문자로 변환한다") {
                val result = either { TradingPair("krw-btc") }
                val pair = result.shouldBeRight()
                pair.market shouldBe Market.KRW
                pair.ticker shouldBe "BTC"
            }

            it("구분자가 없으면 에러를 반환한다") {
                val result = either { TradingPair("KRWBTC") }
                result.shouldBeLeft().shouldBeInstanceOf<InvalidTradingPair>()
            }

            it("티커가 비어있으면 에러를 반환한다") {
                val result = either { TradingPair("KRW-") }
                result.shouldBeLeft() shouldBe InvalidTradingPair("티커가 비어있습니다")
            }

            it("지원하지 않는 마켓이면 에러를 반환한다") {
                val result = either { TradingPair("ETH-BTC") }
                result.shouldBeLeft().shouldBeInstanceOf<InvalidMarket>()
            }

            it("티커에 특수문자가 있으면 에러를 반환한다") {
                val result = either { TradingPair("KRW-BTC@") }
                result.shouldBeLeft().shouldBeInstanceOf<InvalidTradingPair>()
            }
        }

        context("Market과 ticker로 생성할 때") {
            it("유효한 값으로 생성된다") {
                val result = either { TradingPair(Market.BTC, "ETH") }
                val pair = result.shouldBeRight()
                pair.market shouldBe Market.BTC
                pair.ticker shouldBe "ETH"
                pair.value shouldBe "BTC-ETH"
            }

            it("소문자 티커를 대문자로 변환한다") {
                val result = either { TradingPair(Market.USDT, "btc") }
                val pair = result.shouldBeRight()
                pair.ticker shouldBe "BTC"
            }
        }
    }
})
