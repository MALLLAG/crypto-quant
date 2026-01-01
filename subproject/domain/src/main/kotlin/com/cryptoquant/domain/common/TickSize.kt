package com.cryptoquant.domain.common

import java.math.BigDecimal

/**
 * 호가 단위 (Tick Size)
 *
 * 참고: 업비트 호가 단위 정책 (변경 후 기준)
 * - KRW 마켓: https://docs.upbit.com/docs/market-info-trade-price-detail
 * - 확인 일자: 2025-12-31
 *
 * 주의: 업비트 정책 변경 시 이 테이블도 업데이트해야 합니다.
 */
@JvmInline
value class TickSize(val value: BigDecimal) {
    companion object {
        /**
         * KRW 마켓 호가 단위 (변경 후 정책 적용)
         *
         * | 가격 구간 | 호가 단위 |
         * |----------|----------|
         * | 2,000,000 이상 | 1,000 |
         * | 1,000,000 ~ 2,000,000 미만 | 1,000 |
         * | 500,000 ~ 1,000,000 미만 | 500 |
         * | 100,000 ~ 500,000 미만 | 100 |
         * | 50,000 ~ 100,000 미만 | 50 |
         * | 10,000 ~ 50,000 미만 | 10 |
         * | 5,000 ~ 10,000 미만 | 5 |
         * | 1,000 ~ 5,000 미만 | 1 |
         * | 100 ~ 1,000 미만 | 1 |
         * | 10 ~ 100 미만 | 0.1 |
         * | 1 ~ 10 미만 | 0.01 |
         * | 0.1 ~ 1 미만 | 0.001 |
         * | 0.01 ~ 0.1 미만 | 0.0001 |
         * | 0.001 ~ 0.01 미만 | 0.00001 |
         * | 0.0001 ~ 0.001 미만 | 0.000001 |
         * | 0.00001 ~ 0.0001 미만 | 0.0000001 |
         * | 0.00001 미만 | 0.00000001 |
         */
        fun forKrwMarket(price: BigDecimal): TickSize = when {
            price >= BigDecimal("2000000") -> TickSize(BigDecimal("1000"))
            price >= BigDecimal("1000000") -> TickSize(BigDecimal("1000"))
            price >= BigDecimal("500000") -> TickSize(BigDecimal("500"))
            price >= BigDecimal("100000") -> TickSize(BigDecimal("100"))
            price >= BigDecimal("50000") -> TickSize(BigDecimal("50"))
            price >= BigDecimal("10000") -> TickSize(BigDecimal("10"))
            price >= BigDecimal("5000") -> TickSize(BigDecimal("5"))
            price >= BigDecimal("1000") -> TickSize(BigDecimal("1"))
            price >= BigDecimal("100") -> TickSize(BigDecimal("1"))
            price >= BigDecimal("10") -> TickSize(BigDecimal("0.1"))
            price >= BigDecimal("1") -> TickSize(BigDecimal("0.01"))
            price >= BigDecimal("0.1") -> TickSize(BigDecimal("0.001"))
            price >= BigDecimal("0.01") -> TickSize(BigDecimal("0.0001"))
            price >= BigDecimal("0.001") -> TickSize(BigDecimal("0.00001"))
            price >= BigDecimal("0.0001") -> TickSize(BigDecimal("0.000001"))
            price >= BigDecimal("0.00001") -> TickSize(BigDecimal("0.0000001"))
            else -> TickSize(BigDecimal("0.00000001"))
        }

        fun forBtcMarket(): TickSize = TickSize(BigDecimal("0.00000001"))

        /**
         * USDT 마켓 호가 단위
         *
         * | 가격 구간 | 호가 단위 |
         * |----------|----------|
         * | 10 USDT 이상 | 0.01 |
         * | 1 ~ 10 USDT 미만 | 0.001 |
         * | 0.1 ~ 1 USDT 미만 | 0.0001 |
         * | 0.01 ~ 0.1 USDT 미만 | 0.00001 |
         * | 0.001 ~ 0.01 USDT 미만 | 0.000001 |
         * | 0.0001 ~ 0.001 USDT 미만 | 0.0000001 |
         * | 0.0001 USDT 미만 | 0.00000001 |
         */
        fun forUsdtMarket(price: BigDecimal): TickSize = when {
            price >= BigDecimal("10") -> TickSize(BigDecimal("0.01"))
            price >= BigDecimal("1") -> TickSize(BigDecimal("0.001"))
            price >= BigDecimal("0.1") -> TickSize(BigDecimal("0.0001"))
            price >= BigDecimal("0.01") -> TickSize(BigDecimal("0.00001"))
            price >= BigDecimal("0.001") -> TickSize(BigDecimal("0.000001"))
            price >= BigDecimal("0.0001") -> TickSize(BigDecimal("0.0000001"))
            else -> TickSize(BigDecimal("0.00000001"))
        }

        fun forMarket(market: Market, price: BigDecimal): TickSize = when (market) {
            Market.KRW -> forKrwMarket(price)
            Market.BTC -> forBtcMarket()
            Market.USDT -> forUsdtMarket(price)
        }
    }
}
