package com.cryptoquant.infrastructure.upbit.websocket

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class UpbitMessageParserTest {

    private val parser = UpbitMessageParser()

    @Test
    fun `Ticker 메시지를 파싱할 수 있다`() {
        val json = """
            {
                "type": "ticker",
                "code": "KRW-BTC",
                "timestamp": 1704067200000,
                "stream_type": "REALTIME",
                "opening_price": 50000000,
                "high_price": 51000000,
                "low_price": 49000000,
                "trade_price": 50500000,
                "prev_closing_price": 50000000,
                "change": "RISE",
                "change_price": 500000,
                "signed_change_price": 500000,
                "change_rate": 0.01,
                "signed_change_rate": 0.01,
                "trade_volume": 0.5,
                "acc_trade_volume_24h": 1000,
                "acc_trade_price_24h": 50000000000,
                "trade_timestamp": 1704067200000
            }
        """.trimIndent()

        val message = parser.parse(json)

        message.shouldBeInstanceOf<UpbitWebSocketMessage.Ticker>()
        val ticker = message as UpbitWebSocketMessage.Ticker
        ticker.type shouldBe "ticker"
        ticker.code shouldBe "KRW-BTC"
        ticker.tradePrice shouldBe BigDecimal("50500000")
        ticker.change shouldBe "RISE"
    }

    @Test
    fun `Trade 메시지를 파싱할 수 있다`() {
        val json = """
            {
                "type": "trade",
                "code": "KRW-BTC",
                "timestamp": 1704067200000,
                "stream_type": "REALTIME",
                "trade_price": 50500000,
                "trade_volume": 0.001,
                "ask_bid": "BID",
                "prev_closing_price": 50000000,
                "change": "RISE",
                "trade_timestamp": 1704067200000,
                "sequential_id": 123456789
            }
        """.trimIndent()

        val message = parser.parse(json)

        message.shouldBeInstanceOf<UpbitWebSocketMessage.Trade>()
        val trade = message as UpbitWebSocketMessage.Trade
        trade.type shouldBe "trade"
        trade.code shouldBe "KRW-BTC"
        trade.tradePrice shouldBe BigDecimal("50500000")
        trade.askBid shouldBe "BID"
        trade.sequentialId shouldBe 123456789L
    }

    @Test
    fun `Orderbook 메시지를 파싱할 수 있다`() {
        val json = """
            {
                "type": "orderbook",
                "code": "KRW-BTC",
                "timestamp": 1704067200000,
                "stream_type": "REALTIME",
                "total_ask_size": 10.5,
                "total_bid_size": 12.3,
                "orderbook_units": [
                    {
                        "ask_price": 50600000,
                        "bid_price": 50500000,
                        "ask_size": 1.5,
                        "bid_size": 2.0
                    },
                    {
                        "ask_price": 50700000,
                        "bid_price": 50400000,
                        "ask_size": 2.0,
                        "bid_size": 1.5
                    }
                ]
            }
        """.trimIndent()

        val message = parser.parse(json)

        message.shouldBeInstanceOf<UpbitWebSocketMessage.Orderbook>()
        val orderbook = message as UpbitWebSocketMessage.Orderbook
        orderbook.type shouldBe "orderbook"
        orderbook.code shouldBe "KRW-BTC"
        orderbook.totalAskSize shouldBe BigDecimal("10.5")
        orderbook.totalBidSize shouldBe BigDecimal("12.3")
        orderbook.orderbookUnits.size shouldBe 2
        orderbook.orderbookUnits[0].askPrice shouldBe BigDecimal("50600000")
    }

    @Test
    fun `MyOrder 메시지를 파싱할 수 있다`() {
        val json = """
            {
                "type": "myOrder",
                "code": "KRW-BTC",
                "timestamp": 1704067200000,
                "stream_type": "REALTIME",
                "uuid": "order-123",
                "ask_bid": "BID",
                "order_type": "limit",
                "state": "wait",
                "price": 50000000,
                "volume": 0.001,
                "remaining_volume": 0.001,
                "executed_volume": 0,
                "trades_count": 0
            }
        """.trimIndent()

        val message = parser.parse(json)

        message.shouldBeInstanceOf<UpbitWebSocketMessage.MyOrder>()
        val myOrder = message as UpbitWebSocketMessage.MyOrder
        myOrder.type shouldBe "myOrder"
        myOrder.code shouldBe "KRW-BTC"
        myOrder.uuid shouldBe "order-123"
        myOrder.askBid shouldBe "BID"
        myOrder.orderType shouldBe "limit"
        myOrder.state shouldBe "wait"
        myOrder.price shouldBe BigDecimal("50000000")
    }

    @Test
    fun `바이너리 메시지를 파싱할 수 있다`() {
        val json = """{"type":"ticker","code":"KRW-BTC","timestamp":1704067200000,"stream_type":"REALTIME","opening_price":50000000,"high_price":51000000,"low_price":49000000,"trade_price":50500000,"prev_closing_price":50000000,"change":"RISE","change_price":500000,"signed_change_price":500000,"change_rate":0.01,"signed_change_rate":0.01,"trade_volume":0.5,"acc_trade_volume_24h":1000,"acc_trade_price_24h":50000000000,"trade_timestamp":1704067200000}"""
        val bytes = json.toByteArray(Charsets.UTF_8)

        val message = parser.parse(bytes)

        message.shouldBeInstanceOf<UpbitWebSocketMessage.Ticker>()
    }

    @Test
    fun `알 수 없는 타입은 예외를 발생시킨다`() {
        val json = """{"type":"unknown","code":"KRW-BTC"}"""

        shouldThrow<IllegalArgumentException> {
            parser.parse(json)
        }
    }

    @Test
    fun `type 필드가 없으면 예외를 발생시킨다`() {
        val json = """{"code":"KRW-BTC"}"""

        shouldThrow<IllegalArgumentException> {
            parser.parse(json)
        }
    }

    @Test
    fun `parseOrNull은 파싱 실패 시 null을 반환한다`() {
        val json = """{"type":"unknown","code":"KRW-BTC"}"""

        val message = parser.parseOrNull(json)

        message shouldBe null
    }

    @Test
    fun `parseOrNull은 유효한 메시지를 파싱한다`() {
        val json = """
            {
                "type": "ticker",
                "code": "KRW-BTC",
                "timestamp": 1704067200000,
                "stream_type": "REALTIME",
                "opening_price": 50000000,
                "high_price": 51000000,
                "low_price": 49000000,
                "trade_price": 50500000,
                "prev_closing_price": 50000000,
                "change": "RISE",
                "change_price": 500000,
                "signed_change_price": 500000,
                "change_rate": 0.01,
                "signed_change_rate": 0.01,
                "trade_volume": 0.5,
                "acc_trade_volume_24h": 1000,
                "acc_trade_price_24h": 50000000000,
                "trade_timestamp": 1704067200000
            }
        """.trimIndent()

        val message = parser.parseOrNull(json)

        message.shouldBeInstanceOf<UpbitWebSocketMessage.Ticker>()
    }

    @Test
    fun `알 수 없는 필드는 무시된다`() {
        val json = """
            {
                "type": "ticker",
                "code": "KRW-BTC",
                "timestamp": 1704067200000,
                "stream_type": "REALTIME",
                "opening_price": 50000000,
                "high_price": 51000000,
                "low_price": 49000000,
                "trade_price": 50500000,
                "prev_closing_price": 50000000,
                "change": "RISE",
                "change_price": 500000,
                "signed_change_price": 500000,
                "change_rate": 0.01,
                "signed_change_rate": 0.01,
                "trade_volume": 0.5,
                "acc_trade_volume_24h": 1000,
                "acc_trade_price_24h": 50000000000,
                "trade_timestamp": 1704067200000,
                "unknown_field": "should be ignored"
            }
        """.trimIndent()

        val message = parser.parse(json)

        message.shouldBeInstanceOf<UpbitWebSocketMessage.Ticker>()
    }

    @Test
    fun `MyOrder 체결 이벤트를 파싱할 수 있다`() {
        val json = """
            {
                "type": "myOrder",
                "code": "KRW-BTC",
                "timestamp": 1704067200000,
                "stream_type": "REALTIME",
                "uuid": "order-123",
                "ask_bid": "BID",
                "order_type": "limit",
                "state": "trade",
                "trade_uuid": "trade-456",
                "price": 50000000,
                "avg_price": 50000000,
                "volume": 0.001,
                "remaining_volume": 0,
                "executed_volume": 0.001,
                "trades_count": 1,
                "paid_fee": 25,
                "executed_funds": 50000,
                "trade_fee": 25,
                "is_maker": false,
                "trade_timestamp": 1704067200000
            }
        """.trimIndent()

        val message = parser.parse(json)

        message.shouldBeInstanceOf<UpbitWebSocketMessage.MyOrder>()
        val myOrder = message as UpbitWebSocketMessage.MyOrder
        myOrder.state shouldBe "trade"
        myOrder.tradeUuid shouldBe "trade-456"
        myOrder.executedVolume shouldBe BigDecimal("0.001")
        myOrder.tradeFee shouldBe BigDecimal("25")
        myOrder.isMaker shouldBe false
    }
}
