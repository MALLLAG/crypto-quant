package com.cryptoquant.domain

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import arrow.core.raise.ensure
import java.math.BigDecimal

/**
 * 테스트용 Mock 도메인 값 객체
 * 의존관계 확인용으로만 사용됩니다.
 */
@JvmInline
value class MockDomainValue private constructor(val value: BigDecimal) {
    companion object {
        fun Raise<MockDomainError>.invoke(value: BigDecimal): MockDomainValue {
            ensure(value > BigDecimal.ZERO) { MockDomainError.InvalidValue("값은 0보다 커야 합니다") }
            return MockDomainValue(value)
        }

        fun create(value: BigDecimal): Either<MockDomainError, MockDomainValue> = either {
            invoke(value)
        }
    }
}

sealed interface MockDomainError {
    data class InvalidValue(val message: String) : MockDomainError
}

/**
 * 순수 함수 예시 - 도메인 로직
 */
fun MockDomainValue.double(): MockDomainValue =
    MockDomainValue.create(this.value * BigDecimal("2")).getOrNull()!!
