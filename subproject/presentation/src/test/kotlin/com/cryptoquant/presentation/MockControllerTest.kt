package com.cryptoquant.presentation

import arrow.core.raise.effect
import com.cryptoquant.application.MockResult
import com.cryptoquant.application.MockUseCase
import com.ninjasquad.springmockk.MockkBean
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.coEvery
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import java.math.BigDecimal

@WebFluxTest(MockController::class)
class MockControllerTest(
    private val webTestClient: WebTestClient,
    @MockkBean private val mockUseCase: MockUseCase,
) : DescribeSpec({

    describe("MockController") {

        context("POST /api/mock") {
            it("유효한 요청이면 200 OK를 반환한다") {
                coEvery { mockUseCase.execute(any()) } returns effect { MockResult(BigDecimal("100")) }

                webTestClient.post()
                    .uri("/api/mock")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""{"value": 50}""")
                    .exchange()
                    .expectStatus().isOk
                    .expectBody()
                    .jsonPath("$.result").isEqualTo(100)
            }
        }

        context("GET /api/mock/health") {
            it("OK를 반환한다") {
                webTestClient.get()
                    .uri("/api/mock/health")
                    .exchange()
                    .expectStatus().isOk
                    .expectBody(String::class.java)
                    .isEqualTo("OK")
            }
        }
    }
})
