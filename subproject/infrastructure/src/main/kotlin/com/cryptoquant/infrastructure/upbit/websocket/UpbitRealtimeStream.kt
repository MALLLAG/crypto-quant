package com.cryptoquant.infrastructure.upbit.websocket

import arrow.core.raise.context.either
import com.cryptoquant.domain.common.TradingPair
import com.cryptoquant.domain.gateway.RealtimeStream
import com.cryptoquant.domain.order.OrderEvent
import com.cryptoquant.domain.quotation.Orderbook
import com.cryptoquant.domain.quotation.Ticker
import com.cryptoquant.domain.quotation.Trade
import com.cryptoquant.infrastructure.upbit.mapper.UpbitDomainMapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * RealtimeStream 구현.
 *
 * @see <a href="https://docs.upbit.com/kr/reference/websocket-ticker.md">Upbit WebSocket 현재가</a>
 *
 * WebSocket을 통한 실시간 데이터 스트림 제공.
 *
 * 구독 메시지 형식:
 * [
 *   {"ticket": "unique-ticket-id"},
 *   {"type": "ticker", "codes": ["KRW-BTC", "KRW-ETH"]},
 *   {"format": "DEFAULT"}
 * ]
 */
@Component
class UpbitRealtimeStream(
    private val client: UpbitWebSocketClient,
    private val mapper: UpbitDomainMapper,
) : RealtimeStream {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun subscribeTicker(pairs: List<TradingPair>): Flow<Ticker> {
        val message = buildSubscribeMessage("ticker", pairs)
        return client.connectAndSubscribe(message, authenticated = false)
            .filterIsInstance<UpbitWebSocketMessage.Ticker>()
            .mapNotNull { wsMessage ->
                either { mapper.toTicker(wsMessage) }
                    .onLeft { logger.warn("Failed to convert ticker message: ${it.message}") }
                    .getOrNull()
            }
    }

    override fun subscribeOrderbook(pairs: List<TradingPair>): Flow<Orderbook> {
        val message = buildSubscribeMessage("orderbook", pairs)
        return client.connectAndSubscribe(message, authenticated = false)
            .filterIsInstance<UpbitWebSocketMessage.Orderbook>()
            .mapNotNull { wsMessage ->
                either { mapper.toOrderbook(wsMessage) }
                    .onLeft { logger.warn("Failed to convert orderbook message: ${it.message}") }
                    .getOrNull()
            }
    }

    override fun subscribeTrade(pairs: List<TradingPair>): Flow<Trade> {
        val message = buildSubscribeMessage("trade", pairs)
        return client.connectAndSubscribe(message, authenticated = false)
            .filterIsInstance<UpbitWebSocketMessage.Trade>()
            .mapNotNull { wsMessage ->
                either { mapper.toTrade(wsMessage) }
                    .onLeft { logger.warn("Failed to convert trade message: ${it.message}") }
                    .getOrNull()
            }
    }

    /**
     * 내 주문 및 체결 이벤트 구독.
     *
     * @see <a href="https://docs.upbit.com/kr/reference/websocket-myorder.md">Upbit WebSocket 내 주문</a>
     */
    override fun subscribeMyOrder(): Flow<OrderEvent> {
        val message = buildSubscribeMessage("myOrder", emptyList())
        return client.connectAndSubscribe(message, authenticated = true)
            .filterIsInstance<UpbitWebSocketMessage.MyOrder>()
            .mapNotNull { wsMessage ->
                either { mapper.toOrderEvent(wsMessage) }
                    .onLeft { logger.warn("Failed to convert myOrder message: ${it.message}") }
                    .getOrNull()
            }
    }

    /**
     * 구독 메시지 생성.
     *
     * @param type 데이터 타입 (ticker, trade, orderbook, myOrder 등)
     * @param pairs 구독할 페어 목록 (빈 목록이면 codes 필드 생략)
     */
    private fun buildSubscribeMessage(type: String, pairs: List<TradingPair>): String {
        val ticket = mapOf("ticket" to UUID.randomUUID().toString())
        val subscription = buildMap {
            put("type", type)
            if (pairs.isNotEmpty()) {
                put("codes", pairs.map { it.value })
            }
        }
        val format = mapOf("format" to "DEFAULT")

        return Json.encodeToString(listOf(ticket, subscription, format))
    }
}
