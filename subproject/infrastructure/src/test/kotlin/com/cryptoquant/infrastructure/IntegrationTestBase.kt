package com.cryptoquant.infrastructure

import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * 통합 테스트 베이스 클래스.
 *
 * PostgreSQL TestContainer를 사용하여 실제 데이터베이스 환경에서 테스트합니다.
 * 컨테이너는 테스트 클래스 간에 재사용됩니다.
 */
@Testcontainers
abstract class IntegrationTestBase {

    companion object {
        @Container
        @JvmStatic
        val postgresContainer: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:15-alpine")
            .withDatabaseName("cryptoquant_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true)

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.r2dbc.url") {
                "r2dbc:postgresql://${postgresContainer.host}:${postgresContainer.firstMappedPort}/${postgresContainer.databaseName}"
            }
            registry.add("spring.r2dbc.username", postgresContainer::getUsername)
            registry.add("spring.r2dbc.password", postgresContainer::getPassword)
        }
    }
}
