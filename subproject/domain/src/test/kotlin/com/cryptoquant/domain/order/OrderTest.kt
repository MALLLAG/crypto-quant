package com.cryptoquant.domain.order

import arrow.core.raise.context.either
import com.cryptoquant.domain.common.Amount
import com.cryptoquant.domain.common.Market
import com.cryptoquant.domain.common.Price
import com.cryptoquant.domain.common.TradingPair
import com.cryptoquant.domain.common.Volume
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.math.BigDecimal
import java.time.Instant

class OrderTest : DescribeSpec({

    describe("Order") {

        context("유효한 지정가 매수 주문 생성") {
            it("주문이 생성된다") {
                val result = either {
                    val volume = Volume(BigDecimal("0.001"))
                    Order(
                        id = OrderId("test-order-001"),
                        pair = TradingPair(Market.KRW, "BTC"),
                        side = OrderSide.BID,
                        orderType = OrderType.Limit(volume, Price(BigDecimal("50000000"))),
                        state = OrderState.WAIT,
                        remainingVolume = volume,
                        executedVolume = Volume.ZERO,
                        executedAmount = Amount.ZERO,
                        paidFee = Amount.ZERO,
                        createdAt = Instant.now(),
                        doneAt = null,
                    )
                }
                result.shouldBeRight()
            }
        }

        context("시장가 매수(MarketBuy)가 ASK인 경우") {
            it("에러를 반환한다") {
                val result = either {
                    Order(
                        id = OrderId("test-order-001"),
                        pair = TradingPair(Market.KRW, "BTC"),
                        side = OrderSide.ASK, // MarketBuy는 BID만 가능
                        orderType = OrderType.MarketBuy(Amount(BigDecimal("100000"))),
                        state = OrderState.WAIT,
                        remainingVolume = Volume.ZERO,
                        executedVolume = Volume.ZERO,
                        executedAmount = Amount.ZERO,
                        paidFee = Amount.ZERO,
                        createdAt = Instant.now(),
                        doneAt = null,
                    )
                }
                result.shouldBeLeft().shouldBeInstanceOf<OrderError.InvalidOrderRequest>()
            }
        }

        context("시장가 매도(MarketSell)가 BID인 경우") {
            it("에러를 반환한다") {
                val result = either {
                    val volume = Volume(BigDecimal("0.001"))
                    Order(
                        id = OrderId("test-order-001"),
                        pair = TradingPair(Market.KRW, "BTC"),
                        side = OrderSide.BID, // MarketSell은 ASK만 가능
                        orderType = OrderType.MarketSell(volume),
                        state = OrderState.WAIT,
                        remainingVolume = volume,
                        executedVolume = Volume.ZERO,
                        executedAmount = Amount.ZERO,
                        paidFee = Amount.ZERO,
                        createdAt = Instant.now(),
                        doneAt = null,
                    )
                }
                result.shouldBeLeft().shouldBeInstanceOf<OrderError.InvalidOrderRequest>()
            }
        }

        context("완료된 주문(DONE)인데 잔량이 있는 경우") {
            it("에러를 반환한다") {
                val result = either {
                    val volume = Volume(BigDecimal("0.001"))
                    Order(
                        id = OrderId("test-order-001"),
                        pair = TradingPair(Market.KRW, "BTC"),
                        side = OrderSide.BID,
                        orderType = OrderType.Limit(volume, Price(BigDecimal("50000000"))),
                        state = OrderState.DONE, // 완료됨
                        remainingVolume = volume, // 잔량이 있음 - 불변식 위반
                        executedVolume = Volume.ZERO,
                        executedAmount = Amount.ZERO,
                        paidFee = Amount.ZERO,
                        createdAt = Instant.now(),
                        doneAt = Instant.now(),
                    )
                }
                result.shouldBeLeft().shouldBeInstanceOf<OrderError.InvalidOrderRequest>()
            }
        }

        context("종료된 주문인데 doneAt이 없는 경우") {
            it("에러를 반환한다") {
                val result = either {
                    val volume = Volume(BigDecimal("0.001"))
                    Order(
                        id = OrderId("test-order-001"),
                        pair = TradingPair(Market.KRW, "BTC"),
                        side = OrderSide.BID,
                        orderType = OrderType.Limit(volume, Price(BigDecimal("50000000"))),
                        state = OrderState.DONE, // 완료됨
                        remainingVolume = Volume.ZERO,
                        executedVolume = volume,
                        executedAmount = Amount(BigDecimal("50000")),
                        paidFee = Amount.ZERO,
                        createdAt = Instant.now(),
                        doneAt = null, // doneAt이 없음 - 불변식 위반
                    )
                }
                result.shouldBeLeft().shouldBeInstanceOf<OrderError.InvalidOrderRequest>()
            }
        }

        context("수량 불변식 (remaining + executed != total)") {
            it("에러를 반환한다") {
                val result = either {
                    val totalVolume = Volume(BigDecimal("0.001"))
                    Order(
                        id = OrderId("test-order-001"),
                        pair = TradingPair(Market.KRW, "BTC"),
                        side = OrderSide.BID,
                        orderType = OrderType.Limit(totalVolume, Price(BigDecimal("50000000"))),
                        state = OrderState.WAIT,
                        remainingVolume = Volume(BigDecimal("0.0001")), // 불일치
                        executedVolume = Volume(BigDecimal("0.0001")), // 합이 0.0002로 total과 다름
                        executedAmount = Amount.ZERO,
                        paidFee = Amount.ZERO,
                        createdAt = Instant.now(),
                        doneAt = null,
                    )
                }
                result.shouldBeLeft().shouldBeInstanceOf<OrderError.InvalidOrderRequest>()
            }
        }

        context("부분 체결된 주문") {
            it("체결률이 올바르게 계산된다") {
                val order = either {
                    val totalVolume = Volume(BigDecimal("1.0"))
                    Order(
                        id = OrderId("test-order-001"),
                        pair = TradingPair(Market.KRW, "BTC"),
                        side = OrderSide.BID,
                        orderType = OrderType.Limit(totalVolume, Price(BigDecimal("50000000"))),
                        state = OrderState.WAIT,
                        remainingVolume = Volume(BigDecimal("0.5")),
                        executedVolume = Volume(BigDecimal("0.5")),
                        executedAmount = Amount(BigDecimal("25000000")),
                        paidFee = Amount.ZERO,
                        createdAt = Instant.now(),
                        doneAt = null,
                    )
                }.shouldBeRight()

                order.executionRate().compareTo(BigDecimal("50.00")) shouldBe 0
            }
        }

        context("평균 체결가 계산") {
            it("체결된 경우 평균 체결가를 반환한다") {
                val order = either {
                    val totalVolume = Volume(BigDecimal("1.0"))
                    Order(
                        id = OrderId("test-order-001"),
                        pair = TradingPair(Market.KRW, "BTC"),
                        side = OrderSide.BID,
                        orderType = OrderType.Limit(totalVolume, Price(BigDecimal("50000000"))),
                        state = OrderState.DONE,
                        remainingVolume = Volume.ZERO,
                        executedVolume = totalVolume,
                        executedAmount = Amount(BigDecimal("50000000")),
                        paidFee = Amount.ZERO,
                        createdAt = Instant.now(),
                        doneAt = Instant.now(),
                    )
                }.shouldBeRight()

                order.averageExecutedPrice()?.value?.compareTo(BigDecimal("50000000")) shouldBe 0
            }

            it("체결되지 않은 경우 null을 반환한다") {
                val order = either {
                    val totalVolume = Volume(BigDecimal("1.0"))
                    Order(
                        id = OrderId("test-order-001"),
                        pair = TradingPair(Market.KRW, "BTC"),
                        side = OrderSide.BID,
                        orderType = OrderType.Limit(totalVolume, Price(BigDecimal("50000000"))),
                        state = OrderState.WAIT,
                        remainingVolume = totalVolume,
                        executedVolume = Volume.ZERO,
                        executedAmount = Amount.ZERO,
                        paidFee = Amount.ZERO,
                        createdAt = Instant.now(),
                        doneAt = null,
                    )
                }.shouldBeRight()

                order.averageExecutedPrice().shouldBeNull()
            }
        }

        context("주문 상태 속성") {
            it("WAIT 상태는 isOpen=true, isCancellable=true, isClosed=false") {
                val order = either {
                    val totalVolume = Volume(BigDecimal("1.0"))
                    Order(
                        id = OrderId("test-order-001"),
                        pair = TradingPair(Market.KRW, "BTC"),
                        side = OrderSide.BID,
                        orderType = OrderType.Limit(totalVolume, Price(BigDecimal("50000000"))),
                        state = OrderState.WAIT,
                        remainingVolume = totalVolume,
                        executedVolume = Volume.ZERO,
                        executedAmount = Amount.ZERO,
                        paidFee = Amount.ZERO,
                        createdAt = Instant.now(),
                        doneAt = null,
                    )
                }.shouldBeRight()

                order.isOpen shouldBe true
                order.isCancellable shouldBe true
                order.isClosed shouldBe false
            }

            it("DONE 상태는 isOpen=false, isCancellable=false, isClosed=true") {
                val order = either {
                    val totalVolume = Volume(BigDecimal("1.0"))
                    Order(
                        id = OrderId("test-order-001"),
                        pair = TradingPair(Market.KRW, "BTC"),
                        side = OrderSide.BID,
                        orderType = OrderType.Limit(totalVolume, Price(BigDecimal("50000000"))),
                        state = OrderState.DONE,
                        remainingVolume = Volume.ZERO,
                        executedVolume = totalVolume,
                        executedAmount = Amount(BigDecimal("50000000")),
                        paidFee = Amount.ZERO,
                        createdAt = Instant.now(),
                        doneAt = Instant.now(),
                    )
                }.shouldBeRight()

                order.isOpen shouldBe false
                order.isCancellable shouldBe false
                order.isClosed shouldBe true
            }
        }
    }
})
