package com.cryptoquant.infrastructure.upbit.websocket

import com.cryptoquant.infrastructure.upbit.client.UpbitAuthInterceptor
import com.cryptoquant.infrastructure.upbit.config.UpbitProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.retryWhen
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * Upbit WebSocket 클라이언트.
 *
 * @see <a href="https://docs.upbit.com/kr/reference/websocket-guide.md">Upbit WebSocket 안내</a>
 *
 * 연결 정보:
 * - Public: wss://api.upbit.com/websocket/v1 (시세 데이터)
 * - Private: wss://api.upbit.com/websocket/v1/private (내 주문/자산)
 *
 * 특징:
 * - 자동 재연결 (exponential backoff)
 * - 120초 무활동 시 자동 종료 (Ping/Pong으로 유지)
 * - 메시지 파싱 및 Flow 변환
 *
 * 재연결 정책:
 * - 클라이언트가 명시적으로 종료한 경우 (code=1000): 재연결 안함
 * - 서버가 종료하거나 오류 발생: 재연결 시도
 */
@Component
class UpbitWebSocketClient(
    private val properties: UpbitProperties,
    private val authInterceptor: UpbitAuthInterceptor,
    private val parser: UpbitMessageParser,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * WebSocket 연결 및 구독.
     *
     * @param subscribeMessage 구독 메시지 (JSON 배열 형식)
     * @param authenticated 인증 필요 여부
     * @return 메시지 Flow
     */
    fun connectAndSubscribe(
        subscribeMessage: String,
        authenticated: Boolean = false,
    ): Flow<UpbitWebSocketMessage> =
        connect(authenticated, subscribeMessage)
            .retryWithBackoff()

    private fun connect(
        authenticated: Boolean,
        subscribeMessage: String,
    ): Flow<UpbitWebSocketMessage> = callbackFlow {
        val url = if (authenticated) {
            properties.websocket.privateUrl
        } else {
            properties.websocket.url
        }

        val client = OkHttpClient.Builder()
            .pingInterval(properties.websocket.pingInterval.toMillis(), TimeUnit.MILLISECONDS)
            .build()

        val requestBuilder = Request.Builder().url(url)
        if (authenticated) {
            val token = authInterceptor.generateToken()
            requestBuilder.header("Authorization", "Bearer $token")
        }

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                logger.info("WebSocket connected: $url")
                val sent = webSocket.send(subscribeMessage)
                if (sent) {
                    logger.debug("Subscription message sent: $subscribeMessage")
                } else {
                    logger.error("Failed to send subscription message")
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val message = parser.parseOrNull(bytes.toByteArray())
                if (message != null) {
                    trySend(message)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = parser.parseOrNull(text)
                if (message != null) {
                    trySend(message)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                logger.error("WebSocket error", t)
                close(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                logger.info("WebSocket closed: $code $reason")
                if (code != NORMAL_CLOSURE_CODE) {
                    close(WebSocketClosedException(code, reason))
                } else {
                    close()
                }
            }
        }

        val webSocket = client.newWebSocket(requestBuilder.build(), listener)

        awaitClose {
            logger.info("Closing WebSocket connection")
            webSocket.close(NORMAL_CLOSURE_CODE, "Client closed")
            client.dispatcher.executorService.shutdown()
        }
    }

    private fun <T> Flow<T>.retryWithBackoff(): Flow<T> = retryWhen { cause, attempt ->
        if (cause is CancellationException) {
            return@retryWhen false
        }

        if (attempt >= properties.websocket.maxReconnectAttempts) {
            logger.error("Max reconnect attempts (${properties.websocket.maxReconnectAttempts}) reached", cause)
            return@retryWhen false
        }

        val multiplier = (1L shl attempt.toInt().coerceAtMost(MAX_BACKOFF_POWER))
        val delayMs = properties.websocket.reconnectDelay.toMillis() * multiplier
        logger.warn("Reconnecting in ${delayMs}ms (attempt ${attempt + 1})", cause)
        delay(delayMs)
        true
    }

    companion object {
        private const val NORMAL_CLOSURE_CODE = 1000
        private const val MAX_BACKOFF_POWER = 5
    }
}

/**
 * WebSocket이 서버에 의해 종료되었을 때 발생하는 예외.
 *
 * 재연결 로직을 트리거하기 위해 사용됩니다.
 */
class WebSocketClosedException(
    val code: Int,
    val reason: String,
) : Exception("WebSocket closed by server: $code $reason")
