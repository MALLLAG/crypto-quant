package com.cryptoquant.domain.common

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class TickSizeTest : DescribeSpec({

    describe("TickSize") {

        context("KRW 마켓 호가단위") {
            it("2,000,000원 이상은 1,000원 단위") {
                TickSize.forKrwMarket(BigDecimal("2000000")).value shouldBe BigDecimal("1000")
                TickSize.forKrwMarket(BigDecimal("5000000")).value shouldBe BigDecimal("1000")
            }

            it("1,000,000원 이상은 1,000원 단위") {
                TickSize.forKrwMarket(BigDecimal("1000000")).value shouldBe BigDecimal("1000")
                TickSize.forKrwMarket(BigDecimal("1999999")).value shouldBe BigDecimal("1000")
            }

            it("500,000원 이상은 500원 단위") {
                TickSize.forKrwMarket(BigDecimal("500000")).value shouldBe BigDecimal("500")
                TickSize.forKrwMarket(BigDecimal("999999")).value shouldBe BigDecimal("500")
            }

            it("100,000원 이상은 100원 단위") {
                TickSize.forKrwMarket(BigDecimal("100000")).value shouldBe BigDecimal("100")
            }

            it("10원 이상 100원 미만은 0.1원 단위") {
                TickSize.forKrwMarket(BigDecimal("50")).value shouldBe BigDecimal("0.1")
            }

            it("1원 이상 10원 미만은 0.01원 단위") {
                TickSize.forKrwMarket(BigDecimal("5")).value shouldBe BigDecimal("0.01")
            }

            it("0.00001원 미만은 0.00000001원 단위") {
                TickSize.forKrwMarket(BigDecimal("0.000001")).value shouldBe BigDecimal("0.00000001")
            }
        }

        context("BTC 마켓 호가단위") {
            it("항상 0.00000001 BTC 단위") {
                TickSize.forBtcMarket().value shouldBe BigDecimal("0.00000001")
            }
        }

        context("USDT 마켓 호가단위") {
            it("10 USDT 이상은 0.01 단위") {
                TickSize.forUsdtMarket(BigDecimal("100")).value shouldBe BigDecimal("0.01")
            }

            it("1 USDT 이상은 0.001 단위") {
                TickSize.forUsdtMarket(BigDecimal("5")).value shouldBe BigDecimal("0.001")
            }

            it("0.0001 USDT 미만은 0.00000001 단위") {
                TickSize.forUsdtMarket(BigDecimal("0.00001")).value shouldBe BigDecimal("0.00000001")
            }
        }

        context("forMarket") {
            it("마켓별로 올바른 호가단위를 반환한다") {
                TickSize.forMarket(Market.KRW, BigDecimal("1000000")).value shouldBe BigDecimal("1000")
                TickSize.forMarket(Market.BTC, BigDecimal("1")).value shouldBe BigDecimal("0.00000001")
                TickSize.forMarket(Market.USDT, BigDecimal("100")).value shouldBe BigDecimal("0.01")
            }
        }
    }
})
