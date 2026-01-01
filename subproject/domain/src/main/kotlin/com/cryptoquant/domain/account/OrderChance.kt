package com.cryptoquant.domain.account

import com.cryptoquant.domain.common.Amount
import com.cryptoquant.domain.common.FeeRate
import com.cryptoquant.domain.common.TradingPair

/**
 * 주문 가능 정보.
 *
 * 특정 마켓에서 주문 시 필요한 정보를 담고 있습니다.
 *
 * @property pair 마켓 (예: KRW-BTC)
 * @property bidFee 매수 수수료율
 * @property askFee 매도 수수료율
 * @property bidAccount 매수 시 사용할 잔고 (예: KRW 잔고)
 * @property askAccount 매도 시 사용할 잔고 (예: BTC 잔고)
 * @property minOrderAmount 최소 주문금액
 */
data class OrderChance(
    val pair: TradingPair,
    val bidFee: FeeRate,
    val askFee: FeeRate,
    val bidAccount: Balance,
    val askAccount: Balance,
    val minOrderAmount: Amount,
)
