package com.cryptoquant.infrastructure.upbit.mapper

import arrow.core.raise.Raise
import arrow.core.raise.context.withError
import com.cryptoquant.domain.account.Balance
import com.cryptoquant.domain.account.Currency
import com.cryptoquant.domain.account.OrderChance
import com.cryptoquant.domain.common.Amount
import com.cryptoquant.domain.common.AvgBuyPrice
import com.cryptoquant.domain.common.ChangeRate
import com.cryptoquant.domain.common.DomainError
import com.cryptoquant.domain.common.FeeRate
import com.cryptoquant.domain.common.Price
import com.cryptoquant.domain.common.PriceChange
import com.cryptoquant.domain.common.TradeSequentialId
import com.cryptoquant.domain.common.TradingPair
import com.cryptoquant.domain.common.Volume
import com.cryptoquant.domain.gateway.GatewayError
import com.cryptoquant.domain.order.Order
import com.cryptoquant.domain.order.OrderError
import com.cryptoquant.domain.order.OrderEvent
import com.cryptoquant.domain.order.OrderId
import com.cryptoquant.domain.order.OrderSide
import com.cryptoquant.domain.order.OrderState
import com.cryptoquant.domain.order.OrderType
import com.cryptoquant.domain.order.TradeId
import com.cryptoquant.domain.quotation.AskBid
import com.cryptoquant.domain.quotation.Candle
import com.cryptoquant.domain.quotation.CandleUnit
import com.cryptoquant.domain.quotation.Change
import com.cryptoquant.domain.quotation.Orderbook
import com.cryptoquant.domain.quotation.OrderbookUnit
import com.cryptoquant.domain.quotation.Ticker
import com.cryptoquant.domain.quotation.Trade
import com.cryptoquant.infrastructure.upbit.dto.response.UpbitBalanceResponse
import com.cryptoquant.infrastructure.upbit.dto.response.UpbitCandleResponse
import com.cryptoquant.infrastructure.upbit.dto.response.UpbitOrderChanceResponse
import com.cryptoquant.infrastructure.upbit.dto.response.UpbitOrderResponse
import com.cryptoquant.infrastructure.upbit.dto.response.UpbitOrderbookResponse
import com.cryptoquant.infrastructure.upbit.dto.response.UpbitTickerResponse
import com.cryptoquant.infrastructure.upbit.dto.response.UpbitTradeResponse
import com.cryptoquant.infrastructure.upbit.websocket.UpbitWebSocketMessage
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Upbit API 응답 DTO를 도메인 모델로 변환하는 매퍼.
 */
@Component
class UpbitDomainMapper {

    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    // ===== Quotation =====

    /**
     * 캔들 응답을 도메인 모델로 변환.
     */
    context(_: Raise<GatewayError>)
    fun toCandle(response: UpbitCandleResponse, pair: TradingPair, unit: CandleUnit): Candle =
        withError<GatewayError, DomainError, Candle>({ toInvalidResponse(it) }) {
            Candle(
                pair = pair,
                unit = unit,
                timestamp = Instant.ofEpochMilli(response.timestamp),
                openingPrice = Price(response.openingPrice),
                highPrice = Price(response.highPrice),
                lowPrice = Price(response.lowPrice),
                closingPrice = Price(response.tradePrice),
                volume = Volume(response.candleAccTradeVolume),
                amount = Amount(response.candleAccTradePrice),
            )
        }

    /**
     * 현재가 응답을 도메인 모델로 변환.
     */
    context(_: Raise<GatewayError>)
    fun toTicker(response: UpbitTickerResponse): Ticker =
        withError<GatewayError, DomainError, Ticker>({ toInvalidResponse(it) }) {
            val pair = TradingPair(response.market)
            Ticker(
                pair = pair,
                tradePrice = Price(response.tradePrice),
                openingPrice = Price(response.openingPrice),
                highPrice = Price(response.highPrice),
                lowPrice = Price(response.lowPrice),
                prevClosingPrice = Price(response.prevClosingPrice),
                change = parseChange(response.change),
                changePrice = PriceChange(response.changePrice),
                changeRate = ChangeRate(response.changeRate),
                signedChangePrice = PriceChange(response.signedChangePrice),
                signedChangeRate = ChangeRate(response.signedChangeRate),
                tradeVolume = Volume(response.tradeVolume),
                accTradePrice24h = Amount(response.accTradePrice24h),
                accTradeVolume24h = Volume(response.accTradeVolume24h),
                timestamp = Instant.ofEpochMilli(response.timestamp),
            )
        }

    /**
     * 호가창 응답을 도메인 모델로 변환.
     */
    context(_: Raise<GatewayError>)
    fun toOrderbook(response: UpbitOrderbookResponse): Orderbook =
        withError<GatewayError, DomainError, Orderbook>({ toInvalidResponse(it) }) {
            val pair = TradingPair(response.market)
            Orderbook(
                pair = pair,
                timestamp = Instant.ofEpochMilli(response.timestamp),
                totalAskSize = Volume(response.totalAskSize),
                totalBidSize = Volume(response.totalBidSize),
                orderbookUnits = response.orderbookUnits.map { unit ->
                    OrderbookUnit(
                        askPrice = Price(unit.askPrice),
                        bidPrice = Price(unit.bidPrice),
                        askSize = Volume(unit.askSize),
                        bidSize = Volume(unit.bidSize),
                    )
                },
            )
        }

    /**
     * 체결 내역 응답을 도메인 모델로 변환.
     */
    context(_: Raise<GatewayError>)
    fun toTrade(response: UpbitTradeResponse, pair: TradingPair): Trade =
        withError<GatewayError, DomainError, Trade>({ toInvalidResponse(it) }) {
            Trade(
                pair = pair,
                tradePrice = Price(response.tradePrice),
                tradeVolume = Volume(response.tradeVolume),
                askBid = parseAskBid(response.askBid),
                prevClosingPrice = Price(response.prevClosingPrice),
                change = parseChange(response.change),
                timestamp = Instant.ofEpochMilli(response.timestamp),
                sequentialId = TradeSequentialId(response.sequentialId),
            )
        }

    // ===== WebSocket Messages =====

    /**
     * WebSocket 현재가 메시지를 도메인 모델로 변환.
     */
    context(_: Raise<GatewayError>)
    fun toTicker(message: UpbitWebSocketMessage.Ticker): Ticker =
        withError<GatewayError, DomainError, Ticker>({ toInvalidResponse(it) }) {
            val pair = TradingPair(message.code)
            Ticker(
                pair = pair,
                tradePrice = Price(message.tradePrice),
                openingPrice = Price(message.openingPrice),
                highPrice = Price(message.highPrice),
                lowPrice = Price(message.lowPrice),
                prevClosingPrice = Price(message.prevClosingPrice),
                change = parseChange(message.change),
                changePrice = PriceChange(message.changePrice),
                changeRate = ChangeRate(message.changeRate),
                signedChangePrice = PriceChange(message.signedChangePrice),
                signedChangeRate = ChangeRate(message.signedChangeRate),
                tradeVolume = Volume(message.tradeVolume),
                accTradePrice24h = Amount(message.accTradePrice24h),
                accTradeVolume24h = Volume(message.accTradeVolume24h),
                timestamp = Instant.ofEpochMilli(message.timestamp),
            )
        }

    /**
     * WebSocket 호가 메시지를 도메인 모델로 변환.
     */
    context(_: Raise<GatewayError>)
    fun toOrderbook(message: UpbitWebSocketMessage.Orderbook): Orderbook =
        withError<GatewayError, DomainError, Orderbook>({ toInvalidResponse(it) }) {
            val pair = TradingPair(message.code)
            Orderbook(
                pair = pair,
                timestamp = Instant.ofEpochMilli(message.timestamp),
                totalAskSize = Volume(message.totalAskSize),
                totalBidSize = Volume(message.totalBidSize),
                orderbookUnits = message.orderbookUnits.map { unit ->
                    OrderbookUnit(
                        askPrice = Price(unit.askPrice),
                        bidPrice = Price(unit.bidPrice),
                        askSize = Volume(unit.askSize),
                        bidSize = Volume(unit.bidSize),
                    )
                },
            )
        }

    /**
     * WebSocket 체결 메시지를 도메인 모델로 변환.
     */
    context(_: Raise<GatewayError>)
    fun toTrade(message: UpbitWebSocketMessage.Trade): Trade =
        withError<GatewayError, DomainError, Trade>({ toInvalidResponse(it) }) {
            val pair = TradingPair(message.code)
            Trade(
                pair = pair,
                tradePrice = Price(message.tradePrice),
                tradeVolume = Volume(message.tradeVolume),
                askBid = parseAskBid(message.askBid),
                prevClosingPrice = Price(message.prevClosingPrice),
                change = parseChange(message.change),
                timestamp = Instant.ofEpochMilli(message.timestamp),
                sequentialId = TradeSequentialId(message.sequentialId),
            )
        }

    /**
     * WebSocket 내 주문 메시지를 도메인 이벤트로 변환.
     *
     * state에 따라 다른 이벤트 타입을 반환:
     * - wait, watch: OrderCreated
     * - trade: OrderExecuted (체결 발생)
     * - done: OrderExecuted (전량 체결 완료)
     * - cancel, prevented: OrderCancelled
     */
    context(_: Raise<GatewayError>)
    fun toOrderEvent(message: UpbitWebSocketMessage.MyOrder): OrderEvent {
        val orderId = withError<GatewayError, DomainError, OrderId>({ toInvalidResponse(it) }) {
            OrderId(message.uuid)
        }
        val occurredAt = Instant.ofEpochMilli(message.timestamp)

        return when (message.state.lowercase()) {
            "wait", "watch" -> {
                val pair = withError<GatewayError, DomainError, TradingPair>({ toInvalidResponse(it) }) {
                    TradingPair(message.code)
                }
                val side = parseSide(message.askBid)
                val orderType = parseWebSocketOrderType(message)

                OrderEvent.OrderCreated(
                    orderId = orderId,
                    pair = pair,
                    side = side,
                    orderType = orderType,
                    occurredAt = occurredAt,
                )
            }
            "trade", "done" -> {
                val tradeId = withError<GatewayError, OrderError, TradeId>({ toOrderError(it) }) {
                    TradeId(message.tradeUuid ?: message.uuid)
                }
                val executedVolume = withError<GatewayError, DomainError, Volume>({ toInvalidResponse(it) }) {
                    Volume(message.volume ?: BigDecimal.ZERO)
                }
                val executedPrice = withError<GatewayError, DomainError, Price>({ toInvalidResponse(it) }) {
                    Price(message.price ?: message.avgPrice ?: BigDecimal.ONE)
                }
                val fee = withError<GatewayError, DomainError, Amount>({ toInvalidResponse(it) }) {
                    Amount(message.tradeFee ?: message.paidFee ?: BigDecimal.ZERO)
                }

                OrderEvent.OrderExecuted(
                    orderId = orderId,
                    tradeId = tradeId,
                    executedVolume = executedVolume,
                    executedPrice = executedPrice,
                    fee = fee,
                    occurredAt = occurredAt,
                )
            }
            "cancel", "prevented" -> {
                OrderEvent.OrderCancelled(
                    orderId = orderId,
                    occurredAt = occurredAt,
                )
            }
            else -> {
                // 알 수 없는 상태는 OrderCreated로 처리 (안전한 기본값)
                val pair = withError<GatewayError, DomainError, TradingPair>({ toInvalidResponse(it) }) {
                    TradingPair(message.code)
                }
                val side = parseSide(message.askBid)
                val orderType = parseWebSocketOrderType(message)

                OrderEvent.OrderCreated(
                    orderId = orderId,
                    pair = pair,
                    side = side,
                    orderType = orderType,
                    occurredAt = occurredAt,
                )
            }
        }
    }

    /**
     * WebSocket 주문 메시지에서 OrderType 파싱.
     */
    context(_: Raise<GatewayError>)
    private fun parseWebSocketOrderType(message: UpbitWebSocketMessage.MyOrder): OrderType {
        val volumeVal = message.volume ?: BigDecimal.ZERO
        val priceVal = message.price ?: BigDecimal.ZERO

        val volume = withError<GatewayError, DomainError, Volume>({ toInvalidResponse(it) }) {
            Volume(volumeVal)
        }
        val price = if (priceVal > BigDecimal.ZERO) {
            withError<GatewayError, DomainError, Price>({ toInvalidResponse(it) }) {
                Price(priceVal)
            }
        } else {
            withError<GatewayError, DomainError, Price>({ toInvalidResponse(it) }) {
                Price(BigDecimal.ONE)
            }
        }
        val amount = withError<GatewayError, DomainError, Amount>({ toInvalidResponse(it) }) {
            Amount(priceVal)
        }

        return withError<GatewayError, OrderError, OrderType>({ toOrderError(it) }) {
            when (message.orderType.lowercase()) {
                "limit" -> OrderType.Limit(volume, price)
                "price" -> OrderType.MarketBuy(amount)
                "market" -> OrderType.MarketSell(volume)
                "best" -> OrderType.Best(volume)
                else -> OrderType.Limit(volume, price)
            }
        }
    }

    // ===== Exchange =====

    /**
     * 주문 응답을 도메인 모델로 변환.
     */
    context(_: Raise<GatewayError>)
    fun toOrder(response: UpbitOrderResponse): Order {
        val pair = withError<GatewayError, DomainError, TradingPair>({ toInvalidResponse(it) }) {
            TradingPair(response.market)
        }
        val side = parseSide(response.side)
        val state = parseState(response.state)
        val orderType = parseOrderType(response)
        val orderId = withError<GatewayError, DomainError, OrderId>({ toInvalidResponse(it) }) {
            OrderId(response.uuid)
        }
        val remainingVolume = withError<GatewayError, DomainError, Volume>({ toInvalidResponse(it) }) {
            Volume(response.remainingVolume.toBigDecimal())
        }
        val executedVolume = withError<GatewayError, DomainError, Volume>({ toInvalidResponse(it) }) {
            Volume(response.executedVolume.toBigDecimal())
        }
        val executedAmount = withError<GatewayError, DomainError, Amount>({ toInvalidResponse(it) }) {
            val funds = response.executedFunds?.toBigDecimalOrNull()
                ?: calculateExecutedAmount(response)
            Amount(funds)
        }
        val paidFee = withError<GatewayError, DomainError, Amount>({ toInvalidResponse(it) }) {
            Amount(response.paidFee.toBigDecimal())
        }
        val createdAt = parseDateTime(response.createdAt)
        val doneAt = if (state == OrderState.DONE || state == OrderState.CANCEL) {
            createdAt
        } else {
            null
        }

        return withError<GatewayError, OrderError, Order>({ toOrderError(it) }) {
            Order(
                id = orderId,
                pair = pair,
                side = side,
                orderType = orderType,
                state = state,
                remainingVolume = remainingVolume,
                executedVolume = executedVolume,
                executedAmount = executedAmount,
                paidFee = paidFee,
                createdAt = createdAt,
                doneAt = doneAt,
            )
        }
    }

    /**
     * 잔고 응답을 도메인 모델로 변환.
     * 잔고가 0인 경우 null 반환.
     */
    context(_: Raise<GatewayError>)
    fun toBalance(response: UpbitBalanceResponse): Balance? =
        withError<GatewayError, DomainError, Balance?>({ toInvalidResponse(it) }) {
            val balance = response.balance.toBigDecimal()
            val locked = response.locked.toBigDecimal()
            if (balance.compareTo(BigDecimal.ZERO) == 0 && locked.compareTo(BigDecimal.ZERO) == 0) {
                return@withError null
            }

            Balance(
                currency = Currency(response.currency),
                balance = Volume(balance),
                locked = Volume(locked),
                avgBuyPrice = AvgBuyPrice(response.avgBuyPrice),
                avgBuyPriceModified = response.avgBuyPriceModified,
            )
        }

    /**
     * 주문 가능 정보 응답을 도메인 모델로 변환.
     */
    context(_: Raise<GatewayError>)
    fun toOrderChance(response: UpbitOrderChanceResponse, pair: TradingPair): OrderChance =
        withError<GatewayError, DomainError, OrderChance>({ toInvalidResponse(it) }) {
            val bidFee = FeeRate(response.bidFee)
            val askFee = FeeRate(response.askFee)
            val bidAccount = Balance(
                currency = Currency(response.bidAccount.currency),
                balance = Volume(response.bidAccount.balance),
                locked = Volume(response.bidAccount.locked),
                avgBuyPrice = AvgBuyPrice(response.bidAccount.avgBuyPrice),
                avgBuyPriceModified = response.bidAccount.avgBuyPriceModified,
            )
            val askAccount = Balance(
                currency = Currency(response.askAccount.currency),
                balance = Volume(response.askAccount.balance),
                locked = Volume(response.askAccount.locked),
                avgBuyPrice = AvgBuyPrice(response.askAccount.avgBuyPrice),
                avgBuyPriceModified = response.askAccount.avgBuyPriceModified,
            )
            val minOrderAmount = Amount(response.market.bid.minTotal.toBigDecimal())

            OrderChance(
                pair = pair,
                bidFee = bidFee,
                askFee = askFee,
                bidAccount = bidAccount,
                askAccount = askAccount,
                minOrderAmount = minOrderAmount,
            )
        }

    // ===== Private Helpers =====

    private fun parseChange(change: String): Change = when (change.uppercase()) {
        "RISE" -> Change.RISE
        "FALL" -> Change.FALL
        else -> Change.EVEN
    }

    private fun parseAskBid(askBid: String): AskBid = when (askBid.uppercase()) {
        "ASK" -> AskBid.ASK
        else -> AskBid.BID
    }

    private fun parseSide(side: String): OrderSide = when (side.lowercase()) {
        "bid" -> OrderSide.BID
        else -> OrderSide.ASK
    }

    private fun parseState(state: String): OrderState = when (state.lowercase()) {
        "wait" -> OrderState.WAIT
        "watch" -> OrderState.WATCH
        "done" -> OrderState.DONE
        "cancel" -> OrderState.CANCEL
        else -> OrderState.WAIT
    }

    context(_: Raise<GatewayError>)
    private fun parseOrderType(response: UpbitOrderResponse): OrderType {
        val volumeVal = response.volume?.toBigDecimal() ?: BigDecimal.ZERO
        val priceVal = response.price?.toBigDecimal() ?: BigDecimal.ZERO

        // Volume, Price, Amount 생성 - DomainError를 GatewayError로 변환
        val volume = withError<GatewayError, DomainError, Volume>({ toInvalidResponse(it) }) {
            Volume(volumeVal)
        }
        val price = if (priceVal > BigDecimal.ZERO) {
            withError<GatewayError, DomainError, Price>({ toInvalidResponse(it) }) {
                Price(priceVal)
            }
        } else {
            // Price는 0보다 커야 하므로, 기본값 1로 설정
            withError<GatewayError, DomainError, Price>({ toInvalidResponse(it) }) {
                Price(BigDecimal.ONE)
            }
        }
        val amount = withError<GatewayError, DomainError, Amount>({ toInvalidResponse(it) }) {
            Amount(priceVal)
        }

        // OrderType 생성 - OrderError를 GatewayError로 변환
        return withError<GatewayError, OrderError, OrderType>({ toOrderError(it) }) {
            when (response.ordType.lowercase()) {
                "limit" -> OrderType.Limit(volume, price)
                "price" -> OrderType.MarketBuy(amount)
                "market" -> OrderType.MarketSell(volume)
                "best" -> OrderType.Best(volume)
                else -> OrderType.Limit(volume, price)
            }
        }
    }

    private fun calculateExecutedAmount(response: UpbitOrderResponse): BigDecimal {
        val executedVolume = response.executedVolume.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val price = response.price?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        return executedVolume * price
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun parseDateTime(dateTimeStr: String): Instant {
        return try {
            LocalDateTime.parse(dateTimeStr, dateTimeFormatter)
                .toInstant(ZoneOffset.UTC)
        } catch (e: java.time.format.DateTimeParseException) {
            try {
                Instant.parse(dateTimeStr)
            } catch (e2: java.time.format.DateTimeParseException) {
                Instant.now()
            }
        }
    }

    private fun toInvalidResponse(error: DomainError): GatewayError.InvalidResponse =
        GatewayError.InvalidResponse(
            code = "INVALID_RESPONSE",
            message = error.message,
        )

    private fun toOrderError(error: OrderError): GatewayError.InvalidResponse =
        GatewayError.InvalidResponse(
            code = "INVALID_ORDER_RESPONSE",
            message = error.message,
        )
}
