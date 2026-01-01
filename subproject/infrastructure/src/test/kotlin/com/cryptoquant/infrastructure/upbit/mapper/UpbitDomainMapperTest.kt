package com.cryptoquant.infrastructure.upbit.mapper

import arrow.core.raise.context.either
import com.cryptoquant.domain.common.DomainError
import com.cryptoquant.domain.common.TradingPair
import com.cryptoquant.domain.gateway.GatewayError
import com.cryptoquant.domain.order.OrderEvent
import com.cryptoquant.domain.order.OrderSide
import com.cryptoquant.domain.order.OrderState
import com.cryptoquant.domain.quotation.AskBid
import com.cryptoquant.domain.quotation.CandleUnit
import com.cryptoquant.domain.quotation.Change
import com.cryptoquant.infrastructure.upbit.dto.response.UpbitBalanceResponse
import com.cryptoquant.infrastructure.upbit.dto.response.UpbitCandleResponse
import com.cryptoquant.infrastructure.upbit.dto.response.UpbitOrderResponse
import com.cryptoquant.infrastructure.upbit.dto.response.UpbitOrderbookResponse
import com.cryptoquant.infrastructure.upbit.dto.response.UpbitTickerResponse
import com.cryptoquant.infrastructure.upbit.dto.response.UpbitTradeResponse
import com.cryptoquant.infrastructure.upbit.websocket.UpbitWebSocketMessage
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class UpbitDomainMapperTest {

    private val mapper = UpbitDomainMapper()

    // ===== Candle =====

    @Test
    fun `캔들 응답을 도메인 모델로 변환할 수 있다`() {
        // Given
        val response = UpbitCandleResponse(
            market = "KRW-BTC",
            candleDateTimeUtc = "2024-01-01T00:00:00",
            candleDateTimeKst = "2024-01-01T09:00:00",
            openingPrice = BigDecimal("50000000"),
            highPrice = BigDecimal("51000000"),
            lowPrice = BigDecimal("49000000"),
            tradePrice = BigDecimal("50500000"),
            timestamp = 1704067200000,
            candleAccTradePrice = BigDecimal("1000000000"),
            candleAccTradeVolume = BigDecimal("20"),
            unit = 1,
        )
        val pair = either<DomainError, TradingPair> { TradingPair("KRW-BTC") }.getOrNull()!!
        val unit = either<DomainError, CandleUnit> { CandleUnit.Minutes(1) }.getOrNull()!!

        // When
        val result = either<GatewayError, _> { mapper.toCandle(response, pair, unit) }

        // Then
        val candle = result.shouldBeRight()
        candle.pair.value shouldBe "KRW-BTC"
        candle.openingPrice.value shouldBe BigDecimal("50000000")
        candle.highPrice.value shouldBe BigDecimal("51000000")
        candle.lowPrice.value shouldBe BigDecimal("49000000")
        candle.closingPrice.value shouldBe BigDecimal("50500000")
    }

    // ===== Ticker =====

    @Test
    fun `현재가 응답을 도메인 모델로 변환할 수 있다`() {
        // Given
        val response = UpbitTickerResponse(
            market = "KRW-BTC",
            tradePrice = BigDecimal("50500000"),
            openingPrice = BigDecimal("50000000"),
            highPrice = BigDecimal("51000000"),
            lowPrice = BigDecimal("49000000"),
            prevClosingPrice = BigDecimal("50000000"),
            change = "RISE",
            changePrice = BigDecimal("500000"),
            signedChangePrice = BigDecimal("500000"),
            changeRate = BigDecimal("0.01"),
            signedChangeRate = BigDecimal("0.01"),
            tradeVolume = BigDecimal("0.5"),
            accTradePrice24h = BigDecimal("50000000000"),
            accTradeVolume24h = BigDecimal("1000"),
            timestamp = 1704067200000,
        )

        // When
        val result = either<GatewayError, _> { mapper.toTicker(response) }

        // Then
        val ticker = result.shouldBeRight()
        ticker.pair.value shouldBe "KRW-BTC"
        ticker.tradePrice.value shouldBe BigDecimal("50500000")
        ticker.change shouldBe Change.RISE
    }

    // ===== Orderbook =====

    @Test
    fun `호가 응답을 도메인 모델로 변환할 수 있다`() {
        // Given
        val response = UpbitOrderbookResponse(
            market = "KRW-BTC",
            timestamp = 1704067200000,
            totalAskSize = BigDecimal("10.5"),
            totalBidSize = BigDecimal("12.3"),
            orderbookUnits = listOf(
                UpbitOrderbookResponse.OrderbookUnitDto(
                    askPrice = BigDecimal("50600000"),
                    bidPrice = BigDecimal("50500000"),
                    askSize = BigDecimal("1.5"),
                    bidSize = BigDecimal("2.0"),
                )
            ),
        )

        // When
        val result = either<GatewayError, _> { mapper.toOrderbook(response) }

        // Then
        val orderbook = result.shouldBeRight()
        orderbook.pair.value shouldBe "KRW-BTC"
        orderbook.totalAskSize.value shouldBe BigDecimal("10.5")
        orderbook.orderbookUnits.size shouldBe 1
    }

    // ===== Trade =====

    @Test
    fun `체결 응답을 도메인 모델로 변환할 수 있다`() {
        // Given
        val response = UpbitTradeResponse(
            market = "KRW-BTC",
            tradeDateUtc = "2024-01-01",
            tradeTimeUtc = "00:00:00",
            timestamp = 1704067200000,
            tradePrice = BigDecimal("50500000"),
            tradeVolume = BigDecimal("0.001"),
            prevClosingPrice = BigDecimal("50000000"),
            changePrice = BigDecimal("500000"),
            askBid = "BID",
            change = "RISE",
            sequentialId = 123456789L,
        )
        val pair = either<DomainError, TradingPair> { TradingPair("KRW-BTC") }.getOrNull()!!

        // When
        val result = either<GatewayError, _> { mapper.toTrade(response, pair) }

        // Then
        val trade = result.shouldBeRight()
        trade.pair.value shouldBe "KRW-BTC"
        trade.tradePrice.value shouldBe BigDecimal("50500000")
        trade.askBid shouldBe AskBid.BID
        trade.change shouldBe Change.RISE
    }

    // ===== Order =====

    @Test
    fun `주문 응답을 도메인 모델로 변환할 수 있다`() {
        // Given
        val response = UpbitOrderResponse(
            uuid = "order-123",
            side = "bid",
            ordType = "limit",
            price = "50000000",
            state = "wait",
            market = "KRW-BTC",
            createdAt = "2024-01-01T00:00:00",
            volume = "0.001",
            remainingVolume = "0.001",
            executedVolume = "0",
            tradesCount = 0,
            paidFee = "0",
            executedFunds = null,
        )

        // When
        val result = either<GatewayError, _> { mapper.toOrder(response) }

        // Then
        val order = result.shouldBeRight()
        order.id.value shouldBe "order-123"
        order.pair.value shouldBe "KRW-BTC"
        order.side shouldBe OrderSide.BID
        order.state shouldBe OrderState.WAIT
    }

    // ===== Balance =====

    @Test
    fun `잔고 응답을 도메인 모델로 변환할 수 있다`() {
        // Given
        val response = UpbitBalanceResponse(
            currency = "BTC",
            balance = "1.5",
            locked = "0.5",
            avgBuyPrice = BigDecimal("50000000"),
            avgBuyPriceModified = false,
            unitCurrency = "KRW",
        )

        // When
        val result = either<GatewayError, _> { mapper.toBalance(response) }

        // Then
        val balance = result.shouldBeRight()
        balance.shouldNotBeNull()
        balance.currency.value shouldBe "BTC"
        balance.balance.value.compareTo(BigDecimal("1.5")) shouldBe 0
        balance.locked.value.compareTo(BigDecimal("0.5")) shouldBe 0
    }

    @Test
    fun `잔고가 0인 경우 null을 반환한다`() {
        // Given
        val response = UpbitBalanceResponse(
            currency = "BTC",
            balance = "0",
            locked = "0",
            avgBuyPrice = BigDecimal.ZERO,
            avgBuyPriceModified = false,
            unitCurrency = "KRW",
        )

        // When
        val result = either<GatewayError, _> { mapper.toBalance(response) }

        // Then
        result.shouldBeRight().shouldBeNull()
    }

    // ===== WebSocket Messages =====

    @Test
    fun `WebSocket 현재가 메시지를 도메인 모델로 변환할 수 있다`() {
        // Given
        val message = UpbitWebSocketMessage.Ticker(
            type = "ticker",
            code = "KRW-BTC",
            timestamp = 1704067200000,
            streamType = "REALTIME",
            openingPrice = BigDecimal("50000000"),
            highPrice = BigDecimal("51000000"),
            lowPrice = BigDecimal("49000000"),
            tradePrice = BigDecimal("50500000"),
            prevClosingPrice = BigDecimal("50000000"),
            change = "RISE",
            changePrice = BigDecimal("500000"),
            signedChangePrice = BigDecimal("500000"),
            changeRate = BigDecimal("0.01"),
            signedChangeRate = BigDecimal("0.01"),
            tradeVolume = BigDecimal("0.5"),
            accTradeVolume24h = BigDecimal("1000"),
            accTradePrice24h = BigDecimal("50000000000"),
            tradeTimestamp = 1704067200000,
        )

        // When
        val result = either<GatewayError, _> { mapper.toTicker(message) }

        // Then
        val ticker = result.shouldBeRight()
        ticker.pair.value shouldBe "KRW-BTC"
        ticker.tradePrice.value shouldBe BigDecimal("50500000")
    }

    @Test
    fun `WebSocket 호가 메시지를 도메인 모델로 변환할 수 있다`() {
        // Given
        val message = UpbitWebSocketMessage.Orderbook(
            type = "orderbook",
            code = "KRW-BTC",
            timestamp = 1704067200000,
            streamType = "REALTIME",
            totalAskSize = BigDecimal("10.5"),
            totalBidSize = BigDecimal("12.3"),
            orderbookUnits = listOf(
                UpbitWebSocketMessage.Orderbook.OrderbookUnit(
                    askPrice = BigDecimal("50600000"),
                    bidPrice = BigDecimal("50500000"),
                    askSize = BigDecimal("1.5"),
                    bidSize = BigDecimal("2.0"),
                )
            ),
        )

        // When
        val result = either<GatewayError, _> { mapper.toOrderbook(message) }

        // Then
        val orderbook = result.shouldBeRight()
        orderbook.pair.value shouldBe "KRW-BTC"
        orderbook.totalAskSize.value shouldBe BigDecimal("10.5")
    }

    @Test
    fun `WebSocket 체결 메시지를 도메인 모델로 변환할 수 있다`() {
        // Given
        val message = UpbitWebSocketMessage.Trade(
            type = "trade",
            code = "KRW-BTC",
            timestamp = 1704067200000,
            streamType = "REALTIME",
            tradePrice = BigDecimal("50500000"),
            tradeVolume = BigDecimal("0.001"),
            askBid = "BID",
            prevClosingPrice = BigDecimal("50000000"),
            change = "RISE",
            tradeTimestamp = 1704067200000,
            sequentialId = 123456789L,
        )

        // When
        val result = either<GatewayError, _> { mapper.toTrade(message) }

        // Then
        val trade = result.shouldBeRight()
        trade.pair.value shouldBe "KRW-BTC"
        trade.askBid shouldBe AskBid.BID
    }

    @Test
    fun `WebSocket 내 주문 대기 메시지를 OrderCreated 이벤트로 변환할 수 있다`() {
        // Given
        val message = UpbitWebSocketMessage.MyOrder(
            type = "myOrder",
            code = "KRW-BTC",
            timestamp = 1704067200000,
            streamType = "REALTIME",
            uuid = "order-123",
            askBid = "BID",
            orderType = "limit",
            state = "wait",
            price = BigDecimal("50000000"),
            volume = BigDecimal("0.001"),
        )

        // When
        val result = either<GatewayError, _> { mapper.toOrderEvent(message) }

        // Then
        val event = result.shouldBeRight()
        event.shouldBeInstanceOf<OrderEvent.OrderCreated>()
        event.orderId.value shouldBe "order-123"
    }

    @Test
    fun `WebSocket 내 주문 체결 메시지를 OrderExecuted 이벤트로 변환할 수 있다`() {
        // Given
        val message = UpbitWebSocketMessage.MyOrder(
            type = "myOrder",
            code = "KRW-BTC",
            timestamp = 1704067200000,
            streamType = "REALTIME",
            uuid = "order-123",
            askBid = "BID",
            orderType = "limit",
            state = "trade",
            tradeUuid = "trade-456",
            price = BigDecimal("50000000"),
            volume = BigDecimal("0.001"),
            tradeFee = BigDecimal("25"),
        )

        // When
        val result = either<GatewayError, _> { mapper.toOrderEvent(message) }

        // Then
        val event = result.shouldBeRight()
        event.shouldBeInstanceOf<OrderEvent.OrderExecuted>()
        (event as OrderEvent.OrderExecuted).tradeId.value shouldBe "trade-456"
    }

    @Test
    fun `WebSocket 내 주문 취소 메시지를 OrderCancelled 이벤트로 변환할 수 있다`() {
        // Given
        val message = UpbitWebSocketMessage.MyOrder(
            type = "myOrder",
            code = "KRW-BTC",
            timestamp = 1704067200000,
            streamType = "REALTIME",
            uuid = "order-123",
            askBid = "BID",
            orderType = "limit",
            state = "cancel",
        )

        // When
        val result = either<GatewayError, _> { mapper.toOrderEvent(message) }

        // Then
        val event = result.shouldBeRight()
        event.shouldBeInstanceOf<OrderEvent.OrderCancelled>()
        event.orderId.value shouldBe "order-123"
    }
}
