# 업비트 도메인 모델 구현 계획

> 관련 테크스펙: [TECH-SPEC.md](./TECH-SPEC.md)

## 구현 상태

| 단계 | 상태 | 완료일 |
|------|------|--------|
| 1단계: 공통 값 객체 | [x] 완료 | 2026-01-01 |
| 2단계: 공통 도메인 오류 | [x] 완료 | 2026-01-01 |
| 3단계: 시세 도메인 | [x] 완료 | 2026-01-01 |
| 4단계: 주문 도메인 | [x] 완료 | 2026-01-01 |
| 5단계: 계정 도메인 | [x] 완료 | 2026-01-01 |
| 6단계: 게이트웨이 인터페이스 | [x] 완료 | 2026-01-01 |
| 7단계: 저장소 인터페이스 | [x] 완료 | 2026-01-01 |

---

## 1단계: 공통 값 객체

### 목표
모든 도메인에서 공통으로 사용하는 값 객체를 구현합니다.

### 체크리스트
- [x] 작업 1: DecimalConfig 설정 객체
  - 파일: `subproject/domain/src/main/kotlin/com/cryptoquant/domain/common/DecimalConfig.kt`
  - 내용: PERCENT_SCALE, PRICE_SCALE, ROUNDING_MODE 상수 정의
- [x] 작업 2: Market enum
  - 파일: `subproject/domain/src/main/kotlin/com/cryptoquant/domain/common/Market.kt`
  - 내용: KRW, BTC, USDT 마켓 + Raise 컨텍스트 from() 팩토리
- [x] 작업 3: TradingPair 값 객체
  - 파일: `subproject/domain/src/main/kotlin/com/cryptoquant/domain/common/TradingPair.kt`
  - 내용: market + ticker 조합, 스마트 생성자, TICKER_REGEX 검증
- [x] 작업 4: Price 값 객체
  - 파일: `subproject/domain/src/main/kotlin/com/cryptoquant/domain/common/Price.kt`
  - 내용: @JvmInline value class, 양수 검증, adjustToTickSize(), validateTickSize()
- [x] 작업 5: Volume 값 객체
  - 파일: `subproject/domain/src/main/kotlin/com/cryptoquant/domain/common/Volume.kt`
  - 내용: @JvmInline value class, 0 이상 검증, 소수점 8자리 제한, 연산자 오버로딩
- [x] 작업 6: Amount 값 객체
  - 파일: `subproject/domain/src/main/kotlin/com/cryptoquant/domain/common/Amount.kt`
  - 내용: @JvmInline value class, 0 이상 검증, 연산자 오버로딩
- [x] 작업 7: TickSize 값 객체
  - 파일: `subproject/domain/src/main/kotlin/com/cryptoquant/domain/common/TickSize.kt`
  - 내용: KRW/BTC/USDT 마켓별 호가 단위 계산 로직
- [x] 작업 8: FeeRate 값 객체
  - 파일: `subproject/domain/src/main/kotlin/com/cryptoquant/domain/common/FeeRate.kt`
  - 내용: 0~1 범위 검증, toPercent(), DEFAULT 상수
- [x] 작업 9: PriceChange 값 객체
  - 파일: `subproject/domain/src/main/kotlin/com/cryptoquant/domain/common/PriceChange.kt`
  - 내용: 음수 허용, abs(), isPositive/isNegative/isZero 프로퍼티
- [x] 작업 10: ChangeRate 값 객체
  - 파일: `subproject/domain/src/main/kotlin/com/cryptoquant/domain/common/ChangeRate.kt`
  - 내용: -1 이상 검증, toPercent() 변환
- [x] 작업 11: TradeSequentialId 값 객체
  - 파일: `subproject/domain/src/main/kotlin/com/cryptoquant/domain/common/TradeSequentialId.kt`
  - 내용: @JvmInline value class, 양수 검증
- [x] 작업 12: AvgBuyPrice 값 객체
  - 파일: `subproject/domain/src/main/kotlin/com/cryptoquant/domain/common/AvgBuyPrice.kt`
  - 내용: 0 허용 (Price와 구분), isZero 프로퍼티
- [x] 테스트 작성
  - 파일: `subproject/domain/src/test/kotlin/com/cryptoquant/domain/common/`
  - 내용: 각 값 객체별 유효성 검증 테스트

### 예상 산출물
- `subproject/domain/src/main/kotlin/com/cryptoquant/domain/common/*.kt` (12개 파일)
- `subproject/domain/src/test/kotlin/com/cryptoquant/domain/common/*Test.kt`

---

## 2단계: 공통 도메인 오류

### 목표
도메인 계층에서 사용하는 오류 타입을 정의합니다.

### 선행 조건
- 없음 (1단계와 병렬 진행 가능)

### 체크리스트
- [x] 작업 1: DomainError sealed interface
  - 파일: `subproject/domain/src/main/kotlin/com/cryptoquant/domain/common/DomainError.kt`
  - 내용: 공통 message 프로퍼티, Invalid* 데이터 클래스들
- [x] 테스트 작성: 값 객체 테스트에서 DomainError 검증 포함

### 예상 산출물
- `subproject/domain/src/main/kotlin/com/cryptoquant/domain/common/DomainError.kt`
- `subproject/domain/src/test/kotlin/com/cryptoquant/domain/common/DomainErrorTest.kt`

---

## 3단계: 시세 도메인

### 목표
캔들, 현재가, 호가창, 체결 내역 등 시세 관련 도메인 모델을 구현합니다.

### 선행 조건
- 1단계 완료 (TradingPair, Price, Volume, Amount 필요)
- 2단계 완료 (DomainError 필요)

### 체크리스트
- [x] 작업 1: CandleUnit sealed interface
  - 파일: `subproject/domain/src/main/kotlin/com/cryptoquant/domain/quotation/CandleUnit.kt`
  - 내용: Seconds(WebSocket 전용), Minutes, Day, Week, Month 구현
- [x] 작업 2: Candle 엔티티
  - 파일: `subproject/domain/src/main/kotlin/com/cryptoquant/domain/quotation/Candle.kt`
  - 내용: OHLCV 데이터, 고가/저가/시가/종가 불변식 검증
- [x] 작업 3: Change enum
  - 파일: `subproject/domain/src/main/kotlin/com/cryptoquant/domain/quotation/Change.kt`
  - 내용: RISE, EVEN, FALL
- [x] 작업 4: Ticker 엔티티
  - 파일: `subproject/domain/src/main/kotlin/com/cryptoquant/domain/quotation/Ticker.kt`
  - 내용: 현재가, 변동률, 거래량 등 시세 정보
- [x] 작업 5: OrderbookUnit 값 객체
  - 파일: `subproject/domain/src/main/kotlin/com/cryptoquant/domain/quotation/OrderbookUnit.kt`
  - 내용: 개별 호가 단위 (askPrice, bidPrice, askSize, bidSize)
- [x] 작업 6: Orderbook 엔티티
  - 파일: `subproject/domain/src/main/kotlin/com/cryptoquant/domain/quotation/Orderbook.kt`
  - 내용: 호가창, bestBidPrice, bestAskPrice, spread() 계산
- [x] 작업 7: Trade 엔티티
  - 파일: `subproject/domain/src/main/kotlin/com/cryptoquant/domain/quotation/Trade.kt`
  - 내용: 체결 내역, sequentialId 포함, AskBid enum 추가
- [x] 테스트 작성
  - 파일: `subproject/domain/src/test/kotlin/com/cryptoquant/domain/quotation/`
  - 내용: Candle 불변식 검증, Orderbook 계산 로직 테스트

### 예상 산출물
- `subproject/domain/src/main/kotlin/com/cryptoquant/domain/quotation/*.kt` (7개 파일)
- `subproject/domain/src/test/kotlin/com/cryptoquant/domain/quotation/*Test.kt`

---

## 4단계: 주문 도메인

### 목표
주문 생성, 검증, 체결, 취소 등 주문 관련 도메인 모델을 구현합니다.

### 선행 조건
- 1단계 완료
- 2단계 완료
- 3단계 완료 (Change, OrderSide 참조)

### 체크리스트
- [x] 작업 1: OrderId 값 객체
  - 파일: `subproject/domain/src/main/kotlin/com/cryptoquant/domain/order/OrderId.kt`
  - 내용: @JvmInline value class, 비어있지 않음 검증
- [x] 작업 2: OrderSide enum
  - 파일: `subproject/domain/src/main/kotlin/com/cryptoquant/domain/order/OrderSide.kt`
  - 내용: BID(매수), ASK(매도)
- [x] 작업 3: OrderType sealed interface
  - 파일: `subproject/domain/src/main/kotlin/com/cryptoquant/domain/order/OrderType.kt`
  - 내용: Limit, MarketBuy, MarketSell, Best + 스마트 생성자
- [x] 작업 4: OrderState enum
  - 파일: `subproject/domain/src/main/kotlin/com/cryptoquant/domain/order/OrderState.kt`
  - 내용: WAIT, WATCH, DONE, CANCEL
- [x] 작업 5: UnvalidatedOrderRequest / ValidatedOrderRequest
  - 파일: `subproject/domain/src/main/kotlin/com/cryptoquant/domain/order/OrderRequest.kt`
  - 내용: 검증 전/후 주문 요청 타입 분리
- [x] 작업 6: Order 엔티티
  - 파일: `subproject/domain/src/main/kotlin/com/cryptoquant/domain/order/Order.kt`
  - 내용: 주문 엔티티 + 불변식 검증 (side/orderType 정합성, 수량 불변식)
- [x] 작업 7: Order 확장 함수
  - 파일: `subproject/domain/src/main/kotlin/com/cryptoquant/domain/order/OrderExtensions.kt`
  - 내용: limitVolume(), limitPrice(), marketBuyTotalPrice(), sellVolume()
- [x] 작업 8: OrderRequest 검증 함수
  - 파일: `subproject/domain/src/main/kotlin/com/cryptoquant/domain/order/OrderValidation.kt`
  - 내용: validate(), validateMinimumOrderAmount(), validateTickSize()
- [x] 작업 9: TradeId 값 객체
  - 파일: `subproject/domain/src/main/kotlin/com/cryptoquant/domain/order/TradeId.kt`
  - 내용: 체결 ID, 멱등성 처리용
- [x] 작업 10: OrderEvent sealed interface
  - 파일: `subproject/domain/src/main/kotlin/com/cryptoquant/domain/order/OrderEvent.kt`
  - 내용: OrderCreated, OrderExecuted, OrderCancelled + now() 팩토리
- [x] 작업 11: OrderError sealed interface
  - 파일: `subproject/domain/src/main/kotlin/com/cryptoquant/domain/order/OrderError.kt`
  - 내용: InvalidOrderRequest, InsufficientBalance, MinimumOrderAmountNotMet 등
- [x] 테스트 작성
  - 파일: `subproject/domain/src/test/kotlin/com/cryptoquant/domain/order/`
  - 내용: Order 불변식, 검증 로직, 확장 함수 테스트

### 예상 산출물
- `subproject/domain/src/main/kotlin/com/cryptoquant/domain/order/*.kt` (11개 파일)
- `subproject/domain/src/test/kotlin/com/cryptoquant/domain/order/*Test.kt`

---

## 5단계: 계정 도메인

### 목표
잔고, 계정, 주문 가능 정보 등 계정 관련 도메인 모델을 구현합니다.

### 선행 조건
- 1단계 완료 (Volume, Amount, FeeRate, AvgBuyPrice 필요)
- 2단계 완료

### 체크리스트
- [x] 작업 1: Currency 값 객체
  - 파일: `subproject/domain/src/main/kotlin/com/cryptoquant/domain/account/Currency.kt`
  - 내용: @JvmInline value class, 대문자 변환, KRW/BTC/USDT 상수
- [x] 작업 2: Balance 엔티티
  - 파일: `subproject/domain/src/main/kotlin/com/cryptoquant/domain/account/Balance.kt`
  - 내용: balance/locked/available, 불변식 검증, totalValue(), profitLoss(), profitLossRate()
- [x] 작업 3: Account 엔티티
  - 파일: `subproject/domain/src/main/kotlin/com/cryptoquant/domain/account/Account.kt`
  - 내용: 잔고 목록, getBalance(), getAvailableBalance(), hasBalance()
- [x] 작업 4: OrderChance 엔티티
  - 파일: `subproject/domain/src/main/kotlin/com/cryptoquant/domain/account/OrderChance.kt`
  - 내용: 주문 가능 정보, 수수료율, 최소 주문금액
- [x] 테스트 작성
  - 파일: `subproject/domain/src/test/kotlin/com/cryptoquant/domain/account/`
  - 내용: Balance 불변식, 손익 계산 테스트 (OrderValidationTest에서 함께 검증)

### 예상 산출물
- `subproject/domain/src/main/kotlin/com/cryptoquant/domain/account/*.kt` (4개 파일)
- `subproject/domain/src/test/kotlin/com/cryptoquant/domain/account/*Test.kt`

---

## 6단계: 게이트웨이 인터페이스

### 목표
외부 서비스(업비트 API)와의 인터페이스를 도메인 포트로 정의합니다.

### 선행 조건
- 1~5단계 완료

### 체크리스트
- [x] 작업 1: GatewayError sealed interface
  - 파일: `subproject/domain/src/main/kotlin/com/cryptoquant/domain/gateway/GatewayError.kt`
  - 내용: NetworkError, AuthenticationError, RateLimitError, ApiError, InvalidResponse
- [x] 작업 2: PageRequest / PageResponse
  - 파일: `subproject/domain/src/main/kotlin/com/cryptoquant/domain/gateway/Pagination.kt`
  - 내용: 페이지네이션 요청/응답 모델
- [x] 작업 3: ExchangeGateway 인터페이스
  - 파일: `subproject/domain/src/main/kotlin/com/cryptoquant/domain/gateway/ExchangeGateway.kt`
  - 내용: placeOrder, cancelOrder, getOrder, getOpenOrders, getBalances, getOrderChance
- [x] 작업 4: QuotationGateway 인터페이스
  - 파일: `subproject/domain/src/main/kotlin/com/cryptoquant/domain/gateway/QuotationGateway.kt`
  - 내용: getCandles, getTicker, getOrderbook, getTrades
- [x] 작업 5: RealtimeStream 인터페이스
  - 파일: `subproject/domain/src/main/kotlin/com/cryptoquant/domain/gateway/RealtimeStream.kt`
  - 내용: WebSocket 구독 Flow 반환 (subscribeTicker, subscribeOrderbook, subscribeTrade, subscribeMyOrder)

### 예상 산출물
- `subproject/domain/src/main/kotlin/com/cryptoquant/domain/gateway/*.kt` (5개 파일)

---

## 7단계: 저장소 인터페이스

### 목표
도메인 엔티티의 영속화를 위한 저장소 인터페이스를 정의합니다.

### 선행 조건
- 4단계 완료 (Order 엔티티 필요)
- 6단계 완료 (PageRequest/PageResponse 필요)

### 체크리스트
- [x] 작업 1: OrderRepository 인터페이스
  - 파일: `subproject/domain/src/main/kotlin/com/cryptoquant/domain/repository/OrderRepository.kt`
  - 내용: save, findById, findOpenOrders

### 예상 산출물
- `subproject/domain/src/main/kotlin/com/cryptoquant/domain/repository/OrderRepository.kt`

---

## 코드 품질 검사

- [x] `./gradlew ktlintFormat` 실행하여 코드 포맷팅
- [x] `./gradlew ktlintCheck` 통과 확인
- [x] `./gradlew detekt` 통과 확인
- [x] `./gradlew check` 전체 빌드 통과 확인

---

## 완료 기준

- [x] 모든 단계의 체크리스트 완료
- [x] 모든 테스트 통과
- [x] 코드 품질 검사 통과
- [x] MockDomainValue.kt 삭제 (해당 파일 없음)
