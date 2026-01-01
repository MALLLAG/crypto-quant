package com.cryptoquant.domain.quotation

import com.cryptoquant.domain.common.Price
import com.cryptoquant.domain.common.Volume

/**
 * 호가 단위.
 *
 * 매수/매도 호가와 수량을 나타냅니다.
 */
data class OrderbookUnit(
    val askPrice: Price,
    val bidPrice: Price,
    val askSize: Volume,
    val bidSize: Volume,
)
