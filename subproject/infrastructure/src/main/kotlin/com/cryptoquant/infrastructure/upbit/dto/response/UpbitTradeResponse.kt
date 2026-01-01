package com.cryptoquant.infrastructure.upbit.dto.response

import com.cryptoquant.infrastructure.upbit.serializer.BigDecimalSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal

/**
 * 체결 내역 응답 DTO.
 *
 * @see <a href="https://docs.upbit.com/kr/reference/rest-trades-ticks.md">업비트 최근 체결 내역</a>
 */
@Serializable
data class UpbitTradeResponse(
    val market: String,
    @SerialName("trade_date_utc")
    val tradeDateUtc: String,
    @SerialName("trade_time_utc")
    val tradeTimeUtc: String,
    val timestamp: Long,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("trade_price")
    val tradePrice: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("trade_volume")
    val tradeVolume: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("prev_closing_price")
    val prevClosingPrice: BigDecimal,
    /** 전일 대비 변화량 */
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("change_price")
    val changePrice: BigDecimal,
    /** RISE, EVEN, FALL */
    val change: String,
    /** ASK, BID */
    @SerialName("ask_bid")
    val askBid: String,
    @SerialName("sequential_id")
    val sequentialId: Long,
)
