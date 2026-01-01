package com.cryptoquant.domain.account

import com.cryptoquant.domain.common.Volume

/**
 * 계정 정보.
 *
 * 사용자의 전체 잔고 목록을 관리합니다.
 */
data class Account(
    val balances: List<Balance>,
) {
    /**
     * 특정 통화의 잔고를 조회합니다.
     */
    fun getBalance(currency: Currency): Balance? = balances.find { it.currency == currency }

    /**
     * 특정 통화의 사용 가능한 잔고를 조회합니다.
     */
    fun getAvailableBalance(currency: Currency): Volume = getBalance(currency)?.available ?: Volume.ZERO

    /**
     * 특정 통화를 보유하고 있는지 확인합니다.
     */
    fun hasBalance(currency: Currency): Boolean = getBalance(currency)?.balance?.isPositive ?: false
}
