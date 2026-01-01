package com.cryptoquant.infrastructure.upbit.websocket

import com.cryptoquant.infrastructure.upbit.serializer.BigDecimalSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal

/**
 * Upbit WebSocket 메시지.
 *
 * @see <a href="https://docs.upbit.com/kr/reference/websocket-ticker.md">Upbit WebSocket Ticker</a>
 * @see <a href="https://docs.upbit.com/kr/reference/websocket-trade.md">Upbit WebSocket Trade</a>
 * @see <a href="https://docs.upbit.com/kr/reference/websocket-orderbook.md">Upbit WebSocket Orderbook</a>
 * @see <a href="https://docs.upbit.com/kr/reference/websocket-myorder.md">Upbit WebSocket MyOrder</a>
 */
sealed interface UpbitWebSocketMessage {
    val type: String
    val code: String
    val timestamp: Long
    val streamType: String

    /**
     * 현재가 메시지.
     */
    @Serializable
    data class Ticker(
        @SerialName("type") override val type: String,
        @SerialName("code") override val code: String,
        @SerialName("timestamp") override val timestamp: Long,
        @SerialName("stream_type") override val streamType: String,
        @Serializable(with = BigDecimalSerializer::class)
        @SerialName("opening_price") val openingPrice: BigDecimal,
        @Serializable(with = BigDecimalSerializer::class)
        @SerialName("high_price") val highPrice: BigDecimal,
        @Serializable(with = BigDecimalSerializer::class)
        @SerialName("low_price") val lowPrice: BigDecimal,
        @Serializable(with = BigDecimalSerializer::class)
        @SerialName("trade_price") val tradePrice: BigDecimal,
        @Serializable(with = BigDecimalSerializer::class)
        @SerialName("prev_closing_price") val prevClosingPrice: BigDecimal,
        @SerialName("change") val change: String,
        @Serializable(with = BigDecimalSerializer::class)
        @SerialName("change_price") val changePrice: BigDecimal,
        @Serializable(with = BigDecimalSerializer::class)
        @SerialName("signed_change_price") val signedChangePrice: BigDecimal,
        @Serializable(with = BigDecimalSerializer::class)
        @SerialName("change_rate") val changeRate: BigDecimal,
        @Serializable(with = BigDecimalSerializer::class)
        @SerialName("signed_change_rate") val signedChangeRate: BigDecimal,
        @Serializable(with = BigDecimalSerializer::class)
        @SerialName("trade_volume") val tradeVolume: BigDecimal,
        @Serializable(with = BigDecimalSerializer::class)
        @SerialName("acc_trade_volume_24h") val accTradeVolume24h: BigDecimal,
        @Serializable(with = BigDecimalSerializer::class)
        @SerialName("acc_trade_price_24h") val accTradePrice24h: BigDecimal,
        @SerialName("trade_timestamp") val tradeTimestamp: Long,
    ) : UpbitWebSocketMessage

    /**
     * 체결 메시지.
     */
    @Serializable
    data class Trade(
        @SerialName("type") override val type: String,
        @SerialName("code") override val code: String,
        @SerialName("timestamp") override val timestamp: Long,
        @SerialName("stream_type") override val streamType: String,
        @Serializable(with = BigDecimalSerializer::class)
        @SerialName("trade_price") val tradePrice: BigDecimal,
        @Serializable(with = BigDecimalSerializer::class)
        @SerialName("trade_volume") val tradeVolume: BigDecimal,
        @SerialName("ask_bid") val askBid: String,
        @Serializable(with = BigDecimalSerializer::class)
        @SerialName("prev_closing_price") val prevClosingPrice: BigDecimal,
        @SerialName("change") val change: String,
        @SerialName("trade_timestamp") val tradeTimestamp: Long,
        @SerialName("sequential_id") val sequentialId: Long,
    ) : UpbitWebSocketMessage

    /**
     * 호가 메시지.
     */
    @Serializable
    data class Orderbook(
        @SerialName("type") override val type: String,
        @SerialName("code") override val code: String,
        @SerialName("timestamp") override val timestamp: Long,
        @SerialName("stream_type") override val streamType: String,
        @Serializable(with = BigDecimalSerializer::class)
        @SerialName("total_ask_size") val totalAskSize: BigDecimal,
        @Serializable(with = BigDecimalSerializer::class)
        @SerialName("total_bid_size") val totalBidSize: BigDecimal,
        @SerialName("orderbook_units") val orderbookUnits: List<OrderbookUnit>,
    ) : UpbitWebSocketMessage {
        @Serializable
        data class OrderbookUnit(
            @Serializable(with = BigDecimalSerializer::class)
            @SerialName("ask_price") val askPrice: BigDecimal,
            @Serializable(with = BigDecimalSerializer::class)
            @SerialName("bid_price") val bidPrice: BigDecimal,
            @Serializable(with = BigDecimalSerializer::class)
            @SerialName("ask_size") val askSize: BigDecimal,
            @Serializable(with = BigDecimalSerializer::class)
            @SerialName("bid_size") val bidSize: BigDecimal,
        )
    }

    /**
     * 내 주문 메시지.
     */
    @Serializable
    data class MyOrder(
        @SerialName("type") override val type: String,
        @SerialName("code") override val code: String,
        @SerialName("timestamp") override val timestamp: Long,
        @SerialName("stream_type") override val streamType: String,
        @SerialName("uuid") val uuid: String,
        @SerialName("ask_bid") val askBid: String,
        @SerialName("order_type") val orderType: String,
        @SerialName("state") val state: String,
        @SerialName("trade_uuid") val tradeUuid: String? = null,
        @Serializable(with = BigDecimalSerializer::class)
        @SerialName("price") val price: BigDecimal? = null,
        @Serializable(with = BigDecimalSerializer::class)
        @SerialName("avg_price") val avgPrice: BigDecimal? = null,
        @Serializable(with = BigDecimalSerializer::class)
        @SerialName("volume") val volume: BigDecimal? = null,
        @Serializable(with = BigDecimalSerializer::class)
        @SerialName("remaining_volume") val remainingVolume: BigDecimal? = null,
        @Serializable(with = BigDecimalSerializer::class)
        @SerialName("executed_volume") val executedVolume: BigDecimal? = null,
        @SerialName("trades_count") val tradesCount: Int? = null,
        @Serializable(with = BigDecimalSerializer::class)
        @SerialName("paid_fee") val paidFee: BigDecimal? = null,
        @Serializable(with = BigDecimalSerializer::class)
        @SerialName("executed_funds") val executedFunds: BigDecimal? = null,
        @Serializable(with = BigDecimalSerializer::class)
        @SerialName("trade_fee") val tradeFee: BigDecimal? = null,
        @SerialName("is_maker") val isMaker: Boolean? = null,
        @SerialName("trade_timestamp") val tradeTimestamp: Long? = null,
        @SerialName("order_timestamp") val orderTimestamp: Long? = null,
    ) : UpbitWebSocketMessage
}
