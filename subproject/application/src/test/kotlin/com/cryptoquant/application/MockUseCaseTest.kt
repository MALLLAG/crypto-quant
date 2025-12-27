package com.cryptoquant.application

import arrow.core.raise.toEither
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigDecimal

class MockUseCaseTest : DescribeSpec({

    describe("MockUseCase") {
        val mockRepository = mockk<MockRepository>(relaxed = true)
        val useCase = MockUseCase(mockRepository)

        context("유효한 명령이 주어졌을 때") {
            it("도메인 로직을 실행하고 결과를 저장한다") {
                val command = MockCommand(BigDecimal("50"))

                val result = useCase.execute(command).toEither()

                result.isRight() shouldBe true
                result.getOrNull()?.value shouldBe BigDecimal("100")

                coVerify { mockRepository.save(any()) }
            }
        }

        context("잘못된 값이 주어졌을 때") {
            it("도메인 에러를 반환한다") {
                val command = MockCommand(BigDecimal("-10"))

                val result = useCase.execute(command).toEither()

                result.isLeft() shouldBe true
            }
        }
    }
})
