package com.cryptoquant.infrastructure.config

import io.r2dbc.spi.ConnectionFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories
import org.springframework.r2dbc.connection.R2dbcTransactionManager
import org.springframework.transaction.ReactiveTransactionManager

/**
 * R2DBC 설정.
 *
 * Spring Boot의 자동 구성을 활용하며, 추가 커스터마이징을 제공합니다.
 *
 * 연결 풀 설정은 application.yml에서 관리:
 * - spring.r2dbc.pool.initial-size
 * - spring.r2dbc.pool.max-size
 * - spring.r2dbc.pool.max-idle-time
 */
@Configuration
@EnableR2dbcRepositories(basePackages = ["com.cryptoquant.infrastructure.repository"])
@EnableR2dbcAuditing
class R2dbcConfig {

    /**
     * 리액티브 트랜잭션 매니저.
     */
    @Bean
    fun transactionManager(connectionFactory: ConnectionFactory): ReactiveTransactionManager =
        R2dbcTransactionManager(connectionFactory)
}
