package com.cryptoquant.infrastructure

import com.cryptoquant.application.MockRepository
import com.cryptoquant.application.MockUseCase
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MockUseCaseConfig {
    @Bean
    fun mockUseCase(repository: MockRepository): MockUseCase = MockUseCase(repository)
}
