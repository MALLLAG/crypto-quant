package com.cryptoquant.domain.common

import java.math.RoundingMode

object DecimalConfig {
    const val PERCENT_SCALE = 2
    const val PRICE_SCALE = 8
    val ROUNDING_MODE: RoundingMode = RoundingMode.HALF_UP
}
