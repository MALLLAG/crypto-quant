package com.cryptoquant.infrastructure

import arrow.core.raise.context.either
import com.cryptoquant.domain.MockDomainValue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.mockk
import org.springframework.r2dbc.core.DatabaseClient
import java.math.BigDecimal

class MockRepositoryImplTest : DescribeSpec({

    describe("MockRepositoryImpl") {
        val mockDatabaseClient = mockk<DatabaseClient>(relaxed = true)
        val repository = MockRepositoryImpl(mockDatabaseClient)

        context("save 호출 시") {
            it("값을 저장한다") {
                val value = either { MockDomainValue(BigDecimal("100")) }.getOrNull()!!

                repository.save(value)

                val found = repository.findById("mock-id")
                found shouldNotBe null
                found?.value shouldBe BigDecimal("100")
            }
        }

        context("존재하지 않는 ID로 조회 시") {
            it("null을 반환한다") {
                val found = repository.findById("non-existent")

                found shouldBe null
            }
        }
    }
})
