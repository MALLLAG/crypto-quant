package com.cryptoquant.domain.order

import arrow.core.raise.context.either
import com.cryptoquant.domain.common.Amount
import com.cryptoquant.domain.common.Price
import com.cryptoquant.domain.common.Volume
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.math.BigDecimal

class OrderTypeTest : DescribeSpec({

    describe("OrderType.Limit") {

        context("유효한 수량과 가격으로 생성할 때") {
            it("지정가 주문이 생성된다") {
                val result = either {
                    OrderType.Limit(
                        volume = Volume(BigDecimal("0.001")),
                        price = Price(BigDecimal("50000000")),
                    )
                }
                result.shouldBeRight()
            }
        }

        context("수량이 0인 경우") {
            it("에러를 반환한다") {
                val result = either {
                    OrderType.Limit(
                        volume = Volume(BigDecimal.ZERO),
                        price = Price(BigDecimal("50000000")),
                    )
                }
                result.shouldBeLeft().shouldBeInstanceOf<OrderError.InvalidOrderRequest>()
            }
        }
    }

    describe("OrderType.MarketBuy") {

        context("유효한 총액으로 생성할 때") {
            it("시장가 매수 주문이 생성된다") {
                val result = either {
                    OrderType.MarketBuy(
                        totalPrice = Amount(BigDecimal("100000")),
                    )
                }
                result.shouldBeRight()
            }
        }

        context("총액이 0인 경우") {
            it("에러를 반환한다") {
                val result = either {
                    OrderType.MarketBuy(
                        totalPrice = Amount(BigDecimal.ZERO),
                    )
                }
                result.shouldBeLeft().shouldBeInstanceOf<OrderError.InvalidOrderRequest>()
            }
        }
    }

    describe("OrderType.MarketSell") {

        context("유효한 수량으로 생성할 때") {
            it("시장가 매도 주문이 생성된다") {
                val result = either {
                    OrderType.MarketSell(
                        volume = Volume(BigDecimal("0.001")),
                    )
                }
                result.shouldBeRight()
            }
        }

        context("수량이 0인 경우") {
            it("에러를 반환한다") {
                val result = either {
                    OrderType.MarketSell(
                        volume = Volume(BigDecimal.ZERO),
                    )
                }
                result.shouldBeLeft().shouldBeInstanceOf<OrderError.InvalidOrderRequest>()
            }
        }
    }

    describe("OrderType.Best") {

        context("유효한 수량으로 생성할 때") {
            it("최유리 주문이 생성된다") {
                val result = either {
                    OrderType.Best(
                        volume = Volume(BigDecimal("0.001")),
                    )
                }
                result.shouldBeRight()
            }
        }

        context("수량이 0인 경우") {
            it("에러를 반환한다") {
                val result = either {
                    OrderType.Best(
                        volume = Volume(BigDecimal.ZERO),
                    )
                }
                result.shouldBeLeft().shouldBeInstanceOf<OrderError.InvalidOrderRequest>()
            }
        }
    }

    describe("OrderSide") {
        it("BID와 ASK 두 가지 방향이 있다") {
            OrderSide.entries shouldBe listOf(OrderSide.BID, OrderSide.ASK)
        }
    }

    describe("OrderState") {
        it("WAIT, WATCH, DONE, CANCEL 네 가지 상태가 있다") {
            OrderState.entries shouldBe listOf(
                OrderState.WAIT,
                OrderState.WATCH,
                OrderState.DONE,
                OrderState.CANCEL,
            )
        }
    }
})
