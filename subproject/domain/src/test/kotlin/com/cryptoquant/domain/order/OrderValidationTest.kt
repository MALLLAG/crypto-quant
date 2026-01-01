package com.cryptoquant.domain.order

import arrow.core.raise.context.either
import com.cryptoquant.domain.account.Balance
import com.cryptoquant.domain.account.Currency
import com.cryptoquant.domain.account.OrderChance
import com.cryptoquant.domain.common.Amount
import com.cryptoquant.domain.common.AvgBuyPrice
import com.cryptoquant.domain.common.FeeRate
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

class OrderValidationTest : DescribeSpec({

    describe("UnvalidatedOrderRequest.validate()") {

        context("유효한 지정가 주문") {
            it("ValidatedOrderRequest가 생성된다") {
                val request = UnvalidatedOrderRequest(
                    pair = "KRW-BTC",
                    side = "BID",
                    orderType = "limit",
                    volume = "0.001",
                    price = "50000000",
                )

                val result = either { request.validate() }

                val validated = result.shouldBeRight()
                validated.pair.market shouldBe Market.KRW
                validated.pair.ticker shouldBe "BTC"
                validated.side shouldBe OrderSide.BID
                validated.orderType.shouldBeInstanceOf<OrderType.Limit>()
            }
        }

        context("유효한 시장가 매수 주문") {
            it("ValidatedOrderRequest가 생성된다") {
                val request = UnvalidatedOrderRequest(
                    pair = "KRW-BTC",
                    side = "BID",
                    orderType = "price",
                    volume = null,
                    price = "100000",
                )

                val result = either { request.validate() }

                val validated = result.shouldBeRight()
                validated.orderType.shouldBeInstanceOf<OrderType.MarketBuy>()
            }
        }

        context("유효한 시장가 매도 주문") {
            it("ValidatedOrderRequest가 생성된다") {
                val request = UnvalidatedOrderRequest(
                    pair = "KRW-BTC",
                    side = "ASK",
                    orderType = "market",
                    volume = "0.001",
                    price = null,
                )

                val result = either { request.validate() }

                val validated = result.shouldBeRight()
                validated.orderType.shouldBeInstanceOf<OrderType.MarketSell>()
            }
        }

        context("유효한 최유리 주문") {
            it("ValidatedOrderRequest가 생성된다") {
                val request = UnvalidatedOrderRequest(
                    pair = "KRW-BTC",
                    side = "BID",
                    orderType = "best",
                    volume = "0.001",
                    price = null,
                )

                val result = either { request.validate() }

                val validated = result.shouldBeRight()
                validated.orderType.shouldBeInstanceOf<OrderType.Best>()
            }
        }

        context("잘못된 마켓 형식") {
            it("에러를 반환한다") {
                val request = UnvalidatedOrderRequest(
                    pair = "INVALID",
                    side = "BID",
                    orderType = "limit",
                    volume = "0.001",
                    price = "50000000",
                )

                val result = either { request.validate() }
                result.shouldBeLeft().shouldBeInstanceOf<OrderError.ValidationFailed>()
            }
        }

        context("잘못된 주문 방향") {
            it("에러를 반환한다") {
                val request = UnvalidatedOrderRequest(
                    pair = "KRW-BTC",
                    side = "INVALID",
                    orderType = "limit",
                    volume = "0.001",
                    price = "50000000",
                )

                val result = either { request.validate() }
                result.shouldBeLeft().shouldBeInstanceOf<OrderError.InvalidOrderRequest>()
            }
        }

        context("시장가 매수인데 ASK인 경우") {
            it("에러를 반환한다") {
                val request = UnvalidatedOrderRequest(
                    pair = "KRW-BTC",
                    side = "ASK",
                    orderType = "price",
                    volume = null,
                    price = "100000",
                )

                val result = either { request.validate() }
                result.shouldBeLeft().shouldBeInstanceOf<OrderError.InvalidOrderRequest>()
            }
        }

        context("시장가 매도인데 BID인 경우") {
            it("에러를 반환한다") {
                val request = UnvalidatedOrderRequest(
                    pair = "KRW-BTC",
                    side = "BID",
                    orderType = "market",
                    volume = "0.001",
                    price = null,
                )

                val result = either { request.validate() }
                result.shouldBeLeft().shouldBeInstanceOf<OrderError.InvalidOrderRequest>()
            }
        }

        context("지정가 주문인데 수량이 없는 경우") {
            it("에러를 반환한다") {
                val request = UnvalidatedOrderRequest(
                    pair = "KRW-BTC",
                    side = "BID",
                    orderType = "limit",
                    volume = null,
                    price = "50000000",
                )

                val result = either { request.validate() }
                result.shouldBeLeft().shouldBeInstanceOf<OrderError.InvalidOrderRequest>()
            }
        }

        context("지정가 주문인데 가격이 없는 경우") {
            it("에러를 반환한다") {
                val request = UnvalidatedOrderRequest(
                    pair = "KRW-BTC",
                    side = "BID",
                    orderType = "limit",
                    volume = "0.001",
                    price = null,
                )

                val result = either { request.validate() }
                result.shouldBeLeft().shouldBeInstanceOf<OrderError.InvalidOrderRequest>()
            }
        }

        context("지원하지 않는 주문 타입") {
            it("에러를 반환한다") {
                val request = UnvalidatedOrderRequest(
                    pair = "KRW-BTC",
                    side = "BID",
                    orderType = "unknown",
                    volume = "0.001",
                    price = "50000000",
                )

                val result = either { request.validate() }
                result.shouldBeLeft().shouldBeInstanceOf<OrderError.InvalidOrderRequest>()
            }
        }
    }

    describe("ValidatedOrderRequest.validateTickSize()") {

        context("호가단위에 맞는 가격") {
            it("검증을 통과한다") {
                val result = either {
                    val pair = TradingPair(Market.KRW, "BTC")
                    val request = ValidatedOrderRequest(
                        pair = pair,
                        side = OrderSide.BID,
                        orderType = OrderType.Limit(
                            volume = Volume(BigDecimal("0.001")),
                            price = Price(BigDecimal("50000000")), // 1000원 단위 맞음
                        ),
                    )
                    request.validateTickSize()
                }
                result.shouldBeRight()
            }
        }

        context("호가단위에 맞지 않는 가격") {
            it("에러를 반환한다") {
                val result = either {
                    val pair = TradingPair(Market.KRW, "BTC")
                    val request = ValidatedOrderRequest(
                        pair = pair,
                        side = OrderSide.BID,
                        orderType = OrderType.Limit(
                            volume = Volume(BigDecimal("0.001")),
                            price = Price(BigDecimal("50000500")), // 500원 - 1000원 단위 불일치
                        ),
                    )
                    request.validateTickSize()
                }
                result.shouldBeLeft().shouldBeInstanceOf<OrderError.InvalidPriceUnit>()
            }
        }

        context("시장가 주문") {
            it("호가단위 검증을 건너뛴다") {
                val result = either {
                    val pair = TradingPair(Market.KRW, "BTC")
                    val request = ValidatedOrderRequest(
                        pair = pair,
                        side = OrderSide.BID,
                        orderType = OrderType.MarketBuy(Amount(BigDecimal("100000"))),
                    )
                    request.validateTickSize()
                }
                result.shouldBeRight()
            }
        }
    }

    describe("ValidatedOrderRequest.validateMinimumOrderAmount()") {

        fun createOrderChance() = either {
            OrderChance(
                pair = TradingPair(Market.KRW, "BTC"),
                bidFee = FeeRate(BigDecimal("0.0005")),
                askFee = FeeRate(BigDecimal("0.0005")),
                bidAccount = Balance(
                    currency = Currency("KRW"),
                    balance = Volume(BigDecimal("1000000")),
                    locked = Volume.ZERO,
                    avgBuyPrice = AvgBuyPrice.ZERO,
                    avgBuyPriceModified = false,
                ),
                askAccount = Balance(
                    currency = Currency("BTC"),
                    balance = Volume(BigDecimal("1")),
                    locked = Volume.ZERO,
                    avgBuyPrice = AvgBuyPrice.ZERO,
                    avgBuyPriceModified = false,
                ),
                minOrderAmount = Amount(BigDecimal("5000")), // 최소 5000원
            )
        }.getOrNull()!!

        context("최소 주문금액 이상인 지정가 주문") {
            it("검증을 통과한다") {
                val result = either {
                    val pair = TradingPair(Market.KRW, "BTC")
                    val request = ValidatedOrderRequest(
                        pair = pair,
                        side = OrderSide.BID,
                        orderType = OrderType.Limit(
                            volume = Volume(BigDecimal("0.0001")),
                            price = Price(BigDecimal("50000000")), // 5000원
                        ),
                    )
                    request.validateMinimumOrderAmount(createOrderChance())
                }
                result.shouldBeRight()
            }
        }

        context("최소 주문금액 미만인 지정가 주문") {
            it("에러를 반환한다") {
                val result = either {
                    val pair = TradingPair(Market.KRW, "BTC")
                    val request = ValidatedOrderRequest(
                        pair = pair,
                        side = OrderSide.BID,
                        orderType = OrderType.Limit(
                            volume = Volume(BigDecimal("0.00001")),
                            price = Price(BigDecimal("50000000")), // 500원
                        ),
                    )
                    request.validateMinimumOrderAmount(createOrderChance())
                }
                result.shouldBeLeft().shouldBeInstanceOf<OrderError.MinimumOrderAmountNotMet>()
            }
        }

        context("시장가 매도 주문에서 현재가가 없는 경우") {
            it("에러를 반환한다") {
                val result = either {
                    val pair = TradingPair(Market.KRW, "BTC")
                    val request = ValidatedOrderRequest(
                        pair = pair,
                        side = OrderSide.ASK,
                        orderType = OrderType.MarketSell(Volume(BigDecimal("0.001"))),
                    )
                    request.validateMinimumOrderAmount(createOrderChance(), currentPrice = null)
                }
                result.shouldBeLeft().shouldBeInstanceOf<OrderError.CurrentPriceRequired>()
            }
        }

        context("시장가 매도 주문에서 현재가가 있는 경우") {
            it("검증을 통과한다") {
                val result = either {
                    val pair = TradingPair(Market.KRW, "BTC")
                    val request = ValidatedOrderRequest(
                        pair = pair,
                        side = OrderSide.ASK,
                        orderType = OrderType.MarketSell(Volume(BigDecimal("0.001"))),
                    )
                    request.validateMinimumOrderAmount(
                        createOrderChance(),
                        currentPrice = Price(BigDecimal("50000000")), // 50000원
                    )
                }
                result.shouldBeRight()
            }
        }
    }
})
