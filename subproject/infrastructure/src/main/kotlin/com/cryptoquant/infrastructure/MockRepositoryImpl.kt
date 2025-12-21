package com.cryptoquant.infrastructure

import com.cryptoquant.application.MockRepository
import com.cryptoquant.domain.MockDomainValue
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap

/**
 * 테스트용 Mock Repository 구현체
 * Application 모듈 의존관계 확인용으로만 사용됩니다.
 */
@Repository
class MockRepositoryImpl(
    private val databaseClient: DatabaseClient,
) : MockRepository {

    private val inMemoryStore = ConcurrentHashMap<String, BigDecimal>()

    override suspend fun save(value: MockDomainValue) {
        inMemoryStore["mock-id"] = value.value
    }

    override suspend fun findById(id: String): MockDomainValue? {
        return inMemoryStore[id]?.let { MockDomainValue.create(it).getOrNull() }
    }
}

/**
 * 외부 API 클라이언트 예시
 */
interface MockExchangeClient {
    suspend fun fetchPrice(symbol: String): BigDecimal
}
