package com.cryptoquant.domain.quotation

import arrow.core.raise.context.either
import com.cryptoquant.domain.common.Amount
import com.cryptoquant.domain.common.InvalidCandle
import com.cryptoquant.domain.common.Market
import com.cryptoquant.domain.common.Price
import com.cryptoquant.domain.common.TradingPair
import com.cryptoquant.domain.common.Volume
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.math.BigDecimal
import java.time.Instant

class CandleTest : DescribeSpec({

    describe("Candle") {

        context("유효한 OHLCV 데이터로 생성할 때") {
            it("캔들이 생성된다") {
                val result = either {
                    Candle(
                        pair = TradingPair(Market.KRW, "BTC"),
                        unit = CandleUnit.Day,
                        timestamp = Instant.now(),
                        openingPrice = Price(BigDecimal("50000000")),
                        highPrice = Price(BigDecimal("52000000")),
                        lowPrice = Price(BigDecimal("49000000")),
                        closingPrice = Price(BigDecimal("51000000")),
                        volume = Volume(BigDecimal("100")),
                        amount = Amount(BigDecimal("5000000000")),
                    )
                }
                result.shouldBeRight()
            }

            it("시가=고가=저가=종가인 도지 캔들이 생성된다") {
                val result = either {
                    val price = Price(BigDecimal("50000000"))
                    Candle(
                        pair = TradingPair(Market.KRW, "BTC"),
                        unit = CandleUnit.Day,
                        timestamp = Instant.now(),
                        openingPrice = price,
                        highPrice = price,
                        lowPrice = price,
                        closingPrice = price,
                        volume = Volume(BigDecimal("10")),
                        amount = Amount(BigDecimal("500000000")),
                    )
                }
                result.shouldBeRight()
            }
        }

        context("불변식을 위반하는 데이터로 생성할 때") {
            it("고가 < 저가이면 에러를 반환한다") {
                val result = either {
                    Candle(
                        pair = TradingPair(Market.KRW, "BTC"),
                        unit = CandleUnit.Day,
                        timestamp = Instant.now(),
                        openingPrice = Price(BigDecimal("50000000")),
                        highPrice = Price(BigDecimal("49000000")),
                        lowPrice = Price(BigDecimal("51000000")),
                        closingPrice = Price(BigDecimal("50000000")),
                        volume = Volume(BigDecimal("100")),
                        amount = Amount(BigDecimal("5000000000")),
                    )
                }
                result.shouldBeLeft() shouldBe InvalidCandle("고가는 저가보다 크거나 같아야 합니다")
            }

            it("고가 < 시가이면 에러를 반환한다") {
                val result = either {
                    Candle(
                        pair = TradingPair(Market.KRW, "BTC"),
                        unit = CandleUnit.Day,
                        timestamp = Instant.now(),
                        openingPrice = Price(BigDecimal("52000000")),
                        highPrice = Price(BigDecimal("51000000")),
                        lowPrice = Price(BigDecimal("49000000")),
                        closingPrice = Price(BigDecimal("50000000")),
                        volume = Volume(BigDecimal("100")),
                        amount = Amount(BigDecimal("5000000000")),
                    )
                }
                result.shouldBeLeft().shouldBeInstanceOf<InvalidCandle>()
            }

            it("저가 > 종가이면 에러를 반환한다") {
                val result = either {
                    Candle(
                        pair = TradingPair(Market.KRW, "BTC"),
                        unit = CandleUnit.Day,
                        timestamp = Instant.now(),
                        openingPrice = Price(BigDecimal("50000000")),
                        highPrice = Price(BigDecimal("52000000")),
                        lowPrice = Price(BigDecimal("51000000")),
                        closingPrice = Price(BigDecimal("50000000")),
                        volume = Volume(BigDecimal("100")),
                        amount = Amount(BigDecimal("5000000000")),
                    )
                }
                result.shouldBeLeft().shouldBeInstanceOf<InvalidCandle>()
            }
        }
    }
})
