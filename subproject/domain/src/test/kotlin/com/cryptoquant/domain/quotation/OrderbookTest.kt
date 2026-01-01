package com.cryptoquant.domain.quotation

import arrow.core.raise.context.either
import com.cryptoquant.domain.common.Market
import com.cryptoquant.domain.common.Price
import com.cryptoquant.domain.common.TradingPair
import com.cryptoquant.domain.common.Volume
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.Instant

class OrderbookTest : DescribeSpec({

    describe("Orderbook") {

        context("호가 데이터가 있을 때") {
            it("최우선 매수 호가를 반환한다") {
                val orderbook = either {
                    Orderbook(
                        pair = TradingPair(Market.KRW, "BTC"),
                        timestamp = Instant.now(),
                        totalAskSize = Volume(BigDecimal("10")),
                        totalBidSize = Volume(BigDecimal("10")),
                        orderbookUnits = listOf(
                            OrderbookUnit(
                                askPrice = Price(BigDecimal("50100000")),
                                bidPrice = Price(BigDecimal("50000000")),
                                askSize = Volume(BigDecimal("1")),
                                bidSize = Volume(BigDecimal("1")),
                            ),
                            OrderbookUnit(
                                askPrice = Price(BigDecimal("50200000")),
                                bidPrice = Price(BigDecimal("49900000")),
                                askSize = Volume(BigDecimal("2")),
                                bidSize = Volume(BigDecimal("2")),
                            ),
                        ),
                    )
                }.shouldBeRight()

                orderbook.bestBidPrice?.value?.compareTo(BigDecimal("50000000")) shouldBe 0
            }

            it("최우선 매도 호가를 반환한다") {
                val orderbook = either {
                    Orderbook(
                        pair = TradingPair(Market.KRW, "BTC"),
                        timestamp = Instant.now(),
                        totalAskSize = Volume(BigDecimal("10")),
                        totalBidSize = Volume(BigDecimal("10")),
                        orderbookUnits = listOf(
                            OrderbookUnit(
                                askPrice = Price(BigDecimal("50100000")),
                                bidPrice = Price(BigDecimal("50000000")),
                                askSize = Volume(BigDecimal("1")),
                                bidSize = Volume(BigDecimal("1")),
                            ),
                            OrderbookUnit(
                                askPrice = Price(BigDecimal("50200000")),
                                bidPrice = Price(BigDecimal("49900000")),
                                askSize = Volume(BigDecimal("2")),
                                bidSize = Volume(BigDecimal("2")),
                            ),
                        ),
                    )
                }.shouldBeRight()

                orderbook.bestAskPrice?.value?.compareTo(BigDecimal("50100000")) shouldBe 0
            }

            it("스프레드를 계산한다") {
                val orderbook = either {
                    Orderbook(
                        pair = TradingPair(Market.KRW, "BTC"),
                        timestamp = Instant.now(),
                        totalAskSize = Volume(BigDecimal("10")),
                        totalBidSize = Volume(BigDecimal("10")),
                        orderbookUnits = listOf(
                            OrderbookUnit(
                                askPrice = Price(BigDecimal("50100000")),
                                bidPrice = Price(BigDecimal("50000000")),
                                askSize = Volume(BigDecimal("1")),
                                bidSize = Volume(BigDecimal("1")),
                            ),
                        ),
                    )
                }.shouldBeRight()

                orderbook.spread()?.compareTo(BigDecimal("100000")) shouldBe 0
            }
        }

        context("호가 데이터가 비어있을 때") {
            it("최우선 호가가 null이다") {
                val orderbook = either {
                    Orderbook(
                        pair = TradingPair(Market.KRW, "BTC"),
                        timestamp = Instant.now(),
                        totalAskSize = Volume.ZERO,
                        totalBidSize = Volume.ZERO,
                        orderbookUnits = emptyList(),
                    )
                }.shouldBeRight()

                orderbook.bestBidPrice.shouldBeNull()
                orderbook.bestAskPrice.shouldBeNull()
                orderbook.spread().shouldBeNull()
            }
        }
    }
})
