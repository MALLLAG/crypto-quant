package com.cryptoquant.infrastructure.upbit.client

import arrow.core.raise.Raise
import arrow.core.raise.context.raise
import com.cryptoquant.domain.gateway.GatewayError
import com.cryptoquant.infrastructure.upbit.client.UpbitRateLimiter.ApiGroup
import com.cryptoquant.infrastructure.upbit.error.UpbitErrorResponse
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBody

/**
 * Upbit REST API 클라이언트.
 *
 * - WebClient 기반 논블로킹 HTTP 클라이언트
 * - 자동 JWT 인증 헤더 추가
 * - Rate Limit 관리
 * - HTTP 에러를 [GatewayError]로 변환
 *
 * @property webClient Spring WebClient 인스턴스
 * @property authInterceptor JWT 토큰 생성기
 * @property rateLimiter Rate Limit 관리자
 * @property json kotlinx.serialization JSON 인스턴스
 */
class UpbitRestClient(
    @PublishedApi internal val webClient: WebClient,
    @PublishedApi internal val authInterceptor: UpbitAuthInterceptor,
    @PublishedApi internal val rateLimiter: UpbitRateLimiter,
    @PublishedApi internal val json: Json,
) {
    @PublishedApi
    internal val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 인증이 필요 없는 GET 요청 (시세 조회).
     *
     * @param path API 경로 (예: "/v1/ticker")
     * @param params 쿼리 파라미터
     * @return 응답 바디
     */
    context(_: Raise<GatewayError>)
    suspend inline fun <reified T : Any> getPublic(
        path: String,
        params: Map<String, Any> = emptyMap(),
    ): T = rateLimiter.withRateLimit(ApiGroup.QUOTATION) {
        executeRequest {
            webClient.get()
                .uri { builder ->
                    builder.path(path)
                    params.forEach { (key, value) -> builder.queryParam(key, value) }
                    builder.build()
                }
                .retrieve()
                .awaitBody<T>()
        }
    }

    /**
     * 인증이 필요한 GET 요청 (잔고, 주문 조회).
     *
     * @param path API 경로 (예: "/v1/accounts")
     * @param params 쿼리 파라미터
     * @param group API 그룹 (Rate Limit 적용)
     * @return 응답 바디
     */
    context(_: Raise<GatewayError>)
    suspend inline fun <reified T : Any> getPrivate(
        path: String,
        params: Map<String, Any> = emptyMap(),
        group: ApiGroup = ApiGroup.EXCHANGE_DEFAULT,
    ): T = rateLimiter.withRateLimit(group) {
        val token = authInterceptor.generateToken(queryParams = params)

        executeRequest {
            webClient.get()
                .uri { builder ->
                    builder.path(path)
                    params.forEach { (key, value) -> builder.queryParam(key, value) }
                    builder.build()
                }
                .header("Authorization", "Bearer $token")
                .retrieve()
                .awaitBody<T>()
        }
    }

    /**
     * 인증이 필요한 POST 요청 (주문 생성).
     *
     * @param path API 경로 (예: "/v1/orders")
     * @param bodyParams 요청 본문 파라미터 (JWT 해싱에 사용)
     * @param bodyJson JSON 직렬화된 요청 본문
     * @param group API 그룹 (Rate Limit 적용)
     * @return 응답 바디
     *
     * 주의: kotlinx.serialization은 Map<String, Any>를 직렬화할 수 없으므로,
     * 호출부에서 buildJsonObject를 사용해 JsonObject를 생성한 후 toString()으로 전달합니다.
     */
    context(_: Raise<GatewayError>)
    suspend inline fun <reified R : Any> postPrivate(
        path: String,
        bodyParams: Map<String, Any>,
        bodyJson: String,
        group: ApiGroup = ApiGroup.ORDER,
    ): R = rateLimiter.withRateLimit(group) {
        val token = authInterceptor.generateToken(bodyParams = bodyParams)

        executeRequest {
            webClient.post()
                .uri(path)
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(bodyJson)
                .retrieve()
                .awaitBody<R>()
        }
    }

    /**
     * 인증이 필요한 DELETE 요청 (주문 취소).
     *
     * @param path API 경로 (예: "/v1/order")
     * @param params 쿼리 파라미터
     * @param group API 그룹 (Rate Limit 적용)
     * @return 응답 바디
     */
    context(_: Raise<GatewayError>)
    suspend inline fun <reified T : Any> deletePrivate(
        path: String,
        params: Map<String, Any> = emptyMap(),
        group: ApiGroup = ApiGroup.ORDER,
    ): T = rateLimiter.withRateLimit(group) {
        val token = authInterceptor.generateToken(queryParams = params)

        executeRequest {
            webClient.delete()
                .uri { builder ->
                    builder.path(path)
                    params.forEach { (key, value) -> builder.queryParam(key, value) }
                    builder.build()
                }
                .header("Authorization", "Bearer $token")
                .retrieve()
                .awaitBody<T>()
        }
    }

    /**
     * HTTP 에러를 [GatewayError]로 변환.
     *
     * WebClient의 예외를 캐치하여 Raise 컨텍스트로 전환합니다.
     *
     * @see <a href="https://docs.upbit.com/kr/reference/rest-api-guide.md">Upbit REST API 에러 안내</a>
     */
    @Suppress("TooGenericExceptionCaught")
    @PublishedApi
    context(r: Raise<GatewayError>)
    internal suspend inline fun <T> executeRequest(block: () -> T): T {
        return try {
            block()
        } catch (e: WebClientResponseException) {
            val errorBody = e.responseBodyAsString
            val upbitError = runCatching {
                json.decodeFromString<UpbitErrorResponse>(errorBody)
            }.getOrNull()

            val gatewayError = when (e.statusCode.value()) {
                401 -> GatewayError.AuthenticationError(
                    code = upbitError?.error?.name ?: "UNAUTHORIZED",
                    message = upbitError?.error?.message ?: e.message ?: "Authentication failed",
                )
                429 -> GatewayError.RateLimitError(
                    code = "RATE_LIMIT",
                    message = upbitError?.error?.message ?: "Too many requests",
                )
                in 400..499 -> GatewayError.ApiError(
                    code = upbitError?.error?.name ?: "CLIENT_ERROR",
                    message = upbitError?.error?.message ?: e.message ?: "Client error",
                )
                in 500..599 -> GatewayError.NetworkError(
                    code = "SERVER_ERROR",
                    message = upbitError?.error?.message ?: e.message ?: "Server error",
                )
                else -> GatewayError.ApiError(
                    code = "UNKNOWN",
                    message = e.message ?: "Unknown error",
                )
            }

            logger.warn("Upbit API error: ${e.statusCode} - $errorBody", e)
            r.raise(gatewayError)
        } catch (e: Exception) {
            logger.error("Unexpected error during API call", e)
            r.raise(
                GatewayError.NetworkError(
                    code = "NETWORK_ERROR",
                    message = e.message ?: "Network error",
                ),
            )
        }
    }
}
