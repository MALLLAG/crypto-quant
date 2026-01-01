package com.cryptoquant.infrastructure.upbit.error

import com.cryptoquant.domain.gateway.GatewayError
import kotlinx.serialization.Serializable
import java.time.Duration

/**
 * Upbit API 에러.
 *
 * 인프라 계층 내부에서만 사용되며, 외부로는 [GatewayError]로 변환됩니다.
 */
sealed interface UpbitApiError {
    val code: String
    val message: String

    /**
     * 네트워크 오류.
     * 연결 실패, 타임아웃 등 네트워크 관련 오류.
     */
    data class NetworkError(
        override val code: String = "NETWORK_ERROR",
        override val message: String,
        val cause: Throwable? = null,
    ) : UpbitApiError

    /**
     * 인증 오류.
     * API 키가 잘못되었거나 권한이 없는 경우.
     */
    data class AuthenticationError(
        override val code: String,
        override val message: String,
    ) : UpbitApiError

    /**
     * 요청 제한 오류.
     * API 호출 한도 초과.
     */
    data class RateLimitError(
        override val code: String = "RATE_LIMIT",
        override val message: String,
        val retryAfter: Duration? = null,
    ) : UpbitApiError

    /**
     * API 비즈니스 오류.
     * 잔고 부족, 주문 불가 등 비즈니스 로직 수준의 오류.
     */
    data class ApiError(
        override val code: String,
        override val message: String,
    ) : UpbitApiError
}

/**
 * Upbit API 에러 응답.
 *
 * Upbit API는 오류 시 다음 형식의 JSON을 반환합니다:
 * ```json
 * {
 *   "error": {
 *     "name": "jwt_verification",
 *     "message": "Invalid token"
 *   }
 * }
 * ```
 */
@Serializable
data class UpbitErrorResponse(
    val error: ErrorDetail,
) {
    @Serializable
    data class ErrorDetail(
        val name: String,
        val message: String,
    )
}

/**
 * [UpbitApiError]를 [GatewayError]로 변환.
 *
 * 인프라 레이어의 구체적인 에러를 도메인 레이어의 추상 에러로 변환합니다.
 */
fun UpbitApiError.toGatewayError(): GatewayError = when (this) {
    is UpbitApiError.NetworkError -> GatewayError.NetworkError(code, message)
    is UpbitApiError.AuthenticationError -> GatewayError.AuthenticationError(code, message)
    is UpbitApiError.RateLimitError -> GatewayError.RateLimitError(code, message)
    is UpbitApiError.ApiError -> GatewayError.ApiError(code, message)
}
