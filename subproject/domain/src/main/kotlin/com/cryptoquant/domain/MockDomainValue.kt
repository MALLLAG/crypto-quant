package com.cryptoquant.domain

import arrow.core.raise.Raise
import arrow.core.raise.context.ensure
import java.math.BigDecimal

/**
 * 테스트용 Mock 도메인 값 객체
 * 의존관계 확인용으로만 사용됩니다.
 */
@JvmInline
value class MockDomainValue private constructor(val value: BigDecimal) {
    companion object {
        context(_: Raise<MockDomainError>)
        operator fun invoke(value: BigDecimal): MockDomainValue {
            ensure(value > BigDecimal.ZERO) { MockDomainError.InvalidValue("값은 0보다 커야 합니다") }
            return MockDomainValue(value)
        }
    }
}

sealed interface MockDomainError {
    data class InvalidValue(val message: String) : MockDomainError
}

/**
 * 순수 함수 예시 - 도메인 로직
 */
context(_: Raise<MockDomainError>)
fun double(value: MockDomainValue): MockDomainValue =
    MockDomainValue(value.value * BigDecimal("2"))
