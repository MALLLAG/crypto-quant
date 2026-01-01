package com.cryptoquant.domain.common

sealed interface DomainError {
    val message: String
}

data class InvalidMarket(override val message: String) : DomainError

data class InvalidTradingPair(override val message: String) : DomainError

data class InvalidPrice(override val message: String) : DomainError

data class InvalidVolume(override val message: String) : DomainError

data class InvalidCurrency(override val message: String) : DomainError

data class InvalidOrderId(override val message: String) : DomainError

data class InvalidCandleUnit(override val message: String) : DomainError

data class InvalidCandle(override val message: String) : DomainError

data class InvalidTickSize(override val message: String) : DomainError

data class InvalidFeeRate(override val message: String) : DomainError

data class InvalidAmount(override val message: String) : DomainError

data class InvalidBalance(override val message: String) : DomainError

data class InvalidPriceChange(override val message: String) : DomainError

data class InvalidChangeRate(override val message: String) : DomainError

data class InvalidTradeSequentialId(override val message: String) : DomainError

data class InvalidAvgBuyPrice(override val message: String) : DomainError
