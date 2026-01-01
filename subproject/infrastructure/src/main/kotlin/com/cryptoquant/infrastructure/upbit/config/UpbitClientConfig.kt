package com.cryptoquant.infrastructure.upbit.config

import com.cryptoquant.infrastructure.upbit.client.UpbitAuthInterceptor
import com.cryptoquant.infrastructure.upbit.client.UpbitRateLimiter
import com.cryptoquant.infrastructure.upbit.client.UpbitRestClient
import kotlinx.serialization.json.Json
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.codec.json.KotlinSerializationJsonDecoder
import org.springframework.http.codec.json.KotlinSerializationJsonEncoder
import org.springframework.web.reactive.function.client.WebClient

/**
 * Upbit API 클라이언트 설정.
 *
 * - kotlinx.serialization 코덱 설정
 * - WebClient 기본 설정
 * - 인증 및 Rate Limit Bean 등록
 */
@Configuration
@EnableConfigurationProperties(UpbitProperties::class)
class UpbitClientConfig(
    private val properties: UpbitProperties,
) {
    /**
     * kotlinx.serialization JSON 설정.
     *
     * - ignoreUnknownKeys: API 응답에 알 수 없는 필드 무시
     * - isLenient: 유연한 파싱 허용
     * - coerceInputValues: null을 기본값으로 변환
     * - explicitNulls: null 필드 직렬화 생략
     */
    @Bean
    fun upbitJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    /**
     * Upbit API 전용 WebClient.
     *
     * kotlinx.serialization 코덱을 사용하여 JSON 직렬화/역직렬화를 수행합니다.
     */
    @Bean
    fun upbitWebClient(json: Json): WebClient = WebClient.builder()
        .baseUrl(properties.api.baseUrl)
        .codecs { configurer ->
            configurer.defaultCodecs().maxInMemorySize(1024 * 1024) // 1MB
            configurer.defaultCodecs().kotlinSerializationJsonDecoder(
                KotlinSerializationJsonDecoder(json),
            )
            configurer.defaultCodecs().kotlinSerializationJsonEncoder(
                KotlinSerializationJsonEncoder(json),
            )
        }
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .build()

    /**
     * JWT 인증 인터셉터.
     */
    @Bean
    fun upbitAuthInterceptor(): UpbitAuthInterceptor = UpbitAuthInterceptor(
        accessKey = properties.api.accessKey,
        secretKey = properties.api.secretKey,
    )

    /**
     * Rate Limiter.
     */
    @Bean
    fun upbitRateLimiter(): UpbitRateLimiter = UpbitRateLimiter()

    /**
     * Upbit REST API 클라이언트.
     */
    @Bean
    fun upbitRestClient(
        webClient: WebClient,
        authInterceptor: UpbitAuthInterceptor,
        rateLimiter: UpbitRateLimiter,
        json: Json,
    ): UpbitRestClient = UpbitRestClient(
        webClient = webClient,
        authInterceptor = authInterceptor,
        rateLimiter = rateLimiter,
        json = json,
    )
}
