package com.cryptoquant.infrastructure.upbit.dto.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 주문 가능 정보 응답 DTO.
 *
 * @see <a href="https://docs.upbit.com/kr/reference/rest-order-chance.md">업비트 주문 가능 정보</a>
 */
@Serializable
data class UpbitOrderChanceResponse(
    @SerialName("bid_fee")
    val bidFee: String,
    @SerialName("ask_fee")
    val askFee: String,
    val market: MarketDto,
    @SerialName("bid_account")
    val bidAccount: AccountDto,
    @SerialName("ask_account")
    val askAccount: AccountDto,
) {
    @Serializable
    data class MarketDto(
        val id: String,
        val name: String,
        @SerialName("order_types")
        val orderTypes: List<String>,
        val ask: OrderConstraintDto,
        val bid: OrderConstraintDto,
        val state: String,
    )

    @Serializable
    data class OrderConstraintDto(
        val currency: String,
        @SerialName("min_total")
        val minTotal: String,
    )

    @Serializable
    data class AccountDto(
        val currency: String,
        val balance: String,
        val locked: String,
        @SerialName("avg_buy_price")
        val avgBuyPrice: String,
        @SerialName("avg_buy_price_modified")
        val avgBuyPriceModified: Boolean,
        @SerialName("unit_currency")
        val unitCurrency: String,
    )
}
