package com.cryptoquant.domain.gateway

/**
 * 게이트웨이 오류.
 *
 * 외부 서비스 호출 시 발생할 수 있는 도메인 수준의 오류입니다.
 * 인프라 계층에서 발생하는 구체적인 오류는 이 타입으로 변환됩니다.
 */
sealed interface GatewayError {
    val code: String
    val message: String

    /**
     * 네트워크 오류.
     * 연결 실패, 타임아웃 등 네트워크 관련 오류.
     */
    data class NetworkError(
        override val code: String,
        override val message: String,
    ) : GatewayError

    /**
     * 인증 오류.
     * API 키가 잘못되었거나 권한이 없는 경우.
     */
    data class AuthenticationError(
        override val code: String,
        override val message: String,
    ) : GatewayError

    /**
     * 요청 제한 오류.
     * API 호출 한도 초과.
     */
    data class RateLimitError(
        override val code: String,
        override val message: String,
    ) : GatewayError

    /**
     * API 오류.
     * 비즈니스 로직 수준의 오류 (잔고 부족, 주문 불가 등).
     */
    data class ApiError(
        override val code: String,
        override val message: String,
    ) : GatewayError

    /**
     * 잘못된 응답 오류.
     * 응답 파싱 실패, 예상치 못한 응답 형식.
     */
    data class InvalidResponse(
        override val code: String,
        override val message: String,
    ) : GatewayError
}
