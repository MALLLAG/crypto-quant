package com.cryptoquant.presentation

import com.cryptoquant.application.MockCommand
import com.cryptoquant.application.MockUseCase
import com.cryptoquant.application.MockUseCaseError
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

/**
 * 테스트용 Mock 컨트롤러
 * Presentation 모듈의 의존관계 확인용으로만 사용됩니다.
 */
@RestController
@RequestMapping("/api/mock")
class MockController(
    private val mockUseCase: MockUseCase,
) {

    @PostMapping
    suspend fun execute(@RequestBody request: MockRequest): ResponseEntity<*> {
        return mockUseCase.execute(MockCommand(request.value))
            .fold(
                { error -> ResponseEntity.badRequest().body(error.toResponse()) },
                { result -> ResponseEntity.ok(MockResponse(result.value)) }
            )
    }

    @GetMapping("/health")
    suspend fun health(): ResponseEntity<String> {
        return ResponseEntity.ok("OK")
    }
}

data class MockRequest(val value: BigDecimal)

data class MockResponse(val result: BigDecimal)

data class ErrorResponse(val code: String, val message: String)

private fun MockUseCaseError.toResponse(): ErrorResponse = when (this) {
    is MockUseCaseError.DomainError -> ErrorResponse("DOMAIN_ERROR", error.toString())
    is MockUseCaseError.RepositoryError -> ErrorResponse("REPOSITORY_ERROR", message)
}
