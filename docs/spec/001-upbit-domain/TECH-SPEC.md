# 업비트 도메인 모델 설계 테크스펙

> **작성일**: 2025-12-28
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
- 자동매매 전략 (Strategy): RSI 등 기술적 지표 기반 전략
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
| Pair | Market-Ticker 조합 | KRW-BTC, BTC-ETH |
| Candle | 특정 시간 단위의 OHLCV 데이터 | 1분봉, 일봉 |
| Orderbook | 매수/매도 호가 목록 | bid/ask 가격 및 수량 |
| Side | 주문 방향 | BID(매수), ASK(매도) |

### 1.4 관련 문서
- [업비트 개발자 센터](https://docs.upbit.com)
- [REST API Best Practice](https://docs.upbit.com/kr/docs/rest-api-best-practice.md)
- [WebSocket Best Practice](https://docs.upbit.com/kr/docs/websocket-best-practice.md)

---

## 2. 아키텍처 개요

```
┌──────────────────────────────────────────────────────────────────┐
│                        Presentation Layer                         │
│  (REST Controller, WebSocket Handler, Strategy Scheduler)        │
└─────────────────────────────┬────────────────────────────────────┘
                              │
┌─────────────────────────────▼────────────────────────────────────┐
│                        Application Layer                          │
│  (UseCase: PlaceOrder, GetQuotation, ExecuteStrategy)            │
└─────────────────────────────┬────────────────────────────────────┘
                              │
┌─────────────────────────────▼────────────────────────────────────┐
│                         Domain Layer                              │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌──────────┐ │
│  │  Quotation  │  │    Order    │  │   Account   │  │ Strategy │ │
│  │   Domain    │  │   Domain    │  │   Domain    │  │  Domain  │ │
│  └─────────────┘  └─────────────┘  └─────────────┘  └──────────┘ │
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

/**
 * 마켓 통화 (호가 통화)
 *
 * 불변 규칙:
 * - KRW, BTC, USDT 중 하나
 */
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

/**
 * 거래 페어 (마켓-티커)
 *
 * 불변 규칙:
 * - 형식: {MARKET}-{TICKER} (예: KRW-BTC)
 * - 업비트에서 지원하는 페어만 허용
 */
@JvmInline
value class Pair private constructor(val value: String) {
    val market: Market get() = Market.valueOf(value.substringBefore("-"))
    val ticker: String get() = value.substringAfter("-")

    companion object {
        context(_: Raise<DomainError>)
        operator fun invoke(value: String): Pair {
            ensure(value.contains("-")) { InvalidPair("페어 형식이 올바르지 않습니다: $value") }
            val (market, ticker) = value.uppercase().split("-", limit = 2)
            Market.from(market) // 마켓 검증
            ensure(ticker.isNotBlank()) { InvalidPair("티커가 비어있습니다") }
            return Pair("$market-$ticker")
        }

        context(_: Raise<DomainError>)
        operator fun invoke(market: Market, ticker: String): Pair {
            ensure(ticker.isNotBlank()) { InvalidPair("티커가 비어있습니다") }
            return Pair("${market.name}-${ticker.uppercase()}")
        }
    }
}

/**
 * 가격
 *
 * 불변 규칙:
 * - 0보다 커야 함
 *
 * 참고: 호가단위(틱 사이즈) 검증은 마켓별로 다르므로
 * TickSize를 통해 별도로 검증해야 함
 */
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

    /**
     * 호가단위에 맞게 가격 조정 (내림)
     */
    fun adjustToTickSize(tickSize: TickSize): Price {
        val adjusted = (value / tickSize.value).setScale(0, RoundingMode.DOWN) * tickSize.value
        return Price(adjusted)
    }

    /**
     * 호가단위 검증
     */
    context(_: Raise<DomainError>)
    fun validateTickSize(tickSize: TickSize) {
        val remainder = value.remainder(tickSize.value)
        ensure(remainder.compareTo(BigDecimal.ZERO) == 0) {
            InvalidTickSize("가격 $value 은(는) 호가단위 ${tickSize.value}에 맞지 않습니다")
        }
    }
}

/**
 * 수량
 *
 * 불변 규칙:
 * - 0보다 커야 함
 * - 소수점 8자리까지 허용
 */
@JvmInline
value class Volume private constructor(val value: BigDecimal) {
    companion object {
        context(_: Raise<DomainError>)
        operator fun invoke(value: BigDecimal): Volume {
            ensure(value > BigDecimal.ZERO) { InvalidVolume("수량은 0보다 커야 합니다") }
            ensure(value.scale() <= 8) { InvalidVolume("소수점 8자리까지만 허용됩니다") }
            return Volume(value)
        }

        context(_: Raise<DomainError>)
        operator fun invoke(value: String): Volume = invoke(value.toBigDecimalOrNull()
            ?: raise(InvalidVolume("숫자 형식이 아닙니다: $value")))
    }
}

/**
 * 금액 (가격 * 수량)
 */
@JvmInline
value class Amount(val value: BigDecimal) {
    operator fun plus(other: Amount): Amount = Amount(value + other.value)
    operator fun minus(other: Amount): Amount = Amount(value - other.value)
    operator fun compareTo(other: Amount): Int = value.compareTo(other.value)

    companion object {
        val ZERO = Amount(BigDecimal.ZERO)
    }
}

/**
 * 호가단위 (틱 사이즈)
 *
 * 마켓별로 다른 호가단위 정책을 적용
 */
@JvmInline
value class TickSize(val value: BigDecimal) {
    companion object {
        /**
         * KRW 마켓 호가단위 조회
         *
         * 가격대별 호가단위:
         * - 2,000,000원 이상: 1,000원
         * - 1,000,000 ~ 2,000,000원: 1,000원
         * - 500,000 ~ 1,000,000원: 500원
         * - 100,000 ~ 500,000원: 100원
         * - 50,000 ~ 100,000원: 50원
         * - 10,000 ~ 50,000원: 10원
         * - 5,000 ~ 10,000원: 5원
         * - 1,000 ~ 5,000원: 1원
         * - 100 ~ 1,000원: 1원
         * - 10 ~ 100원: 0.1원
         * - 1 ~ 10원: 0.01원
         * - 0.1 ~ 1원: 0.001원
         * - 0.01 ~ 0.1원: 0.0001원
         * - 0.001 ~ 0.01원: 0.00001원
         * - 0.0001 ~ 0.001원: 0.000001원
         * - 0.00001 ~ 0.0001원: 0.0000001원
         * - 0.00001원 미만: 0.00000001원
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

        /**
         * BTC 마켓 호가단위 조회
         *
         * 가격과 무관하게 단일 호가단위: 0.00000001 BTC (1 사토시)
         */
        fun forBtcMarket(): TickSize = TickSize(BigDecimal("0.00000001"))

        /**
         * USDT 마켓 호가단위 조회
         *
         * 가격대별 호가단위 (KRW 마켓과 유사한 구조)
         * TODO: 업비트 공식 문서 확인 후 정확한 값으로 업데이트 필요
         */
        fun forUsdtMarket(price: BigDecimal): TickSize = when {
            price >= BigDecimal("1000") -> TickSize(BigDecimal("0.001"))
            price >= BigDecimal("100") -> TickSize(BigDecimal("0.0001"))
            price >= BigDecimal("10") -> TickSize(BigDecimal("0.00001"))
            price >= BigDecimal("1") -> TickSize(BigDecimal("0.000001"))
            price >= BigDecimal("0.1") -> TickSize(BigDecimal("0.0000001"))
            else -> TickSize(BigDecimal("0.00000001"))
        }

        /**
         * 마켓과 가격에 따른 호가단위 조회
         */
        fun forMarket(market: Market, price: BigDecimal): TickSize = when (market) {
            Market.KRW -> forKrwMarket(price)
            Market.BTC -> forBtcMarket()
            Market.USDT -> forUsdtMarket(price)
        }
    }
}

/**
 * 타임스탬프 (밀리초)
 */
@JvmInline
value class Timestamp(val value: Long) {
    fun toInstant(): Instant = Instant.ofEpochMilli(value)

    companion object {
        fun now(): Timestamp = Timestamp(System.currentTimeMillis())
        fun from(instant: Instant): Timestamp = Timestamp(instant.toEpochMilli())
    }
}
```

### 3.2 시세 도메인 (Quotation Domain)

```kotlin
package com.cryptoquant.domain.quotation

/**
 * 캔들 단위
 */
sealed interface CandleUnit {
    val code: String

    data class Seconds(val seconds: Int) : CandleUnit {
        override val code: String = "${seconds}s"
    }

    data class Minutes(val minutes: Int) : CandleUnit {
        override val code: String = "${minutes}m"

        companion object {
            context(_: Raise<DomainError>)
            operator fun invoke(minutes: Int): Minutes {
                ensure(minutes in listOf(1, 3, 5, 15, 30, 60, 240)) {
                    InvalidCandleUnit("지원하지 않는 분봉 단위: $minutes")
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

    object Year : CandleUnit {
        override val code: String = "1Y"
    }
}

/**
 * 캔들 (OHLCV)
 *
 * 불변식:
 * - high >= low
 * - high >= open, close
 * - low <= open, close
 */
data class Candle(
    val pair: Pair,
    val unit: CandleUnit,
    val timestamp: Timestamp,
    val openingPrice: Price,
    val highPrice: Price,
    val lowPrice: Price,
    val closingPrice: Price,
    val volume: Volume,
    val amount: Amount,
) {
    init {
        require(highPrice.value >= lowPrice.value) { "고가는 저가보다 크거나 같아야 합니다" }
        require(highPrice.value >= openingPrice.value) { "고가는 시가보다 크거나 같아야 합니다" }
        require(highPrice.value >= closingPrice.value) { "고가는 종가보다 크거나 같아야 합니다" }
        require(lowPrice.value <= openingPrice.value) { "저가는 시가보다 작거나 같아야 합니다" }
        require(lowPrice.value <= closingPrice.value) { "저가는 종가보다 작거나 같아야 합니다" }
    }
}

/**
 * 현재가 (Ticker)
 */
data class Ticker(
    val pair: Pair,
    val tradePrice: Price,
    val openingPrice: Price,
    val highPrice: Price,
    val lowPrice: Price,
    val prevClosingPrice: Price,
    val change: Change,
    val changePrice: Price,
    val changeRate: BigDecimal,
    val signedChangePrice: BigDecimal,
    val signedChangeRate: BigDecimal,
    val tradeVolume: Volume,
    val accTradePrice24h: Amount,
    val accTradeVolume24h: Volume,
    val timestamp: Timestamp,
)

/**
 * 가격 변동 방향
 */
enum class Change {
    RISE,  // 상승
    EVEN,  // 보합
    FALL   // 하락
}

/**
 * 호가 단위
 */
data class OrderbookUnit(
    val askPrice: Price,    // 매도 호가
    val bidPrice: Price,    // 매수 호가
    val askSize: Volume,    // 매도 잔량
    val bidSize: Volume,    // 매수 잔량
)

/**
 * 호가
 */
data class Orderbook(
    val pair: Pair,
    val timestamp: Timestamp,
    val totalAskSize: Volume,
    val totalBidSize: Volume,
    val orderbookUnits: List<OrderbookUnit>,
) {
    /**
     * 최우선 매수 호가 (가장 높은 매수 가격)
     */
    val bestBidPrice: Price? get() = orderbookUnits.firstOrNull()?.bidPrice

    /**
     * 최우선 매도 호가 (가장 낮은 매도 가격)
     */
    val bestAskPrice: Price? get() = orderbookUnits.firstOrNull()?.askPrice

    /**
     * 스프레드 (매도-매수 호가 차이)
     */
    fun spread(): BigDecimal? {
        val ask = bestAskPrice?.value ?: return null
        val bid = bestBidPrice?.value ?: return null
        return ask - bid
    }
}

/**
 * 체결 내역
 */
data class Trade(
    val pair: Pair,
    val tradePrice: Price,
    val tradeVolume: Volume,
    val askBid: OrderSide,
    val prevClosingPrice: Price,
    val change: Change,
    val timestamp: Timestamp,
    val sequentialId: Long,
)
```

### 3.3 주문 도메인 (Order Domain)

```kotlin
package com.cryptoquant.domain.order

/**
 * 주문 ID
 */
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

/**
 * 주문 방향
 */
enum class OrderSide {
    BID,  // 매수
    ASK   // 매도
}

/**
 * 주문 유형
 *
 * 업비트 지원 주문 타입:
 * - limit: 지정가 주문
 * - price: 시장가 매수 (총액 지정)
 * - market: 시장가 매도 (수량 지정)
 * - best: 최유리 지정가 주문
 */
sealed interface OrderType {
    /**
     * 지정가 주문
     */
    data class Limit(
        val volume: Volume,
        val price: Price,
    ) : OrderType

    /**
     * 시장가 매수 (총액 지정)
     * - 매수 시: price에 총 매수 금액 지정
     */
    data class MarketBuy(
        val totalPrice: Amount,
    ) : OrderType

    /**
     * 시장가 매도 (수량 지정)
     * - 매도 시: volume에 매도 수량 지정
     */
    data class MarketSell(
        val volume: Volume,
    ) : OrderType

    /**
     * 최유리 지정가 주문
     * - 주문 시점의 최우선 호가로 주문
     */
    data class Best(
        val volume: Volume,
    ) : OrderType
}

/**
 * 주문 상태
 */
enum class OrderState {
    WAIT,     // 체결 대기
    WATCH,    // 예약 주문 대기
    DONE,     // 전체 체결 완료
    CANCEL,   // 주문 취소
}

/**
 * 미검증 주문 요청 (외부 입력)
 */
data class UnvalidatedOrderRequest(
    val pair: String,
    val side: String,
    val orderType: String,
    val volume: String?,
    val price: String?,
)

/**
 * 검증된 주문 요청
 */
data class ValidatedOrderRequest(
    val pair: Pair,
    val side: OrderSide,
    val orderType: OrderType,
)

/**
 * 주문 (생성됨)
 */
data class Order(
    val id: OrderId,
    val pair: Pair,
    val side: OrderSide,
    val orderType: OrderType,
    val state: OrderState,
    val volume: Volume?,
    val remainingVolume: Volume?,
    val price: Price?,
    val executedVolume: Volume,
    val executedAmount: Amount,
    val paidFee: Amount,
    val createdAt: Instant,
) {
    /**
     * 체결 가능 여부
     */
    val isOpen: Boolean get() = state == OrderState.WAIT || state == OrderState.WATCH

    /**
     * 취소 가능 여부
     */
    val isCancellable: Boolean get() = isOpen

    /**
     * 평균 체결가
     */
    fun averageExecutedPrice(): Price? {
        if (executedVolume.value == BigDecimal.ZERO) return null
        return Price(executedAmount.value / executedVolume.value)
    }

    /**
     * 체결률 (%)
     */
    fun executionRate(): BigDecimal? {
        val totalVolume = volume?.value ?: return null
        if (totalVolume == BigDecimal.ZERO) return BigDecimal.ZERO
        return (executedVolume.value / totalVolume) * BigDecimal(100)
    }
}

/**
 * 주문 검증 함수
 */
context(_: Raise<OrderError>)
fun UnvalidatedOrderRequest.validate(): ValidatedOrderRequest {
    val pair = Pair(this.pair)
    val side = OrderSide.entries.find { it.name == this.side.uppercase() }
        ?: raise(InvalidOrderRequest("올바르지 않은 주문 방향: ${this.side}"))

    val orderType = when (this.orderType.lowercase()) {
        "limit" -> {
            ensureNotNull(this.volume) { InvalidOrderRequest("지정가 주문은 수량이 필요합니다") }
            ensureNotNull(this.price) { InvalidOrderRequest("지정가 주문은 가격이 필요합니다") }
            OrderType.Limit(Volume(this.volume), Price(this.price))
        }
        "price" -> {
            ensure(side == OrderSide.BID) { InvalidOrderRequest("시장가 매수는 BID만 가능합니다") }
            ensureNotNull(this.price) { InvalidOrderRequest("시장가 매수는 총액이 필요합니다") }
            OrderType.MarketBuy(Amount(this.price.toBigDecimal()))
        }
        "market" -> {
            ensure(side == OrderSide.ASK) { InvalidOrderRequest("시장가 매도는 ASK만 가능합니다") }
            ensureNotNull(this.volume) { InvalidOrderRequest("시장가 매도는 수량이 필요합니다") }
            OrderType.MarketSell(Volume(this.volume))
        }
        "best" -> {
            ensureNotNull(this.volume) { InvalidOrderRequest("최유리 주문은 수량이 필요합니다") }
            OrderType.Best(Volume(this.volume))
        }
        else -> raise(InvalidOrderRequest("지원하지 않는 주문 타입: ${this.orderType}"))
    }

    return ValidatedOrderRequest(pair, side, orderType)
}

/**
 * 최소 주문 금액 검증
 */
context(_: Raise<OrderError>)
fun ValidatedOrderRequest.validateMinimumOrderAmount() {
    val minimumAmount = when (pair.market) {
        Market.KRW -> Amount(BigDecimal("5000"))
        Market.BTC -> Amount(BigDecimal("0.00005"))  // 2024년 8월 변경
        Market.USDT -> Amount(BigDecimal("1"))
    }

    val orderAmount = when (val type = orderType) {
        is OrderType.Limit -> Amount(type.volume.value * type.price.value)
        is OrderType.MarketBuy -> type.totalPrice
        is OrderType.MarketSell -> Amount(BigDecimal.ZERO) // 시장가 매도는 사전 검증 불가
        is OrderType.Best -> Amount(BigDecimal.ZERO) // 최유리 주문은 사전 검증 불가
    }

    if (orderAmount.value > BigDecimal.ZERO) {
        ensure(orderAmount >= minimumAmount) {
            MinimumOrderAmountNotMet(minimumAmount, orderAmount)
        }
    }
}

/**
 * 호가단위(틱 사이즈) 검증
 *
 * 지정가 주문의 경우 마켓별 호가단위에 맞는지 검증
 */
context(_: Raise<OrderError>)
fun ValidatedOrderRequest.validateTickSize() {
    val price = when (val type = orderType) {
        is OrderType.Limit -> type.price
        else -> return  // 지정가 주문만 호가단위 검증 필요
    }

    val tickSize = TickSize.forMarket(pair.market, price.value)
    val remainder = price.value.remainder(tickSize.value)

    ensure(remainder.compareTo(BigDecimal.ZERO) == 0) {
        InvalidPriceUnit(price, tickSize.value)
    }
}

/**
 * 주문 이벤트
 */
sealed interface OrderEvent {
    val orderId: OrderId
    val occurredAt: Instant

    data class OrderCreated(
        override val orderId: OrderId,
        val pair: Pair,
        val side: OrderSide,
        val orderType: OrderType,
        override val occurredAt: Instant = Instant.now(),
    ) : OrderEvent

    data class OrderExecuted(
        override val orderId: OrderId,
        val executedVolume: Volume,
        val executedPrice: Price,
        val fee: Amount,
        override val occurredAt: Instant = Instant.now(),
    ) : OrderEvent

    data class OrderCancelled(
        override val orderId: OrderId,
        override val occurredAt: Instant = Instant.now(),
    ) : OrderEvent
}

/**
 * 주문 도메인 오류
 */
sealed interface OrderError {
    data class InvalidOrderRequest(val reason: String) : OrderError
    data class InsufficientBalance(val required: Amount, val available: Amount) : OrderError
    data class MinimumOrderAmountNotMet(val minimum: Amount, val actual: Amount) : OrderError
    data class InvalidPriceUnit(val price: Price, val expectedUnit: BigDecimal) : OrderError
    data class OrderNotFound(val id: OrderId) : OrderError
    data class OrderNotCancellable(val id: OrderId, val state: OrderState) : OrderError
    data class ExchangeError(val code: String, val message: String) : OrderError
}
```

### 3.4 계정 도메인 (Account Domain)

```kotlin
package com.cryptoquant.domain.account

/**
 * 통화 코드
 */
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
 * 자산 잔고
 */
data class Balance(
    val currency: Currency,
    val balance: BigDecimal,           // 총 잔고
    val locked: BigDecimal,            // 주문 중 잠긴 금액
    val avgBuyPrice: BigDecimal,       // 평균 매수가
    val avgBuyPriceModified: Boolean,  // 매수 평균가 수정 여부
) {
    /**
     * 주문 가능 금액
     */
    val available: BigDecimal get() = balance - locked

    /**
     * 총 평가금액 (현재가 기준)
     */
    fun totalValue(currentPrice: Price): Amount {
        return Amount(balance * currentPrice.value)
    }

    /**
     * 평가 손익 (현재가 기준)
     */
    fun profitLoss(currentPrice: Price): Amount {
        val currentValue = balance * currentPrice.value
        val buyValue = balance * avgBuyPrice
        return Amount(currentValue - buyValue)
    }

    /**
     * 수익률 (%)
     */
    fun profitLossRate(currentPrice: Price): BigDecimal {
        if (avgBuyPrice == BigDecimal.ZERO) return BigDecimal.ZERO
        return ((currentPrice.value - avgBuyPrice) / avgBuyPrice) * BigDecimal(100)
    }
}

/**
 * 계정 (잔고 집합)
 */
data class Account(
    val balances: List<Balance>,
) {
    /**
     * 특정 통화 잔고 조회
     */
    fun getBalance(currency: Currency): Balance? {
        return balances.find { it.currency == currency }
    }

    /**
     * 주문 가능 금액 조회
     */
    fun getAvailableBalance(currency: Currency): BigDecimal {
        return getBalance(currency)?.available ?: BigDecimal.ZERO
    }

    /**
     * 특정 통화 보유 여부
     */
    fun hasBalance(currency: Currency): Boolean {
        return getBalance(currency)?.let { it.balance > BigDecimal.ZERO } ?: false
    }
}

/**
 * 주문 가능 정보
 */
data class OrderChance(
    val pair: Pair,
    val bidFee: BigDecimal,            // 매수 수수료율
    val askFee: BigDecimal,            // 매도 수수료율
    val bidAccount: Balance,           // 매수 시 사용되는 화폐 계좌
    val askAccount: Balance,           // 매도 시 사용되는 화폐 계좌
    val minOrderAmount: Amount,        // 최소 주문 금액
)
```

### 3.5 전략 도메인 (Strategy Domain)

```kotlin
package com.cryptoquant.domain.strategy

/**
 * 매매 신호
 */
enum class Signal {
    BUY,   // 매수
    SELL,  // 매도
    HOLD   // 관망
}

/**
 * RSI 지표
 *
 * 계산 방법:
 * 1. 기간 동안 상승폭 평균 (AU) = 상승일 상승폭 합계 / 기간
 * 2. 기간 동안 하락폭 평균 (AD) = 하락일 하락폭 합계 / 기간
 * 3. RS = AU / AD
 * 4. RSI = 100 - (100 / (1 + RS))
 */
@JvmInline
value class RSI private constructor(val value: BigDecimal) {
    val isOverbought: Boolean get() = value >= BigDecimal(70)
    val isOversold: Boolean get() = value <= BigDecimal(30)

    companion object {
        context(_: Raise<StrategyError>)
        operator fun invoke(value: BigDecimal): RSI {
            ensure(value >= BigDecimal.ZERO && value <= BigDecimal(100)) {
                InvalidIndicatorValue("RSI는 0-100 사이여야 합니다: $value")
            }
            return RSI(value)
        }

        /**
         * 캔들 목록으로부터 RSI 계산
         * @param candles 캔들 목록 (최신순)
         * @param period RSI 계산 기간 (기본 14)
         */
        context(_: Raise<StrategyError>)
        fun calculate(candles: List<Candle>, period: Int = 14): RSI {
            ensure(candles.size >= period + 1) {
                InsufficientData("RSI 계산에 최소 ${period + 1}개의 캔들이 필요합니다")
            }

            // 가격 변화량 계산 (최신 -> 과거 순서)
            val changes = candles.zipWithNext { current, previous ->
                current.closingPrice.value - previous.closingPrice.value
            }.take(period)

            val gains = changes.filter { it > BigDecimal.ZERO }
            val losses = changes.filter { it < BigDecimal.ZERO }.map { it.abs() }

            val avgGain = if (gains.isNotEmpty()) {
                gains.reduce { acc, g -> acc + g } / BigDecimal(period)
            } else BigDecimal.ZERO

            val avgLoss = if (losses.isNotEmpty()) {
                losses.reduce { acc, l -> acc + l } / BigDecimal(period)
            } else BigDecimal.ZERO

            val rsi = if (avgLoss == BigDecimal.ZERO) {
                BigDecimal(100)
            } else {
                val rs = avgGain / avgLoss
                BigDecimal(100) - (BigDecimal(100) / (BigDecimal.ONE + rs))
            }

            return RSI(rsi.setScale(2, RoundingMode.HALF_UP))
        }
    }
}

/**
 * 이동평균
 */
@JvmInline
value class MovingAverage private constructor(val value: BigDecimal) {
    companion object {
        context(_: Raise<StrategyError>)
        fun calculate(candles: List<Candle>, period: Int): MovingAverage {
            ensure(candles.size >= period) {
                InsufficientData("이동평균 계산에 최소 ${period}개의 캔들이 필요합니다")
            }

            val sum = candles.take(period)
                .map { it.closingPrice.value }
                .reduce { acc, price -> acc + price }

            return MovingAverage(sum / BigDecimal(period))
        }
    }
}

/**
 * RSI 전략 설정
 */
data class RSIStrategyConfig(
    val period: Int = 14,
    val oversoldThreshold: BigDecimal = BigDecimal(30),
    val overboughtThreshold: BigDecimal = BigDecimal(70),
)

/**
 * RSI 기반 매매 전략
 */
class RSIStrategy(private val config: RSIStrategyConfig) {

    context(_: Raise<StrategyError>)
    fun evaluate(candles: List<Candle>): Signal {
        val rsi = RSI.calculate(candles, config.period)

        return when {
            rsi.value <= config.oversoldThreshold -> Signal.BUY
            rsi.value >= config.overboughtThreshold -> Signal.SELL
            else -> Signal.HOLD
        }
    }
}

/**
 * 전략 실행 결과
 */
data class StrategyResult(
    val pair: Pair,
    val signal: Signal,
    val indicators: Map<String, BigDecimal>,
    val timestamp: Timestamp,
    val reason: String,
)

/**
 * 전략 오류
 */
sealed interface StrategyError {
    data class InsufficientData(val message: String) : StrategyError
    data class InvalidIndicatorValue(val message: String) : StrategyError
    data class CalculationError(val message: String) : StrategyError
}
```

### 3.6 공통 도메인 오류

```kotlin
package com.cryptoquant.domain.common

/**
 * 도메인 오류
 */
sealed interface DomainError {
    val message: String
}

// 공통 값 객체 오류
data class InvalidMarket(override val message: String) : DomainError
data class InvalidPair(override val message: String) : DomainError
data class InvalidPrice(override val message: String) : DomainError
data class InvalidVolume(override val message: String) : DomainError
data class InvalidCurrency(override val message: String) : DomainError
data class InvalidOrderId(override val message: String) : DomainError
data class InvalidCandleUnit(override val message: String) : DomainError
data class InvalidTickSize(override val message: String) : DomainError
```

---

## 4. 인터페이스 정의

### 4.1 외부 서비스 게이트웨이

```kotlin
/**
 * 업비트 REST API 클라이언트
 */
interface UpbitExchangeClient {
    context(_: Raise<ExchangeError>)
    suspend fun placeOrder(request: ValidatedOrderRequest): Order

    context(_: Raise<ExchangeError>)
    suspend fun cancelOrder(orderId: OrderId): Order

    context(_: Raise<ExchangeError>)
    suspend fun getOrder(orderId: OrderId): Order

    context(_: Raise<ExchangeError>)
    suspend fun getOpenOrders(pair: Pair?): List<Order>

    context(_: Raise<ExchangeError>)
    suspend fun getBalances(): List<Balance>

    context(_: Raise<ExchangeError>)
    suspend fun getOrderChance(pair: Pair): OrderChance
}

/**
 * 업비트 시세 API 클라이언트
 */
interface UpbitQuotationClient {
    context(_: Raise<QuotationError>)
    suspend fun getCandles(pair: Pair, unit: CandleUnit, count: Int): List<Candle>

    context(_: Raise<QuotationError>)
    suspend fun getTicker(pairs: List<Pair>): List<Ticker>

    context(_: Raise<QuotationError>)
    suspend fun getOrderbook(pairs: List<Pair>): List<Orderbook>

    context(_: Raise<QuotationError>)
    suspend fun getTrades(pair: Pair, count: Int): List<Trade>
}

/**
 * 업비트 WebSocket 클라이언트
 */
interface UpbitWebSocketClient {
    fun subscribeTicker(pairs: List<Pair>): Flow<Ticker>
    fun subscribeOrderbook(pairs: List<Pair>): Flow<Orderbook>
    fun subscribeTrade(pairs: List<Pair>): Flow<Trade>
    fun subscribeMyOrder(): Flow<OrderEvent>
}
```

### 4.2 저장소 인터페이스

```kotlin
/**
 * 주문 저장소
 */
interface OrderRepository {
    suspend fun save(order: Order): Unit
    suspend fun findById(orderId: OrderId): Order?
    suspend fun findOpenOrders(pair: Pair?): List<Order>
}

/**
 * 전략 실행 이력 저장소
 */
interface StrategyExecutionRepository {
    suspend fun save(result: StrategyResult): Unit
    suspend fun findByPair(pair: Pair, limit: Int): List<StrategyResult>
}
```

---

## 5. 기술적 결정사항

### 5.1 기술 선택

| 항목 | 선택 | 대안 | 선택 이유 |
|------|------|------|-----------|
| FP 라이브러리 | Arrow | kotlin-result, Λrrow 2.0 | Raise DSL의 명시적 오류 처리, 컨텍스트 수신자 지원 |
| HTTP 클라이언트 | WebClient | Ktor Client, OkHttp | Spring WebFlux와 자연스러운 통합, 리액티브 스트림 지원 |
| WebSocket | Spring WebSocket | Ktor WebSocket | Spring 생태계 일관성 유지 |
| JSON 직렬화 | kotlinx.serialization | Jackson, Gson | Kotlin 친화적, 컴파일 타임 안전성 |
| DB 접근 | R2DBC | JDBC, jOOQ | 논블로킹 DB 접근, WebFlux와 일관성 |

### 5.2 설계 결정

#### 결정 1: Value Class 사용

**컨텍스트**: 원시 타입(String, BigDecimal)을 직접 사용하면 타입 안전성이 낮아짐

**결정**: 모든 도메인 개념에 Value Class 적용

**이유**:
- 컴파일 타임에 타입 오류 검출
- 런타임 오버헤드 없음 (인라인)
- 스마트 생성자로 불변식 강제

**결과**:
- 장점: 타입 안전성, 자기 문서화 코드
- 단점: 초기 작성 코드량 증가
- 트레이드오프: 개발 초기 비용 vs 장기 유지보수 비용

#### 결정 2: Raise 컨텍스트 사용

**컨텍스트**: 오류 처리 방식 선택 필요 (예외, Either, Raise)

**결정**: Arrow의 Raise 컨텍스트 사용

**이유**:
- 함수 시그니처에 오류 가능성 명시
- 컴파일러가 오류 처리 강제
- Either보다 직관적인 코드 작성

**결과**:
- 장점: 명시적 오류 처리, 누락 방지
- 단점: Arrow 학습 곡선
- 트레이드오프: 프레임워크 의존성 vs 타입 안전성

#### 결정 3: 상태 전이 타입 미분리

**컨텍스트**: 주문 상태별 별도 타입 (UnvalidatedOrder, ValidatedOrder 등) 분리 여부

**결정**: 현재는 단일 Order 타입 사용, 상태는 enum으로 관리

**이유**:
- 업비트 API가 반환하는 구조와 일치
- 초기 구현 복잡도 감소
- 필요시 점진적으로 분리 가능

**결과**:
- 장점: 구현 단순화, API 매핑 용이
- 단점: 잘못된 상태 전이 컴파일 타임 검출 불가
- 트레이드오프: 초기 개발 속도 vs 타입 안전성

#### 결정 4: 호가단위(틱 사이즈) 검증 분리

**컨텍스트**: 마켓별로 호가단위 정책이 다름
- KRW 마켓: 가격대별로 다른 호가단위 (1,000원 ~ 0.00000001원)
- BTC 마켓: 단일 호가단위 (0.00000001 BTC)
- USDT 마켓: 가격대별로 다른 호가단위

**결정**: Price 값 객체에서 소수점 제약을 제거하고, TickSize 값 객체로 마켓별 호가단위 검증 분리

**이유**:
- 마켓별로 다른 정책을 단일 규칙으로 강제할 수 없음
- 호가단위 검증은 주문 시점에 마켓과 가격 정보가 필요
- 시세 데이터 수신 시에는 호가단위 검증이 불필요

**결과**:
- 장점: 마켓별 정책을 정확하게 반영, 유연한 검증
- 단점: 주문 시 별도 검증 로직 필요
- 트레이드오프: Price 값 객체 단순화 vs 검증 책임 분산

---

## 6. 체크리스트

### 테크스펙 완성 확인

- [x] 도메인 모델이 코드 수준으로 정의되었는가?
- [x] 값 객체와 스마트 생성자가 정의되었는가?
- [x] 도메인 오류 타입이 정의되었는가?
- [x] 도메인 이벤트가 정의되었는가?
- [x] Repository/Gateway 인터페이스가 정의되었는가?
- [x] 기술적 결정사항과 트레이드오프가 문서화되었는가?
- [x] 마켓별 호가단위(틱 사이즈) 정책이 반영되었는가?
