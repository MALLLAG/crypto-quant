package com.cryptoquant.infrastructure.upbit.dto.response

import com.cryptoquant.infrastructure.upbit.serializer.BigDecimalSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal

/**
 * 잔고 응답 DTO.
 *
 * @see <a href="https://docs.upbit.com/kr/reference/rest-accounts.md">업비트 전체 계좌 조회</a>
 */
@Serializable
data class UpbitBalanceResponse(
    val currency: String,
    val balance: String,
    val locked: String,
    @Serializable(with = BigDecimalSerializer::class)
    @SerialName("avg_buy_price")
    val avgBuyPrice: BigDecimal,
    @SerialName("avg_buy_price_modified")
    val avgBuyPriceModified: Boolean,
    @SerialName("unit_currency")
    val unitCurrency: String,
)
