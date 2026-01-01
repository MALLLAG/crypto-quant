package com.cryptoquant.infrastructure.upbit.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Upbit API 설정 프로퍼티.
 *
 * application.yml의 `upbit` 프리픽스로 바인딩됩니다.
 *
 * @property api REST API 설정
 * @property websocket WebSocket 설정
 */
@ConfigurationProperties(prefix = "upbit")
data class UpbitProperties(
    val api: ApiProperties,
    val websocket: WebSocketProperties,
) {
    /**
     * REST API 설정.
     *
     * @property baseUrl API 기본 URL
     * @property accessKey 접근 키 (환경변수 주입 권장)
     * @property secretKey 비밀 키 (환경변수 주입 권장)
     */
    data class ApiProperties(
        val baseUrl: String = "https://api.upbit.com",
        val accessKey: String = "",
        val secretKey: String = "",
    )

    /**
     * WebSocket 설정.
     *
     * @property url 공개 WebSocket URL (시세 데이터)
     * @property privateUrl 비공개 WebSocket URL (내 주문/자산)
     * @property pingInterval Ping 전송 간격
     * @property reconnectDelay 재연결 대기 시간 (기본값)
     * @property maxReconnectAttempts 최대 재연결 시도 횟수
     */
    data class WebSocketProperties(
        val url: String = "wss://api.upbit.com/websocket/v1",
        val privateUrl: String = "wss://api.upbit.com/websocket/v1/private",
        val pingInterval: Duration = Duration.ofSeconds(60),
        val reconnectDelay: Duration = Duration.ofSeconds(5),
        val maxReconnectAttempts: Int = 10,
    )
}
