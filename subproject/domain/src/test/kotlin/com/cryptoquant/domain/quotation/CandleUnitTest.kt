package com.cryptoquant.domain.quotation

import arrow.core.raise.context.either
import com.cryptoquant.domain.common.InvalidCandleUnit
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class CandleUnitTest : DescribeSpec({

    describe("CandleUnit.Seconds") {

        context("지원하는 초봉 단위로 생성할 때") {
            it("1초봉이 생성된다") {
                val result = either { CandleUnit.Seconds(1) }
                val seconds = result.shouldBeRight()
                seconds.seconds shouldBe 1
                seconds.code shouldBe "1s"
            }
        }

        context("지원하지 않는 초봉 단위로 생성할 때") {
            it("에러를 반환한다") {
                val result = either { CandleUnit.Seconds(5) }
                result.shouldBeLeft().shouldBeInstanceOf<InvalidCandleUnit>()
            }
        }

        context("ONE 상수") {
            it("1초봉을 반환한다") {
                CandleUnit.Seconds.ONE.seconds shouldBe 1
                CandleUnit.Seconds.ONE.code shouldBe "1s"
            }
        }
    }

    describe("CandleUnit.Minutes") {

        context("지원하는 분봉 단위로 생성할 때") {
            listOf(1, 3, 5, 10, 15, 30, 60, 240).forEach { minutes ->
                it("${minutes}분봉이 생성된다") {
                    val result = either { CandleUnit.Minutes(minutes) }
                    val unit = result.shouldBeRight()
                    unit.minutes shouldBe minutes
                    unit.code shouldBe "${minutes}m"
                }
            }
        }

        context("지원하지 않는 분봉 단위로 생성할 때") {
            it("2분봉은 에러를 반환한다") {
                val result = either { CandleUnit.Minutes(2) }
                result.shouldBeLeft().shouldBeInstanceOf<InvalidCandleUnit>()
            }

            it("120분봉은 에러를 반환한다") {
                val result = either { CandleUnit.Minutes(120) }
                result.shouldBeLeft().shouldBeInstanceOf<InvalidCandleUnit>()
            }
        }
    }

    describe("CandleUnit.Day") {
        it("일봉 코드가 올바르다") {
            CandleUnit.Day.code shouldBe "1d"
        }
    }

    describe("CandleUnit.Week") {
        it("주봉 코드가 올바르다") {
            CandleUnit.Week.code shouldBe "1w"
        }
    }

    describe("CandleUnit.Month") {
        it("월봉 코드가 올바르다") {
            CandleUnit.Month.code shouldBe "1M"
        }
    }
})
