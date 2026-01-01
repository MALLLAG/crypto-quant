package com.cryptoquant.infrastructure.upbit.dto.response

import com.cryptoquant.infrastructure.upbit.serializer.BigDecimalSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal

/**
 * 현재가(Ticker) 응답 DTO.
 *
 * @see <a href="https://docs.upbit.com/kr/reference/rest-ticker.md">업비트 현재가 정보</a>
 */
@Serializable
data class UpbitTickerResponse(
    val market: String,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("trade_price")
    val tradePrice: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("opening_price")
    val openingPrice: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("high_price")
    val highPrice: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("low_price")
    val lowPrice: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("prev_closing_price")
    val prevClosingPrice: BigDecimal,
    /** RISE, EVEN, FALL */
    val change: String,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("change_price")
    val changePrice: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("change_rate")
    val changeRate: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("signed_change_price")
    val signedChangePrice: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("signed_change_rate")
    val signedChangeRate: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("trade_volume")
    val tradeVolume: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("acc_trade_price_24h")
    val accTradePrice24h: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("acc_trade_volume_24h")
    val accTradeVolume24h: BigDecimal,
    val timestamp: Long,
)
