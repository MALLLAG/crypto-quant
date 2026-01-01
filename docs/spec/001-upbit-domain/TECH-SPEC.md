# 업비트 도메인 모델 설계 테크스펙

> **작성일**: 2025-12-28
> **수정일**: 2026-01-01 (코드 리뷰 반영 - 아키텍처 개선 및 검증 강화)
> **작성자**: Claude

---

## 1. 개요

### 1.1 목적

- **문제 정의**: 가상자산 자동매매 시스템을 구축하기 위해 업비트 API와 연동하는 도메인 모델이 필요함
- **해결 방안**: 함수형 DDD 원칙에 따라 타입 안전하고 순수한 도메인 모델을 설계
- **기대 효과**:
  - 컴파일 타임에 비즈니스 규칙 위반 감지
  - 테스트 용이한 순수 함수 기반 비즈니스 로직
  - 명시적 오류 처리로 안정적인 시스템 운영

### 1.2 범위

#### 포함 (In Scope)
- 시세 조회 (Quotation): 캔들, 현재가, 호가, 체결 내역
- 주문 (Order): 지정가/시장가 매수/매도, 조회, 취소
- 계정 (Account): 잔고 조회, 주문 가능 정보
- 마켓: KRW, BTC, USDT 전체 마켓 지원

#### 제외 (Out of Scope)
- 입출금 기능 (향후 확장)
- 트래블룰 검증 (향후 확장)
- 웹 UI (별도 스펙)

### 1.3 용어 정의

| 용어 | 설명 | 예시 |
|------|------|------|
| Market | 호가 통화 (quote currency) | KRW, BTC, USDT |
| Ticker | 기준 통화 (base currency) | BTC, ETH, XRP |
| TradingPair | Market-Ticker 조합 | KRW-BTC, BTC-ETH |
| Candle | 특정 시간 단위의 OHLCV 데이터 | 1분봉, 일봉 |
| Orderbook | 매수/매도 호가 목록 | bid/ask 가격 및 수량 |
| Side | 주문 방향 | BID(매수), ASK(매도) |

### 1.4 관련 문서

> 관련 문서: [upbit.md](../../upbit.md)

---

## 2. 아키텍처 개요

```
┌──────────────────────────────────────────────────────────────────┐
│                        Presentation Layer                         │
│  (REST Controller, WebSocket Handler)                            │
└─────────────────────────────┬────────────────────────────────────┘
                              │
┌─────────────────────────────▼────────────────────────────────────┐
│                        Application Layer                          │
│  (UseCase: PlaceOrder, CancelOrder, GetQuotation, GetAccount)    │
└─────────────────────────────┬────────────────────────────────────┘
                              │
┌─────────────────────────────▼────────────────────────────────────┐
│                         Domain Layer                              │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐   │
│  │    Quotation    │  │      Order      │  │     Account     │   │
│  │     Domain      │  │     Domain      │  │     Domain      │   │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘   │
└─────────────────────────────┬────────────────────────────────────┘
                              │
┌─────────────────────────────▼────────────────────────────────────┐
│                      Infrastructure Layer                         │
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────────┐  │
│  │ UpbitRestClient │  │UpbitWsClient    │  │  R2DBC Repository│  │
│  └─────────────────┘  └─────────────────┘  └──────────────────┘  │
└──────────────────────────────────────────────────────────────────┘
```

---

## 3. 도메인 모델

### 3.1 공통 값 객체 (Common Value Objects)

```kotlin
package com.cryptoquant.domain.common

import java.math.RoundingMode
import arrow.core.raise.recover

object DecimalConfig {
    const val PERCENT_SCALE = 2
    const val PRICE_SCALE = 8
    val ROUNDING_MODE = RoundingMode.HALF_UP
}

enum class Market {
    KRW, BTC, USDT;

    companion object {
        context(_: Raise<DomainError>)
        fun from(value: String): Market {
            return entries.find { it.name == value.uppercase() }
                ?: raise(InvalidMarket("지원하지 않는 마켓: $value"))
        }
    }
}

data class TradingPair private constructor(
    val market: Market,
    val ticker: String,
) {
    val value: String get() = "${market.name}-$ticker"

    companion object {
        // 티커에 허용되는 문자: 영문 대문자, 숫자만
        private val TICKER_REGEX = Regex("^[A-Z0-9]+$")

        context(_: Raise<DomainError>)
        operator fun invoke(value: String): TradingPair {
            ensure(value.contains("-")) { InvalidTradingPair("페어 형식이 올바르지 않습니다: $value") }
            val parts = value.uppercase().split("-", limit = 2)
            val market = Market.from(parts[0])
            val ticker = parts[1]
            ensure(ticker.isNotBlank()) { InvalidTradingPair("티커가 비어있습니다") }
            ensure(!ticker.contains("-")) { InvalidTradingPair("티커에 '-'가 포함될 수 없습니다: $ticker") }
            ensure(TICKER_REGEX.matches(ticker)) { InvalidTradingPair("티커는 영문과 숫자만 허용됩니다: $ticker") }
            return TradingPair(market, ticker)
        }

        context(_: Raise<DomainError>)
        operator fun invoke(market: Market, ticker: String): TradingPair {
            val upperTicker = ticker.uppercase()
            ensure(upperTicker.isNotBlank()) { InvalidTradingPair("티커가 비어있습니다") }
            ensure(!upperTicker.contains("-")) { InvalidTradingPair("티커에 '-'가 포함될 수 없습니다: $upperTicker") }
            ensure(TICKER_REGEX.matches(upperTicker)) { InvalidTradingPair("티커는 영문과 숫자만 허용됩니다: $upperTicker") }
            return TradingPair(market, upperTicker)
        }
    }
}

@JvmInline
value class Price private constructor(val value: BigDecimal) {
    companion object {
        context(_: Raise<DomainError>)
        operator fun invoke(value: BigDecimal): Price {
            ensure(value > BigDecimal.ZERO) { InvalidPrice("가격은 0보다 커야 합니다") }
            return Price(value)
        }

        context(_: Raise<DomainError>)
        operator fun invoke(value: String): Price = invoke(value.toBigDecimalOrNull()
            ?: raise(InvalidPrice("숫자 형식이 아닙니다: $value")))
    }

    context(_: Raise<DomainError>)
    fun adjustToTickSize(tickSize: TickSize): Price {
        val adjusted = (value / tickSize.value).setScale(0, RoundingMode.DOWN) * tickSize.value
        return if (adjusted > BigDecimal.ZERO) {
            Price(adjusted)
        } else {
            Price(tickSize.value)
        }
    }

    context(_: Raise<DomainError>)
    fun validateTickSize(tickSize: TickSize) {
        val remainder = value.remainder(tickSize.value)
        ensure(remainder.compareTo(BigDecimal.ZERO) == 0) {
            InvalidTickSize("가격 $value 은(는) 호가단위 ${tickSize.value}에 맞지 않습니다")
        }
    }
}

@JvmInline
value class Volume private constructor(val value: BigDecimal) {
    val isZero: Boolean get() = value.compareTo(BigDecimal.ZERO) == 0
    val isPositive: Boolean get() = value > BigDecimal.ZERO

    operator fun plus(other: Volume): Volume = Volume(value + other.value)
    operator fun minus(other: Volume): Volume? {
        val result = value - other.value
        return if (result >= BigDecimal.ZERO) Volume(result) else null
    }
    operator fun compareTo(other: Volume): Int = value.compareTo(other.value)

    companion object {
        val ZERO = Volume(BigDecimal.ZERO)

        context(_: Raise<DomainError>)
        operator fun invoke(value: BigDecimal): Volume {
            ensure(value >= BigDecimal.ZERO) { InvalidVolume("수량은 0 이상이어야 합니다") }
            ensure(value.scale() <= 8) { InvalidVolume("소수점 8자리까지만 허용됩니다") }
            return Volume(value)
        }

        context(_: Raise<DomainError>)
        operator fun invoke(value: String): Volume = invoke(value.toBigDecimalOrNull()
            ?: raise(InvalidVolume("숫자 형식이 아닙니다: $value")))
    }
}

@JvmInline
value class Amount private constructor(val value: BigDecimal) {
    val isZero: Boolean get() = value.compareTo(BigDecimal.ZERO) == 0
    val isPositive: Boolean get() = value > BigDecimal.ZERO

    operator fun plus(other: Amount): Amount = Amount(value + other.value)
    operator fun minus(other: Amount): Amount? {
        val result = value - other.value
        return if (result >= BigDecimal.ZERO) Amount(result) else null
    }
    operator fun compareTo(other: Amount): Int = value.compareTo(other.value)

    companion object {
        val ZERO = Amount(BigDecimal.ZERO)

        context(_: Raise<DomainError>)
        operator fun invoke(value: BigDecimal): Amount {
            ensure(value >= BigDecimal.ZERO) { InvalidAmount("금액은 0 이상이어야 합니다: $value") }
            return Amount(value)
        }

        context(_: Raise<DomainError>)
        operator fun invoke(value: String): Amount = invoke(value.toBigDecimalOrNull()
            ?: raise(InvalidAmount("숫자 형식이 아닙니다: $value")))
    }
}

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

@JvmInline
value class FeeRate private constructor(val value: BigDecimal) {
    fun toPercent(): BigDecimal = value * BigDecimal(100)

    companion object {
        context(_: Raise<DomainError>)
        operator fun invoke(value: BigDecimal): FeeRate {
            ensure(value >= BigDecimal.ZERO) { InvalidFeeRate("수수료율은 0 이상이어야 합니다: $value") }
            ensure(value <= BigDecimal.ONE) { InvalidFeeRate("수수료율은 1 이하여야 합니다: $value") }
            return FeeRate(value)
        }

        context(_: Raise<DomainError>)
        operator fun invoke(value: String): FeeRate = invoke(value.toBigDecimalOrNull()
            ?: raise(InvalidFeeRate("숫자 형식이 아닙니다: $value")))

        val DEFAULT = FeeRate(BigDecimal("0.0005"))
    }
}

/**
 * 가격 변동량 (음수 허용).
 *
 * 전일 대비 가격 변화량을 나타냅니다.
 * - 양수: 상승
 * - 음수: 하락
 * - 0: 보합
 */
@JvmInline
value class PriceChange(val value: BigDecimal) {
    val isPositive: Boolean get() = value > BigDecimal.ZERO
    val isNegative: Boolean get() = value < BigDecimal.ZERO
    val isZero: Boolean get() = value.compareTo(BigDecimal.ZERO) == 0

    /** 절대값 반환 */
    fun abs(): BigDecimal = value.abs()

    companion object {
        val ZERO = PriceChange(BigDecimal.ZERO)

        operator fun invoke(value: BigDecimal): PriceChange = PriceChange(value)

        context(_: Raise<DomainError>)
        operator fun invoke(value: String): PriceChange = PriceChange(
            value.toBigDecimalOrNull()
                ?: raise(InvalidPriceChange("숫자 형식이 아닙니다: $value"))
        )
    }
}

/**
 * 변동률 (음수 허용).
 *
 * 비율을 나타내며 -1.0 ~ 무제한 범위입니다.
 * (예: 0.05 = 5% 상승, -0.03 = 3% 하락)
 *
 * 참고: 상승률은 이론상 무제한이지만, 하락률은 -100%(-1.0)가 최대입니다.
 */
@JvmInline
value class ChangeRate private constructor(val value: BigDecimal) {
    val isPositive: Boolean get() = value > BigDecimal.ZERO
    val isNegative: Boolean get() = value < BigDecimal.ZERO
    val isZero: Boolean get() = value.compareTo(BigDecimal.ZERO) == 0

    /** 퍼센트로 변환 (예: 0.05 → 5.00) */
    fun toPercent(): BigDecimal = value
        .multiply(BigDecimal(100))
        .setScale(DecimalConfig.PERCENT_SCALE, DecimalConfig.ROUNDING_MODE)

    companion object {
        val ZERO = ChangeRate(BigDecimal.ZERO)

        context(_: Raise<DomainError>)
        operator fun invoke(value: BigDecimal): ChangeRate {
            // 하락률은 -100%(-1.0)가 최대
            ensure(value >= BigDecimal("-1")) {
                InvalidChangeRate("변동률은 -100% 이상이어야 합니다: $value")
            }
            return ChangeRate(value)
        }

        context(_: Raise<DomainError>)
        operator fun invoke(value: String): ChangeRate = invoke(
            value.toBigDecimalOrNull()
                ?: raise(InvalidChangeRate("숫자 형식이 아닙니다: $value"))
        )
    }
}

/**
 * 체결 순서 ID.
 *
 * 업비트에서 체결 내역의 고유 순서를 나타내는 ID입니다.
 * 시간순으로 증가하며, 페이징 조회에 사용됩니다.
 */
@JvmInline
value class TradeSequentialId private constructor(val value: Long) {
    companion object {
        context(_: Raise<DomainError>)
        operator fun invoke(value: Long): TradeSequentialId {
            ensure(value > 0) { InvalidTradeSequentialId("체결 순서 ID는 양수여야 합니다: $value") }
            return TradeSequentialId(value)
        }
    }
}

/**
 * 평균 매수가.
 *
 * Price와 달리 0을 허용합니다 (아직 매수한 적 없는 경우).
 */
@JvmInline
value class AvgBuyPrice private constructor(val value: BigDecimal) {
    val isZero: Boolean get() = value.compareTo(BigDecimal.ZERO) == 0

    companion object {
        val ZERO = AvgBuyPrice(BigDecimal.ZERO)

        context(_: Raise<DomainError>)
        operator fun invoke(value: BigDecimal): AvgBuyPrice {
            ensure(value >= BigDecimal.ZERO) { InvalidAvgBuyPrice("평균 매수가는 0 이상이어야 합니다: $value") }
            return AvgBuyPrice(value)
        }

        context(_: Raise<DomainError>)
        operator fun invoke(value: String): AvgBuyPrice = invoke(
            value.toBigDecimalOrNull()
                ?: raise(InvalidAvgBuyPrice("숫자 형식이 아닙니다: $value"))
        )
    }
}
```

### 3.2 시세 도메인 (Quotation Domain)

```kotlin
package com.cryptoquant.domain.quotation

/**
 * 캔들 단위.
 *
 * 업비트 API 지원 현황:
 * - REST API: 분봉(1,3,5,10,15,30,60,240분), 일봉, 주봉, 월봉
 * - WebSocket: 초봉(1초) 포함
 * - 연봉: 미지원
 */
sealed interface CandleUnit {
    val code: String

    /**
     * 초봉 단위.
     *
     * 주의: WebSocket API에서만 지원됩니다. REST API에서는 사용 불가.
     */
    data class Seconds private constructor(val seconds: Int) : CandleUnit {
        override val code: String = "${seconds}s"

        companion object {
            private val SUPPORTED_UNITS = listOf(1)

            context(_: Raise<DomainError>)
            operator fun invoke(seconds: Int): Seconds {
                ensure(seconds in SUPPORTED_UNITS) {
                    InvalidCandleUnit("지원하지 않는 초봉 단위: $seconds (지원: $SUPPORTED_UNITS)")
                }
                return Seconds(seconds)
            }

            val ONE = Seconds(1)
        }
    }

    data class Minutes private constructor(val minutes: Int) : CandleUnit {
        override val code: String = "${minutes}m"

        companion object {
            private val SUPPORTED_UNITS = listOf(1, 3, 5, 10, 15, 30, 60, 240)

            context(_: Raise<DomainError>)
            operator fun invoke(minutes: Int): Minutes {
                ensure(minutes in SUPPORTED_UNITS) {
                    InvalidCandleUnit("지원하지 않는 분봉 단위: $minutes (지원: $SUPPORTED_UNITS)")
                }
                return Minutes(minutes)
            }
        }
    }

    object Day : CandleUnit {
        override val code: String = "1d"
    }

    object Week : CandleUnit {
        override val code: String = "1w"
    }

    object Month : CandleUnit {
        override val code: String = "1M"
    }
}

data class Candle private constructor(
    val pair: TradingPair,
    val unit: CandleUnit,
    val timestamp: Instant,
    val openingPrice: Price,
    val highPrice: Price,
    val lowPrice: Price,
    val closingPrice: Price,
    val volume: Volume,
    val amount: Amount,
) {
    companion object {
        context(_: Raise<DomainError>)
        operator fun invoke(
            pair: TradingPair,
            unit: CandleUnit,
            timestamp: Instant,
            openingPrice: Price,
            highPrice: Price,
            lowPrice: Price,
            closingPrice: Price,
            volume: Volume,
            amount: Amount,
        ): Candle {
            ensure(highPrice.value >= lowPrice.value) {
                InvalidCandle("고가는 저가보다 크거나 같아야 합니다")
            }
            ensure(highPrice.value >= openingPrice.value) {
                InvalidCandle("고가는 시가보다 크거나 같아야 합니다")
            }
            ensure(highPrice.value >= closingPrice.value) {
                InvalidCandle("고가는 종가보다 크거나 같아야 합니다")
            }
            ensure(lowPrice.value <= openingPrice.value) {
                InvalidCandle("저가는 시가보다 작거나 같아야 합니다")
            }
            ensure(lowPrice.value <= closingPrice.value) {
                InvalidCandle("저가는 종가보다 작거나 같아야 합니다")
            }
            return Candle(pair, unit, timestamp, openingPrice, highPrice, lowPrice, closingPrice, volume, amount)
        }
    }
}

/**
 * 현재가 정보.
 *
 * 업비트 시세 API의 현재가(ticker) 응답을 나타냅니다.
 *
 * 주의: changePrice/changeRate는 업비트 API에서 절대값(항상 양수)으로 제공됩니다.
 * PriceChange/ChangeRate 타입 자체는 음수를 허용하지만, 이 필드들은 항상 양수입니다.
 * 하락 여부는 change 필드(RISE/EVEN/FALL)로 확인하세요.
 *
 * @property change 전일 대비 변동 방향 (RISE: 상승, EVEN: 보합, FALL: 하락)
 * @property changePrice 전일 대비 변화량 (절대값, 업비트 API에서 항상 양수로 제공)
 * @property changeRate 전일 대비 변화율 (절대값, 업비트 API에서 항상 양수로 제공)
 * @property signedChangePrice 전일 대비 변화량 (부호 포함, 하락 시 음수)
 * @property signedChangeRate 전일 대비 변화율 (부호 포함, 하락 시 음수)
 */
data class Ticker(
    val pair: TradingPair,
    val tradePrice: Price,
    val openingPrice: Price,
    val highPrice: Price,
    val lowPrice: Price,
    val prevClosingPrice: Price,
    val change: Change,
    val changePrice: PriceChange,
    val changeRate: ChangeRate,
    val signedChangePrice: PriceChange,
    val signedChangeRate: ChangeRate,
    val tradeVolume: Volume,
    val accTradePrice24h: Amount,
    val accTradeVolume24h: Volume,
    val timestamp: Instant,
)

enum class Change {
    RISE,
    EVEN,
    FALL
}

data class OrderbookUnit(
    val askPrice: Price,
    val bidPrice: Price,
    val askSize: Volume,
    val bidSize: Volume,
)

/**
 * 호가창 데이터.
 *
 * 참고: 업비트 API는 이미 정렬된 호가 데이터를 반환합니다.
 * - orderbookUnits[0]: 최우선 호가 (best ask/bid)
 * - ask: 오름차순 (낮은 가격이 best)
 * - bid: 내림차순 (높은 가격이 best)
 */
data class Orderbook private constructor(
    val pair: TradingPair,
    val timestamp: Instant,
    val totalAskSize: Volume,
    val totalBidSize: Volume,
    val orderbookUnits: List<OrderbookUnit>,
) {
    /** 최우선 매수 호가 (가장 높은 매수가) */
    val bestBidPrice: Price? get() = orderbookUnits.maxByOrNull { it.bidPrice.value }?.bidPrice

    /** 최우선 매도 호가 (가장 낮은 매도가) */
    val bestAskPrice: Price? get() = orderbookUnits.minByOrNull { it.askPrice.value }?.askPrice

    /** 스프레드 (최우선 매도가 - 최우선 매수가) */
    fun spread(): BigDecimal? {
        val ask = bestAskPrice?.value ?: return null
        val bid = bestBidPrice?.value ?: return null
        return ask - bid
    }

    companion object {
        /**
         * Orderbook 생성.
         * 업비트 API 응답은 이미 정렬되어 있으므로 재정렬하지 않습니다.
         */
        operator fun invoke(
            pair: TradingPair,
            timestamp: Instant,
            totalAskSize: Volume,
            totalBidSize: Volume,
            orderbookUnits: List<OrderbookUnit>,
        ): Orderbook = Orderbook(pair, timestamp, totalAskSize, totalBidSize, orderbookUnits)
    }
}

/**
 * 체결 내역.
 *
 * 업비트 시세 API의 체결 내역(trades) 응답을 나타냅니다.
 *
 * @property sequentialId 체결 순서 ID (페이징 조회에 사용)
 */
data class Trade(
    val pair: TradingPair,
    val tradePrice: Price,
    val tradeVolume: Volume,
    val askBid: OrderSide,
    val prevClosingPrice: Price,
    val change: Change,
    val timestamp: Instant,
    val sequentialId: TradeSequentialId,
)
```

### 3.3 주문 도메인 (Order Domain)

```kotlin
package com.cryptoquant.domain.order

@JvmInline
value class OrderId private constructor(val value: String) {
    companion object {
        context(_: Raise<DomainError>)
        operator fun invoke(value: String): OrderId {
            ensure(value.isNotBlank()) { InvalidOrderId("주문 ID는 비어있을 수 없습니다") }
            return OrderId(value)
        }
    }
}

enum class OrderSide {
    BID,
    ASK
}

sealed interface OrderType {
    data class Limit private constructor(val volume: Volume, val price: Price) : OrderType {
        companion object {
            context(_: Raise<OrderError>)
            operator fun invoke(volume: Volume, price: Price): Limit {
                ensure(volume.isPositive) { InvalidOrderRequest("지정가 주문 수량은 0보다 커야 합니다") }
                return Limit(volume, price)
            }
        }
    }

    data class MarketBuy private constructor(val totalPrice: Amount) : OrderType {
        companion object {
            context(_: Raise<OrderError>)
            operator fun invoke(totalPrice: Amount): MarketBuy {
                ensure(totalPrice.isPositive) { InvalidOrderRequest("시장가 매수 총액은 0보다 커야 합니다") }
                return MarketBuy(totalPrice)
            }
        }
    }

    data class MarketSell private constructor(val volume: Volume) : OrderType {
        companion object {
            context(_: Raise<OrderError>)
            operator fun invoke(volume: Volume): MarketSell {
                ensure(volume.isPositive) { InvalidOrderRequest("시장가 매도 수량은 0보다 커야 합니다") }
                return MarketSell(volume)
            }
        }
    }

    data class Best private constructor(val volume: Volume) : OrderType {
        companion object {
            context(_: Raise<OrderError>)
            operator fun invoke(volume: Volume): Best {
                ensure(volume.isPositive) { InvalidOrderRequest("최유리 주문 수량은 0보다 커야 합니다") }
                return Best(volume)
            }
        }
    }
}

enum class OrderState {
    WAIT,
    WATCH,
    DONE,
    CANCEL,
}

data class UnvalidatedOrderRequest(
    val pair: String,
    val side: String,
    val orderType: String,
    val volume: String?,
    val price: String?,
)

data class ValidatedOrderRequest(
    val pair: TradingPair,
    val side: OrderSide,
    val orderType: OrderType,
)

/**
 * 주문 도메인 모델.
 *
 * 설계 노트:
 * - orderType에 이미 volume/price 정보가 포함되어 있습니다.
 * - remainingVolume은 체결 진행 상태를 추적하기 위한 필드입니다.
 * - 주문 타입별 속성 접근은 limitVolume(), limitPrice() 등의
 *   Raise 컨텍스트 함수를 사용하세요.
 *
 * 불변식:
 * - side와 orderType의 정합성 (MarketBuy는 BID만, MarketSell은 ASK만)
 * - remainingVolume + executedVolume == 총 주문 수량 (Limit, MarketSell, Best)
 * - 완료된 주문(DONE)은 remainingVolume이 0이어야 함
 * - 종료된 주문(DONE, CANCEL)은 doneAt이 있어야 함
 *
 * @property orderType 주문 유형 (Limit, MarketBuy, MarketSell, Best) - 수량/가격 정보 포함
 * @property remainingVolume 미체결 잔량 (부분 체결 시 추적용)
 * @property executedVolume 체결된 수량
 * @property executedAmount 체결된 금액 (수량 × 체결가)
 */
data class Order private constructor(
    val id: OrderId,
    val pair: TradingPair,
    val side: OrderSide,
    val orderType: OrderType,
    val state: OrderState,
    val remainingVolume: Volume,
    val executedVolume: Volume,
    val executedAmount: Amount,
    val paidFee: Amount,
    val createdAt: Instant,
    val doneAt: Instant?,
) {
    companion object {
        context(_: Raise<OrderError>)
        operator fun invoke(
            id: OrderId,
            pair: TradingPair,
            side: OrderSide,
            orderType: OrderType,
            state: OrderState,
            remainingVolume: Volume,
            executedVolume: Volume,
            executedAmount: Amount,
            paidFee: Amount,
            createdAt: Instant,
            doneAt: Instant?,
        ): Order {
            // 불변식 검증: side와 orderType 정합성
            when (orderType) {
                is OrderType.MarketBuy -> ensure(side == OrderSide.BID) {
                    InvalidOrderRequest("시장가 매수(MarketBuy)는 BID만 가능합니다")
                }
                is OrderType.MarketSell -> ensure(side == OrderSide.ASK) {
                    InvalidOrderRequest("시장가 매도(MarketSell)는 ASK만 가능합니다")
                }
                is OrderType.Limit, is OrderType.Best -> { /* 양방향 가능 */ }
            }

            // 불변식 검증: 완료된 주문은 잔량이 0이어야 함
            if (state == OrderState.DONE) {
                ensure(remainingVolume.isZero) {
                    InvalidOrderRequest("완료된 주문의 미체결 잔량은 0이어야 합니다")
                }
            }

            // 불변식 검증: 종료된 주문(DONE, CANCEL)은 doneAt이 있어야 함
            if (state == OrderState.DONE || state == OrderState.CANCEL) {
                ensureNotNull(doneAt) {
                    InvalidOrderRequest("종료된 주문은 완료 시각이 있어야 합니다")
                }
            }

            // 불변식 검증: 수량 기반 주문의 경우 remaining + executed == total
            when (orderType) {
                is OrderType.Limit -> {
                    val total = orderType.volume.value
                    val sum = remainingVolume.value + executedVolume.value
                    ensure(sum.compareTo(total) == 0) {
                        InvalidOrderRequest("미체결 잔량($remainingVolume) + 체결 수량($executedVolume) != 총 수량($total)")
                    }
                }
                is OrderType.MarketSell -> {
                    val total = orderType.volume.value
                    val sum = remainingVolume.value + executedVolume.value
                    ensure(sum.compareTo(total) == 0) {
                        InvalidOrderRequest("미체결 잔량($remainingVolume) + 체결 수량($executedVolume) != 총 수량($total)")
                    }
                }
                is OrderType.Best -> {
                    val total = orderType.volume.value
                    val sum = remainingVolume.value + executedVolume.value
                    ensure(sum.compareTo(total) == 0) {
                        InvalidOrderRequest("미체결 잔량($remainingVolume) + 체결 수량($executedVolume) != 총 수량($total)")
                    }
                }
                is OrderType.MarketBuy -> {
                    // 시장가 매수는 금액 기반이므로 수량 불변식 검증 생략
                    // remainingAmount()로 잔여 주문금액 조회 가능
                }
            }

            return Order(
                id, pair, side, orderType, state,
                remainingVolume, executedVolume, executedAmount,
                paidFee, createdAt, doneAt
            )
        }
    }

    val isOpen: Boolean get() = state == OrderState.WAIT || state == OrderState.WATCH
    val isCancellable: Boolean get() = isOpen
    val isClosed: Boolean get() = state == OrderState.DONE || state == OrderState.CANCEL

    /**
     * 시장가 매수 주문의 미체결 잔여 금액.
     * 시장가 매수가 아닌 경우 null 반환.
     */
    fun remainingAmount(): Amount? = when (val type = orderType) {
        is OrderType.MarketBuy -> {
            val remaining = type.totalPrice.value - executedAmount.value
            if (remaining >= BigDecimal.ZERO) {
                recover({ Amount(remaining) }) { null }
            } else null
        }
        else -> null
    }

    fun averageExecutedPrice(): Price? {
        if (executedVolume.isZero) return null
        val avgPrice = executedAmount.value.divide(
            executedVolume.value,
            DecimalConfig.PRICE_SCALE,
            DecimalConfig.ROUNDING_MODE
        )
        return recover({ Price(avgPrice) }) { null }
    }

    /**
     * 주문 체결률(%)을 계산합니다.
     * orderType에서 직접 총 수량/금액을 가져와 계산합니다.
     */
    fun executionRate(): BigDecimal = when (val type = orderType) {
        is OrderType.Limit -> {
            val totalVolume = type.volume.value
            if (totalVolume == BigDecimal.ZERO) BigDecimal.ZERO
            else executedVolume.value
                .divide(totalVolume, DecimalConfig.PERCENT_SCALE + 2, DecimalConfig.ROUNDING_MODE)
                .multiply(BigDecimal(100))
                .setScale(DecimalConfig.PERCENT_SCALE, DecimalConfig.ROUNDING_MODE)
        }
        is OrderType.MarketBuy -> {
            val totalPrice = type.totalPrice.value
            if (totalPrice == BigDecimal.ZERO) BigDecimal.ZERO
            else executedAmount.value
                .divide(totalPrice, DecimalConfig.PERCENT_SCALE + 2, DecimalConfig.ROUNDING_MODE)
                .multiply(BigDecimal(100))
                .setScale(DecimalConfig.PERCENT_SCALE, DecimalConfig.ROUNDING_MODE)
        }
        is OrderType.MarketSell -> {
            val totalVolume = type.volume.value
            if (totalVolume == BigDecimal.ZERO) BigDecimal.ZERO
            else executedVolume.value
                .divide(totalVolume, DecimalConfig.PERCENT_SCALE + 2, DecimalConfig.ROUNDING_MODE)
                .multiply(BigDecimal(100))
                .setScale(DecimalConfig.PERCENT_SCALE, DecimalConfig.ROUNDING_MODE)
        }
        is OrderType.Best -> {
            val totalVolume = type.volume.value
            if (totalVolume == BigDecimal.ZERO) BigDecimal.ZERO
            else executedVolume.value
                .divide(totalVolume, DecimalConfig.PERCENT_SCALE + 2, DecimalConfig.ROUNDING_MODE)
                .multiply(BigDecimal(100))
                .setScale(DecimalConfig.PERCENT_SCALE, DecimalConfig.ROUNDING_MODE)
        }
    }
}

/**
 * 주문 타입별 속성 접근 함수들.
 * 타입 안전성을 위해 확장 프로퍼티 대신 Raise 컨텍스트를 사용하는 함수로 정의합니다.
 * 잘못된 주문 타입에서 호출 시 예외 대신 OrderError를 raise합니다.
 */

context(_: Raise<OrderError>)
fun Order.limitVolume(): Volume = when (val type = orderType) {
    is OrderType.Limit -> type.volume
    else -> raise(InvalidOrderRequest("지정가 주문이 아닙니다: ${orderType::class.simpleName}"))
}

context(_: Raise<OrderError>)
fun Order.limitPrice(): Price = when (val type = orderType) {
    is OrderType.Limit -> type.price
    else -> raise(InvalidOrderRequest("지정가 주문이 아닙니다: ${orderType::class.simpleName}"))
}

context(_: Raise<OrderError>)
fun Order.marketBuyTotalPrice(): Amount = when (val type = orderType) {
    is OrderType.MarketBuy -> type.totalPrice
    else -> raise(InvalidOrderRequest("시장가 매수 주문이 아닙니다: ${orderType::class.simpleName}"))
}

context(_: Raise<OrderError>)
fun Order.sellVolume(): Volume = when (val type = orderType) {
    is OrderType.MarketSell -> type.volume
    is OrderType.Best -> type.volume
    is OrderType.Limit -> if (side == OrderSide.ASK) type.volume
                          else raise(InvalidOrderRequest("매도 주문이 아닙니다"))
    else -> raise(InvalidOrderRequest("매도 수량을 가져올 수 없는 주문 타입: ${orderType::class.simpleName}"))
}

context(_: Raise<OrderError>)
fun UnvalidatedOrderRequest.validate(): ValidatedOrderRequest {
    val pair = withError({ ValidationFailed(it.message) }) {
        TradingPair(this@validate.pair)
    }

    val side = OrderSide.entries.find { it.name == this.side.uppercase() }
        ?: raise(InvalidOrderRequest("올바르지 않은 주문 방향: ${this.side}"))

    val orderType = when (this.orderType.lowercase()) {
        "limit" -> {
            ensureNotNull(this.volume) { InvalidOrderRequest("지정가 주문은 수량이 필요합니다") }
            ensureNotNull(this.price) { InvalidOrderRequest("지정가 주문은 가격이 필요합니다") }
            val volume = withError({ ValidationFailed(it.message) }) { Volume(this@validate.volume) }
            val price = withError({ ValidationFailed(it.message) }) { Price(this@validate.price) }
            OrderType.Limit(volume, price)
        }
        "price" -> {
            ensure(side == OrderSide.BID) { InvalidOrderRequest("시장가 매수는 BID만 가능합니다") }
            ensureNotNull(this.price) { InvalidOrderRequest("시장가 매수는 총액이 필요합니다") }
            val priceValue = this@validate.price.toBigDecimalOrNull()
                ?: raise(ValidationFailed("숫자 형식이 아닙니다: ${this@validate.price}"))
            val totalPrice = withError({ ValidationFailed(it.message) }) { Amount(priceValue) }
            OrderType.MarketBuy(totalPrice)
        }
        "market" -> {
            ensure(side == OrderSide.ASK) { InvalidOrderRequest("시장가 매도는 ASK만 가능합니다") }
            ensureNotNull(this.volume) { InvalidOrderRequest("시장가 매도는 수량이 필요합니다") }
            val volume = withError({ ValidationFailed(it.message) }) { Volume(this@validate.volume) }
            OrderType.MarketSell(volume)
        }
        "best" -> {
            ensureNotNull(this.volume) { InvalidOrderRequest("최유리 주문은 수량이 필요합니다") }
            val volume = withError({ ValidationFailed(it.message) }) { Volume(this@validate.volume) }
            OrderType.Best(volume)
        }
        else -> raise(InvalidOrderRequest("지원하지 않는 주문 타입: ${this.orderType}"))
    }

    return ValidatedOrderRequest(pair, side, orderType)
}

/**
 * 최소 주문 금액 검증.
 *
 * @param orderChance 주문 가능 정보 (최소 주문금액 포함)
 * @param currentPrice 현재가 (시장가 매도/Best 주문 시 필수)
 * @throws CurrentPriceRequired 시장가 매도/Best 주문에서 currentPrice가 없는 경우
 */
context(_: Raise<OrderError>)
fun ValidatedOrderRequest.validateMinimumOrderAmount(
    orderChance: OrderChance,
    currentPrice: Price? = null,
) {
    val minimumAmount = orderChance.minOrderAmount

    val orderAmount: Amount = when (val type = orderType) {
        is OrderType.Limit -> {
            recover({ Amount(type.volume.value * type.price.value) }) {
                raise(ValidationFailed("주문 금액 계산 실패"))
            }
        }
        is OrderType.MarketBuy -> type.totalPrice
        is OrderType.MarketSell -> {
            val price = ensureNotNull(currentPrice) {
                CurrentPriceRequired("시장가 매도 주문의 최소금액 검증에는 현재가가 필요합니다")
            }
            recover({ Amount(type.volume.value * price.value) }) {
                raise(ValidationFailed("주문 금액 계산 실패"))
            }
        }
        is OrderType.Best -> {
            val price = ensureNotNull(currentPrice) {
                CurrentPriceRequired("최유리 주문의 최소금액 검증에는 현재가가 필요합니다")
            }
            recover({ Amount(type.volume.value * price.value) }) {
                raise(ValidationFailed("주문 금액 계산 실패"))
            }
        }
    }

    ensure(orderAmount >= minimumAmount) {
        MinimumOrderAmountNotMet(minimumAmount, orderAmount)
    }
}

context(_: Raise<OrderError>)
fun ValidatedOrderRequest.validateTickSize() {
    val price = when (val type = orderType) {
        is OrderType.Limit -> type.price
        else -> return
    }

    val tickSize = TickSize.forMarket(pair.market, price.value)
    val remainder = price.value.remainder(tickSize.value)

    ensure(remainder.compareTo(BigDecimal.ZERO) == 0) {
        InvalidPriceUnit(price, tickSize)
    }
}

/**
 * 체결 ID.
 *
 * 개별 체결 건을 고유하게 식별합니다.
 * 부분 체결 시 동일 주문에 여러 체결 ID가 발생할 수 있습니다.
 */
@JvmInline
value class TradeId private constructor(val value: String) {
    companion object {
        context(_: Raise<OrderError>)
        operator fun invoke(value: String): TradeId {
            ensure(value.isNotBlank()) { InvalidOrderRequest("체결 ID는 비어있을 수 없습니다") }
            return TradeId(value)
        }
    }
}

/**
 * 주문 이벤트.
 *
 * 설계 노트:
 * - occurredAt은 기본값 없이 명시적으로 전달받습니다 (테스트 용이성).
 * - 편의를 위해 now() 팩토리 함수를 제공합니다.
 * - OrderExecuted는 개별 체결 건을 나타냅니다 (부분 체결 시 여러 이벤트 발생).
 */
sealed interface OrderEvent {
    val orderId: OrderId
    val occurredAt: Instant

    data class OrderCreated(
        override val orderId: OrderId,
        val pair: TradingPair,
        val side: OrderSide,
        val orderType: OrderType,
        override val occurredAt: Instant,
    ) : OrderEvent {
        companion object {
            fun now(
                orderId: OrderId,
                pair: TradingPair,
                side: OrderSide,
                orderType: OrderType,
            ) = OrderCreated(orderId, pair, side, orderType, Instant.now())
        }
    }

    /**
     * 주문 체결 이벤트.
     *
     * 개별 체결 건을 나타냅니다. 부분 체결 시 동일 orderId로 여러 이벤트가 발생합니다.
     * tradeId로 개별 체결을 구분하여 멱등성 처리에 활용할 수 있습니다.
     *
     * @property tradeId 개별 체결 ID (멱등성 처리용)
     * @property executedVolume 이번 체결에서 체결된 수량 (누적 아님)
     * @property executedPrice 체결 가격
     */
    data class OrderExecuted(
        override val orderId: OrderId,
        val tradeId: TradeId,
        val executedVolume: Volume,
        val executedPrice: Price,
        val fee: Amount,
        override val occurredAt: Instant,
    ) : OrderEvent {
        companion object {
            fun now(
                orderId: OrderId,
                tradeId: TradeId,
                executedVolume: Volume,
                executedPrice: Price,
                fee: Amount,
            ) = OrderExecuted(orderId, tradeId, executedVolume, executedPrice, fee, Instant.now())
        }
    }

    data class OrderCancelled(
        override val orderId: OrderId,
        override val occurredAt: Instant,
    ) : OrderEvent {
        companion object {
            fun now(orderId: OrderId) = OrderCancelled(orderId, Instant.now())
        }
    }
}

sealed interface OrderError {
    data class InvalidOrderRequest(val reason: String) : OrderError
    data class ValidationFailed(val reason: String) : OrderError
    data class InsufficientBalance(val required: Amount, val available: Amount) : OrderError
    data class MinimumOrderAmountNotMet(val minimum: Amount, val actual: Amount) : OrderError
    data class InvalidPriceUnit(val price: Price, val expectedTickSize: TickSize) : OrderError
    data class CurrentPriceRequired(val reason: String) : OrderError
    data class OrderNotFound(val id: OrderId) : OrderError
    data class OrderNotCancellable(val id: OrderId, val state: OrderState) : OrderError
}
```

### 3.4 계정 도메인 (Account Domain)

```kotlin
package com.cryptoquant.domain.account

@JvmInline
value class Currency private constructor(val value: String) {
    companion object {
        context(_: Raise<DomainError>)
        operator fun invoke(value: String): Currency {
            ensure(value.isNotBlank()) { InvalidCurrency("통화 코드는 비어있을 수 없습니다") }
            return Currency(value.uppercase())
        }

        val KRW = Currency("KRW")
        val BTC = Currency("BTC")
        val USDT = Currency("USDT")
    }
}

/**
 * 계정의 특정 통화 잔고를 나타냅니다.
 *
 * 불변식:
 * - locked <= balance (주문에 묶인 수량은 보유 수량을 초과할 수 없음)
 *
 * @property balance 보유 수량 (예: 0.5 BTC)
 * @property locked 주문에 묶인 수량
 * @property avgBuyPrice 평균 매수가 (quote currency 기준, 예: KRW)
 */
data class Balance private constructor(
    val currency: Currency,
    val balance: Volume,
    val locked: Volume,
    val avgBuyPrice: AvgBuyPrice,
    val avgBuyPriceModified: Boolean,
) {
    /**
     * 사용 가능한 수량 (balance - locked).
     * 불변식이 보장되므로 항상 0 이상입니다.
     */
    val available: Volume get() = balance - locked ?: Volume.ZERO

    companion object {
        context(_: Raise<DomainError>)
        operator fun invoke(
            currency: Currency,
            balance: Volume,
            locked: Volume,
            avgBuyPrice: AvgBuyPrice,
            avgBuyPriceModified: Boolean,
        ): Balance {
            ensure(locked <= balance) {
                InvalidBalance("locked($locked)가 balance($balance)를 초과할 수 없습니다")
            }
            return Balance(currency, balance, locked, avgBuyPrice, avgBuyPriceModified)
        }
    }

    /**
     * 현재 가격 기준 총 평가금액을 계산합니다.
     * Volume × Price = Amount (예: 0.5 BTC × 100,000,000 KRW/BTC = 50,000,000 KRW)
     */
    fun totalValue(currentPrice: Price): Amount {
        val value = balance.value * currentPrice.value
        return recover({ Amount(value) }) { Amount.ZERO }
    }

    /**
     * 현재 가격 기준 평가손익을 계산합니다.
     * (현재가 - 평균매수가) × 수량
     */
    fun profitLoss(currentPrice: Price): BigDecimal {
        val currentValue = balance.value * currentPrice.value
        val buyValue = balance.value * avgBuyPrice.value
        return currentValue - buyValue
    }

    /**
     * 현재 가격 기준 수익률(%)을 계산합니다.
     * ((현재가 - 평균매수가) / 평균매수가) × 100
     */
    fun profitLossRate(currentPrice: Price): BigDecimal {
        if (avgBuyPrice.isZero) return BigDecimal.ZERO
        return (currentPrice.value - avgBuyPrice.value)
            .divide(avgBuyPrice.value, DecimalConfig.PERCENT_SCALE + 2, DecimalConfig.ROUNDING_MODE)
            .multiply(BigDecimal(100))
            .setScale(DecimalConfig.PERCENT_SCALE, DecimalConfig.ROUNDING_MODE)
    }
}

data class Account(
    val balances: List<Balance>,
) {
    fun getBalance(currency: Currency): Balance? = balances.find { it.currency == currency }
    fun getAvailableBalance(currency: Currency): Volume = getBalance(currency)?.available ?: Volume.ZERO
    fun hasBalance(currency: Currency): Boolean = getBalance(currency)?.balance?.isPositive ?: false
}

data class OrderChance(
    val pair: TradingPair,
    val bidFee: FeeRate,
    val askFee: FeeRate,
    val bidAccount: Balance,
    val askAccount: Balance,
    val minOrderAmount: Amount,
)
```

### 3.5 공통 도메인 오류

```kotlin
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
```

### 3.6 인프라스트럭처 오류

인프라 오류는 도메인이 아닌 인프라스트럭처 계층에 정의합니다.
도메인 순수성을 유지하기 위해 외부 서비스 관련 오류는 분리합니다.

```kotlin
package com.cryptoquant.infrastructure.upbit

/**
 * 업비트 거래소 API 오류.
 * 인프라스트럭처 계층에서 정의하며, 도메인 계층에서는 참조하지 않습니다.
 */
sealed interface UpbitExchangeError {
    val code: String
    val message: String

    data class ApiError(override val code: String, override val message: String) : UpbitExchangeError
    data class NetworkError(override val code: String, override val message: String) : UpbitExchangeError
    data class AuthenticationError(override val code: String, override val message: String) : UpbitExchangeError
    data class RateLimitError(override val code: String, override val message: String) : UpbitExchangeError
}

/**
 * 업비트 시세 API 오류.
 */
sealed interface UpbitQuotationError {
    val code: String
    val message: String

    data class ApiError(override val code: String, override val message: String) : UpbitQuotationError
    data class NetworkError(override val code: String, override val message: String) : UpbitQuotationError
    data class InvalidResponse(override val code: String, override val message: String) : UpbitQuotationError
}
```

---

## 4. 인터페이스 정의

### 4.1 게이트웨이 인터페이스 (포트)

도메인 계층에 정의되는 아웃바운드 포트입니다.
인프라 계층에서 구현체를 제공합니다 (Hexagonal Architecture).

```kotlin
package com.cryptoquant.domain.gateway

/**
 * 게이트웨이 오류.
 *
 * 외부 서비스 호출 시 발생할 수 있는 도메인 수준의 오류입니다.
 * 인프라 계층에서 발생하는 구체적인 오류는 이 타입으로 변환됩니다.
 */
sealed interface GatewayError {
    val code: String
    val message: String

    data class NetworkError(override val code: String, override val message: String) : GatewayError
    data class AuthenticationError(override val code: String, override val message: String) : GatewayError
    data class RateLimitError(override val code: String, override val message: String) : GatewayError
    data class ApiError(override val code: String, override val message: String) : GatewayError
    data class InvalidResponse(override val code: String, override val message: String) : GatewayError
}

/**
 * 페이지네이션 요청 파라미터.
 *
 * @property limit 조회할 최대 개수 (1~200, 기본 100)
 * @property cursor 페이지 커서 (이전 응답의 마지막 ID)
 */
data class PageRequest(
    val limit: Int = 100,
    val cursor: String? = null,
) {
    init {
        require(limit in 1..200) { "limit은 1~200 범위여야 합니다" }
    }
}

/**
 * 페이지네이션 응답.
 *
 * @property items 조회된 항목들
 * @property nextCursor 다음 페이지 커서 (null이면 마지막 페이지)
 */
data class PageResponse<T>(
    val items: List<T>,
    val nextCursor: String?,
) {
    val hasNext: Boolean get() = nextCursor != null
}

/**
 * 거래소 게이트웨이.
 *
 * 주문, 잔고 조회 등 인증이 필요한 거래소 API와의 인터페이스입니다.
 */
interface ExchangeGateway {
    context(_: Raise<GatewayError>)
    suspend fun placeOrder(request: ValidatedOrderRequest): Order

    context(_: Raise<GatewayError>)
    suspend fun cancelOrder(orderId: OrderId): Order

    context(_: Raise<GatewayError>)
    suspend fun getOrder(orderId: OrderId): Order

    /**
     * 미체결 주문 목록 조회.
     *
     * @param pair 조회할 마켓 (null이면 전체)
     * @param page 페이지네이션 파라미터
     */
    context(_: Raise<GatewayError>)
    suspend fun getOpenOrders(pair: TradingPair?, page: PageRequest = PageRequest()): PageResponse<Order>

    context(_: Raise<GatewayError>)
    suspend fun getBalances(): List<Balance>

    context(_: Raise<GatewayError>)
    suspend fun getOrderChance(pair: TradingPair): OrderChance
}

/**
 * 시세 게이트웨이.
 *
 * 캔들, 호가, 체결 내역 등 시세 조회 API와의 인터페이스입니다.
 */
interface QuotationGateway {
    /**
     * 캔들 조회.
     *
     * @param pair 마켓
     * @param unit 캔들 단위 (Seconds는 WebSocket만 지원)
     * @param count 조회 개수 (최대 200)
     * @param to 마지막 캔들 시각 (null이면 최신)
     */
    context(_: Raise<GatewayError>)
    suspend fun getCandles(
        pair: TradingPair,
        unit: CandleUnit,
        count: Int = 200,
        to: Instant? = null,
    ): List<Candle>

    context(_: Raise<GatewayError>)
    suspend fun getTicker(pairs: List<TradingPair>): List<Ticker>

    context(_: Raise<GatewayError>)
    suspend fun getOrderbook(pairs: List<TradingPair>): List<Orderbook>

    /**
     * 체결 내역 조회.
     *
     * @param pair 마켓
     * @param count 조회 개수 (최대 500)
     * @param cursor 페이지 커서 (sequentialId 기준)
     */
    context(_: Raise<GatewayError>)
    suspend fun getTrades(
        pair: TradingPair,
        count: Int = 100,
        cursor: TradeSequentialId? = null,
    ): List<Trade>
}

/**
 * 실시간 데이터 스트림.
 *
 * WebSocket을 통한 실시간 데이터 수신 인터페이스입니다.
 */
interface RealtimeStream {
    fun subscribeTicker(pairs: List<TradingPair>): Flow<Ticker>
    fun subscribeOrderbook(pairs: List<TradingPair>): Flow<Orderbook>
    fun subscribeTrade(pairs: List<TradingPair>): Flow<Trade>
    fun subscribeMyOrder(): Flow<OrderEvent>
}
```

### 4.2 저장소 인터페이스

```kotlin
package com.cryptoquant.domain.repository

interface OrderRepository {
    suspend fun save(order: Order): Unit
    suspend fun findById(orderId: OrderId): Order?
    suspend fun findOpenOrders(pair: TradingPair?, page: PageRequest = PageRequest()): PageResponse<Order>
}
```

---

## 5. 기술적 결정사항

### 5.1 기술 선택

| 항목 | 선택 | 대안 | 선택 이유 |
|------|------|------|-----------|
| FP 라이브러리 | Arrow 2.2.x | kotlin-result, Arrow 1.x | Raise DSL + Context Parameters API로 명시적 오류 처리, `either` + `bind()` 스타일 지원 |
| HTTP 클라이언트 | WebClient | Ktor Client, OkHttp | Spring WebFlux와 자연스러운 통합, 리액티브 스트림 지원 |
| WebSocket | Spring WebSocket | Ktor WebSocket | Spring 생태계 일관성 유지 |
| JSON 직렬화 | kotlinx.serialization | Jackson, Gson | Kotlin 친화적, 컴파일 타임 안전성 |
| DB 접근 | R2DBC | JDBC, jOOQ | 논블로킹 DB 접근, WebFlux와 일관성 |

