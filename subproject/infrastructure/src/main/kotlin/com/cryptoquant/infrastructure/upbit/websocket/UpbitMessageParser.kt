package com.cryptoquant.infrastructure.upbit.websocket

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.charset.Charset

/**
 * Upbit WebSocket 메시지 파서.
 *
 * WebSocket으로 수신한 바이너리 메시지를 [UpbitWebSocketMessage]로 변환합니다.
 *
 * 메시지 형식:
 * - 바이너리 메시지 (UTF-8 인코딩된 JSON)
 * - type 필드로 메시지 유형 구분 (ticker, trade, orderbook, myOrder)
 */
@Component
class UpbitMessageParser {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * 바이너리 메시지를 파싱.
     *
     * @param bytes 바이너리 메시지
     * @return 파싱된 메시지
     * @throws IllegalArgumentException 알 수 없는 메시지 타입
     * @throws kotlinx.serialization.SerializationException 파싱 실패
     */
    fun parse(bytes: ByteArray): UpbitWebSocketMessage {
        val jsonString = bytes.toString(Charset.forName("UTF-8"))
        return parse(jsonString)
    }

    /**
     * JSON 문자열을 파싱.
     *
     * @param jsonString JSON 문자열
     * @return 파싱된 메시지
     * @throws IllegalArgumentException 알 수 없는 메시지 타입
     * @throws kotlinx.serialization.SerializationException 파싱 실패
     */
    fun parse(jsonString: String): UpbitWebSocketMessage {
        val jsonObject = json.decodeFromString<JsonObject>(jsonString)
        val type = jsonObject["type"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing 'type' field in message: $jsonString")

        return when (type) {
            "ticker" -> json.decodeFromString<UpbitWebSocketMessage.Ticker>(jsonString)
            "trade" -> json.decodeFromString<UpbitWebSocketMessage.Trade>(jsonString)
            "orderbook" -> json.decodeFromString<UpbitWebSocketMessage.Orderbook>(jsonString)
            "myOrder" -> json.decodeFromString<UpbitWebSocketMessage.MyOrder>(jsonString)
            else -> {
                logger.warn("Unknown message type: $type")
                throw IllegalArgumentException("Unknown message type: $type")
            }
        }
    }

    /**
     * 바이너리 메시지를 안전하게 파싱.
     *
     * 파싱 실패 시 null을 반환합니다.
     *
     * @param bytes 바이너리 메시지
     * @return 파싱된 메시지 또는 null
     */
    fun parseOrNull(bytes: ByteArray): UpbitWebSocketMessage? =
        runCatching { parse(bytes) }
            .onFailure { logger.error("Failed to parse message", it) }
            .getOrNull()

    /**
     * JSON 문자열을 안전하게 파싱.
     *
     * 파싱 실패 시 null을 반환합니다.
     *
     * @param jsonString JSON 문자열
     * @return 파싱된 메시지 또는 null
     */
    fun parseOrNull(jsonString: String): UpbitWebSocketMessage? =
        runCatching { parse(jsonString) }
            .onFailure { logger.error("Failed to parse message: $jsonString", it) }
            .getOrNull()
}
