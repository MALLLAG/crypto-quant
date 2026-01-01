package com.cryptoquant.infrastructure.upbit.dto.response

import com.cryptoquant.infrastructure.upbit.serializer.BigDecimalSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal

/**
 * 캔들 응답 DTO.
 *
 * @see <a href="https://docs.upbit.com/kr/reference/rest-candle-minutes.md">업비트 분(Minute) 캔들</a>
 */
@Serializable
data class UpbitCandleResponse(
    val market: String,
    @SerialName("candle_date_time_utc")
    val candleDateTimeUtc: String,
    @SerialName("candle_date_time_kst")
    val candleDateTimeKst: String,
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
    @SerialName("trade_price")
    val tradePrice: BigDecimal,
    val timestamp: Long,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("candle_acc_trade_price")
    val candleAccTradePrice: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("candle_acc_trade_volume")
    val candleAccTradeVolume: BigDecimal,
    /** 분봉 단위 (분봉일 경우에만 존재) */
    val unit: Int? = null,
)
