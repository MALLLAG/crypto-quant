package com.cryptoquant.infrastructure.upbit.dto.response

import com.cryptoquant.infrastructure.upbit.serializer.BigDecimalSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal

/**
 * 호가창 응답 DTO.
 *
 * @see <a href="https://docs.upbit.com/kr/reference/rest-orderbook.md">업비트 호가 정보</a>
 */
@Serializable
data class UpbitOrderbookResponse(
    val market: String,
    val timestamp: Long,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("total_ask_size")
    val totalAskSize: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("total_bid_size")
    val totalBidSize: BigDecimal,
    @SerialName("orderbook_units")
    val orderbookUnits: List<OrderbookUnitDto>,
) {
    @Serializable
    data class OrderbookUnitDto(
        @Serializable(with = BigDecimalSerializer::class)
        @SerialName("ask_price")
        val askPrice: BigDecimal,
        @Serializable(with = BigDecimalSerializer::class)
        @SerialName("bid_price")
        val bidPrice: BigDecimal,
        @Serializable(with = BigDecimalSerializer::class)
        @SerialName("ask_size")
        val askSize: BigDecimal,
        @Serializable(with = BigDecimalSerializer::class)
        @SerialName("bid_size")
        val bidSize: BigDecimal,
    )
}
