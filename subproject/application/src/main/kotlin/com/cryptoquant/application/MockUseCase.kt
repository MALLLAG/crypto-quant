package com.cryptoquant.application

import arrow.core.Either
import arrow.core.raise.either
import com.cryptoquant.domain.MockDomainError
import com.cryptoquant.domain.MockDomainValue
import com.cryptoquant.domain.double
import java.math.BigDecimal

/**
 * 테스트용 Mock 유스케이스
 * Domain 모듈 의존관계 확인용으로만 사용됩니다.
 */
class MockUseCase(
    private val repository: MockRepository,
) {
    suspend fun execute(command: MockCommand): Either<MockUseCaseError, MockResult> = either {
        val domainValue = MockDomainValue.create(command.value)
            .mapLeft { MockUseCaseError.DomainError(it) }
            .bind()

        val doubled = domainValue.double()

        repository.save(doubled)

        MockResult(doubled.value)
    }
}

data class MockCommand(val value: BigDecimal)

data class MockResult(val value: BigDecimal)

sealed interface MockUseCaseError {
    data class DomainError(val error: MockDomainError) : MockUseCaseError
    data class RepositoryError(val message: String) : MockUseCaseError
}

/**
 * Repository 인터페이스 - Infrastructure에서 구현
 */
interface MockRepository {
    suspend fun save(value: MockDomainValue)
    suspend fun findById(id: String): MockDomainValue?
}
