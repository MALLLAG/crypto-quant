package com.cryptoquant.infrastructure.upbit.dto.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 주문 응답 DTO.
 *
 * @see <a href="https://docs.upbit.com/kr/reference/rest-order-create.md">업비트 주문하기</a>
 * @see <a href="https://docs.upbit.com/kr/reference/rest-order-info.md">업비트 개별 주문 조회</a>
 */
@Serializable
data class UpbitOrderResponse(
    val uuid: String,
    /** bid(매수), ask(매도) */
    val side: String,
    /** limit, price(시장가 매수), market(시장가 매도), best */
    @SerialName("ord_type")
    val ordType: String,
    /** 주문 가격 (지정가 주문 시) */
    val price: String? = null,
    /** wait, watch, done, cancel */
    val state: String,
    val market: String,
    /** 주문 수량 (지정가, 시장가 매도 시) */
    val volume: String? = null,
    @SerialName("remaining_volume")
    val remainingVolume: String,
    @SerialName("executed_volume")
    val executedVolume: String,
    @SerialName("trades_count")
    val tradesCount: Int,
    @SerialName("paid_fee")
    val paidFee: String,
    @SerialName("created_at")
    val createdAt: String,
    /** 주문에 묶인 금액 (미체결 상태일 때) */
    val locked: String? = null,
    /** 체결된 금액 */
    @SerialName("executed_funds")
    val executedFunds: String? = null,
)
