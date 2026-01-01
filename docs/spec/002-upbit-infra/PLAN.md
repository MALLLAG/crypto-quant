# 업비트 인프라 레이어 연동 구현 계획

> 관련 테크스펙: [TECH-SPEC.md](./TECH-SPEC.md)

## 구현 상태

| 단계 | 상태 | 완료일 |
|------|------|--------|
| 1단계: 기반 구조 설정 | [x] 완료 | 2026-01-01 |
| 2단계: REST 클라이언트 구현 | [x] 완료 | 2026-01-01 |
| 3단계: Gateway 구현 | [x] 완료 | 2026-01-01 |
| 4단계: WebSocket 구현 | [x] 완료 | 2026-01-01 |
| 5단계: Repository 구현 | [x] 완료 | 2026-01-01 |
| 6단계: 통합 테스트 | [x] 완료 | 2026-01-01 |

---

## 1단계: 기반 구조 설정

### 목표
Gradle 의존성 추가, 설정 프로퍼티, 공통 유틸리티 구현

### 체크리스트

- [x] **Gradle 의존성 추가**
  - 파일: `subproject/infrastructure/build.gradle.kts`
  - 내용:
    - `spring-boot-starter-webflux`
    - `spring-boot-starter-data-r2dbc`
    - `r2dbc-postgresql`
    - `kotlinx-serialization-json`
    - `com.auth0:java-jwt:4.4.0`
    - `com.bucket4j:bucket4j-core:8.7.0`
    - `com.squareup.okhttp3:okhttp:4.12.0`
    - 테스트: `testcontainers:postgresql`, `wiremock-standalone`

- [x] **설정 프로퍼티 정의**
  - 파일: `infrastructure/src/main/kotlin/com/cryptoquant/infrastructure/upbit/config/UpbitProperties.kt`
  - 내용: `UpbitProperties` (ApiProperties, WebSocketProperties)

- [x] **application.yml 설정 추가**
  - 파일: `infrastructure/src/main/resources/application.yml`
  - 내용: `upbit.api`, `upbit.websocket`, `spring.r2dbc` 설정

- [x] **페이징 타입 정의**
  - 파일: `infrastructure/src/main/kotlin/com/cryptoquant/infrastructure/common/Pagination.kt`
  - 내용: `OffsetPageRequest/Response` (도메인에 cursor 기반 PageRequest/PageResponse 이미 존재)

- [x] **BigDecimalSerializer 구현**
  - 파일: `infrastructure/src/main/kotlin/com/cryptoquant/infrastructure/upbit/serializer/BigDecimalSerializer.kt`
  - 내용: JSON 숫자/문자열 모두 지원하는 BigDecimal 직렬화

### 예상 산출물
- `subproject/infrastructure/build.gradle.kts` (수정)
- `infrastructure/src/main/kotlin/com/cryptoquant/infrastructure/upbit/config/UpbitProperties.kt`
- `infrastructure/src/main/kotlin/com/cryptoquant/infrastructure/common/Pagination.kt`
- `infrastructure/src/main/kotlin/com/cryptoquant/infrastructure/upbit/serializer/BigDecimalSerializer.kt`
- `infrastructure/src/main/resources/application.yml` (수정)

---

## 2단계: REST 클라이언트 구현

### 목표
JWT 인증, Rate Limiter, HTTP 클라이언트 구현

### 선행 조건
- 1단계 완료

### 체크리스트

- [x] **JWT 인증 인터셉터 구현**
  - 파일: `infrastructure/src/main/kotlin/com/cryptoquant/infrastructure/upbit/client/UpbitAuthInterceptor.kt`
  - 내용:
    - `generateToken(queryParams, bodyParams)` 메서드
    - SHA512 해싱, HMAC512 서명
    - 쿼리 문자열 생성 (`toQueryString()`)
  - 테스트: JWT 토큰 형식, 해시 검증

- [x] **Rate Limiter 구현**
  - 파일: `infrastructure/src/main/kotlin/com/cryptoquant/infrastructure/upbit/client/UpbitRateLimiter.kt`
  - 내용:
    - `ApiGroup` enum (QUOTATION, EXCHANGE_DEFAULT, ORDER)
    - Bucket4j 기반 토큰 버킷
    - `withRateLimit()` suspend 함수
  - 테스트: Rate Limit 초과 시 GatewayError 반환

- [x] **REST 클라이언트 구현**
  - 파일: `infrastructure/src/main/kotlin/com/cryptoquant/infrastructure/upbit/client/UpbitRestClient.kt`
  - 내용:
    - `getPublic()`, `getPrivate()`, `postPrivate()`, `deletePrivate()`
    - `executeRequest()` - HTTP 에러 → GatewayError 변환
  - 테스트: WireMock으로 API 응답/에러 모킹 (6단계에서 추가 예정)

- [x] **에러 응답 DTO**
  - 파일: `infrastructure/src/main/kotlin/com/cryptoquant/infrastructure/upbit/error/UpbitApiError.kt`
  - 내용: `UpbitApiError` sealed interface, `UpbitErrorResponse` DTO

- [x] **WebClient 설정**
  - 파일: `infrastructure/src/main/kotlin/com/cryptoquant/infrastructure/upbit/config/UpbitClientConfig.kt`
  - 내용:
    - `upbitWebClient()` Bean
    - kotlinx.serialization 코덱 설정
    - `upbitAuthInterceptor()`, `upbitRateLimiter()` Bean

- [x] **단위 테스트 작성**
  - 파일: `infrastructure/src/test/kotlin/com/cryptoquant/infrastructure/upbit/client/UpbitAuthInterceptorTest.kt`
  - 파일: `infrastructure/src/test/kotlin/com/cryptoquant/infrastructure/upbit/client/UpbitRateLimiterTest.kt`

### 예상 산출물
- `infrastructure/src/main/kotlin/com/cryptoquant/infrastructure/upbit/client/UpbitAuthInterceptor.kt`
- `infrastructure/src/main/kotlin/com/cryptoquant/infrastructure/upbit/client/UpbitRateLimiter.kt`
- `infrastructure/src/main/kotlin/com/cryptoquant/infrastructure/upbit/client/UpbitRestClient.kt`
- `infrastructure/src/main/kotlin/com/cryptoquant/infrastructure/upbit/error/UpbitApiError.kt`
- `infrastructure/src/main/kotlin/com/cryptoquant/infrastructure/upbit/config/UpbitClientConfig.kt`
- `infrastructure/src/test/kotlin/com/cryptoquant/infrastructure/upbit/client/UpbitAuthInterceptorTest.kt`
- `infrastructure/src/test/kotlin/com/cryptoquant/infrastructure/upbit/client/UpbitRateLimiterTest.kt`

---

## 3단계: Gateway 구현

### 목표
QuotationGateway, ExchangeGateway 구현

### 선행 조건
- 2단계 완료

### 체크리스트

- [x] **응답 DTO 정의**
  - 파일: `infrastructure/src/main/kotlin/com/cryptoquant/infrastructure/upbit/dto/response/`
  - 내용:
    - `UpbitCandleResponse.kt`
    - `UpbitTickerResponse.kt`
    - `UpbitOrderbookResponse.kt`
    - `UpbitTradeResponse.kt`
    - `UpbitOrderResponse.kt`
    - `UpbitBalanceResponse.kt`
    - `UpbitOrderChanceResponse.kt`

- [x] **도메인 매퍼 구현**
  - 파일: `infrastructure/src/main/kotlin/com/cryptoquant/infrastructure/upbit/mapper/UpbitDomainMapper.kt`
  - 내용: DTO → 도메인 모델 변환 함수들

- [x] **QuotationGateway 구현**
  - 파일: `infrastructure/src/main/kotlin/com/cryptoquant/infrastructure/upbit/gateway/UpbitQuotationGateway.kt`
  - 내용:
    - `getCandles()` - 캔들 조회
    - `getTicker()` - 현재가 조회
    - `getOrderbook()` - 호가 조회
    - `getTrades()` - 체결 내역 조회

- [x] **ExchangeGateway 구현**
  - 파일: `infrastructure/src/main/kotlin/com/cryptoquant/infrastructure/upbit/gateway/UpbitExchangeGateway.kt`
  - 내용:
    - `placeOrder()` - 주문 생성 (buildJsonObject 사용)
    - `cancelOrder()` - 주문 취소
    - `getOrder()` - 주문 조회
    - `getOpenOrders()` - 미체결 주문 목록 (OffsetPageRequest 사용)
    - `getBalances()` - 잔고 조회
    - `getOrderChance()` - 주문 가능 정보

- [x] **단위 테스트 작성** (6단계에서 진행)
  - 파일: `infrastructure/src/test/kotlin/com/cryptoquant/infrastructure/upbit/mapper/UpbitDomainMapperTest.kt`

- [x] **WireMock 통합 테스트 작성** (6단계에서 진행)
  - 파일: `infrastructure/src/test/kotlin/com/cryptoquant/infrastructure/upbit/gateway/UpbitQuotationGatewayWireMockTest.kt`
  - 파일: `infrastructure/src/test/kotlin/com/cryptoquant/infrastructure/upbit/gateway/UpbitExchangeGatewayWireMockTest.kt`

### 예상 산출물
- `infrastructure/src/main/kotlin/com/cryptoquant/infrastructure/upbit/dto/response/*.kt` (7개 파일)
- `infrastructure/src/main/kotlin/com/cryptoquant/infrastructure/upbit/mapper/UpbitDomainMapper.kt`
- `infrastructure/src/main/kotlin/com/cryptoquant/infrastructure/upbit/gateway/UpbitQuotationGateway.kt`
- `infrastructure/src/main/kotlin/com/cryptoquant/infrastructure/upbit/gateway/UpbitExchangeGateway.kt`
- `infrastructure/src/test/kotlin/com/cryptoquant/infrastructure/upbit/mapper/UpbitDomainMapperTest.kt`
- `infrastructure/src/test/kotlin/com/cryptoquant/infrastructure/upbit/gateway/*WireMockTest.kt` (2개 파일)

---

## 4단계: WebSocket 구현

### 목표
WebSocket 클라이언트, RealtimeStream 구현

### 선행 조건
- 3단계 완료 (도메인 매퍼 필요)

### 체크리스트

- [x] **WebSocket 메시지 타입 정의**
  - 파일: `infrastructure/src/main/kotlin/com/cryptoquant/infrastructure/upbit/websocket/UpbitWebSocketMessage.kt`
  - 내용: `UpbitWebSocketMessage` sealed interface (Ticker, Orderbook, Trade, MyOrder)

- [x] **메시지 파서 구현**
  - 파일: `infrastructure/src/main/kotlin/com/cryptoquant/infrastructure/upbit/websocket/UpbitMessageParser.kt`
  - 내용: 바이너리 메시지 → `UpbitWebSocketMessage` 변환

- [x] **WebSocket 클라이언트 구현**
  - 파일: `infrastructure/src/main/kotlin/com/cryptoquant/infrastructure/upbit/websocket/UpbitWebSocketClient.kt`
  - 내용:
    - OkHttp WebSocket 사용
    - `connectAndSubscribe()` - Flow 반환
    - `retryWithBackoff()` - 재연결 로직 (`Duration.multipliedBy()` 사용)
    - `WebSocketClosedException` - 서버 종료 시 재연결 트리거
    - 재연결 정책: code != 1000 시 재시도

- [x] **RealtimeStream 구현**
  - 파일: `infrastructure/src/main/kotlin/com/cryptoquant/infrastructure/upbit/websocket/UpbitRealtimeStream.kt`
  - 내용:
    - `subscribeTicker()` - 현재가 스트림
    - `subscribeOrderbook()` - 호가 스트림
    - `subscribeTrade()` - 체결 스트림
    - `subscribeMyOrder()` - 내 주문 스트림 (인증)
    - `buildSubscribeMessage()` - 구독 메시지 생성

- [x] **단위 테스트 작성**
  - 파일: `infrastructure/src/test/kotlin/com/cryptoquant/infrastructure/upbit/websocket/UpbitMessageParserTest.kt`

### 예상 산출물
- `infrastructure/src/main/kotlin/com/cryptoquant/infrastructure/upbit/websocket/UpbitWebSocketMessage.kt`
- `infrastructure/src/main/kotlin/com/cryptoquant/infrastructure/upbit/websocket/UpbitMessageParser.kt`
- `infrastructure/src/main/kotlin/com/cryptoquant/infrastructure/upbit/websocket/UpbitWebSocketClient.kt`
- `infrastructure/src/main/kotlin/com/cryptoquant/infrastructure/upbit/websocket/UpbitRealtimeStream.kt`
- `infrastructure/src/test/kotlin/com/cryptoquant/infrastructure/upbit/websocket/UpbitMessageParserTest.kt`

---

## 5단계: Repository 구현

### 목표
R2DBC 기반 OrderRepository 구현

### 선행 조건
- 1단계 완료 (R2DBC 의존성)

### 체크리스트

- [x] **데이터베이스 스키마 생성**
  - 파일: `infrastructure/src/main/resources/db/migration/V1__create_orders_table.sql`
  - 내용: `orders` 테이블, 인덱스 생성

- [x] **R2DBC 설정**
  - 파일: `infrastructure/src/main/kotlin/com/cryptoquant/infrastructure/config/R2dbcConfig.kt`
  - 내용: R2DBC 커넥션 풀 설정

- [x] **엔티티 정의**
  - 파일: `infrastructure/src/main/kotlin/com/cryptoquant/infrastructure/repository/OrderEntity.kt`
  - 내용: `OrderEntity` data class with `@Table`, `@Id`

- [x] **엔티티 매퍼 구현**
  - 파일: `infrastructure/src/main/kotlin/com/cryptoquant/infrastructure/repository/OrderEntityMapper.kt`
  - 내용: 엔티티 ↔ 도메인 모델 변환

- [x] **OrderRepository 구현**
  - 파일: `infrastructure/src/main/kotlin/com/cryptoquant/infrastructure/repository/OrderR2dbcRepository.kt`
  - 내용:
    - `save()` - UPSERT (ON CONFLICT)
    - `findById()` - 단건 조회
    - `findOpenOrders()` - 미체결 주문 조회 (CursorPageRequest 사용, `Instant.parse()`)

- [x] **TestContainers 통합 테스트 작성**
  - 파일: `infrastructure/src/test/kotlin/com/cryptoquant/infrastructure/repository/OrderR2dbcRepositoryIntegrationTest.kt`
  - 내용: PostgreSQL 컨테이너로 CRUD 테스트

### 예상 산출물
- `infrastructure/src/main/resources/db/migration/V1__create_orders_table.sql`
- `infrastructure/src/main/kotlin/com/cryptoquant/infrastructure/config/R2dbcConfig.kt`
- `infrastructure/src/main/kotlin/com/cryptoquant/infrastructure/repository/OrderEntity.kt`
- `infrastructure/src/main/kotlin/com/cryptoquant/infrastructure/repository/OrderEntityMapper.kt`
- `infrastructure/src/main/kotlin/com/cryptoquant/infrastructure/repository/OrderR2dbcRepository.kt`
- `infrastructure/src/test/kotlin/com/cryptoquant/infrastructure/repository/OrderR2dbcRepositoryIntegrationTest.kt`

---

## 6단계: 통합 테스트

### 목표
전체 인프라 레이어 통합 테스트, 문서화

### 선행 조건
- 5단계 완료

### 체크리스트

- [x] **통합 테스트 베이스 클래스**
  - 파일: `infrastructure/src/test/kotlin/com/cryptoquant/infrastructure/IntegrationTestBase.kt`
  - 내용: PostgreSQL TestContainer, DynamicPropertySource

- [x] **REST 클라이언트 WireMock 테스트 보강**
  - 파일: `infrastructure/src/test/kotlin/com/cryptoquant/infrastructure/upbit/client/UpbitRestClientWireMockTest.kt`
  - 내용: 401, 429, 400, 500 에러 변환 테스트

- [x] **실제 API 통합 테스트** (선택적, CI 제외)
  - 파일: `infrastructure/src/test/kotlin/com/cryptoquant/infrastructure/upbit/gateway/UpbitQuotationGatewayIntegrationTest.kt`
  - 내용: 실제 Upbit API 호출 테스트 (`@Disabled` 또는 프로파일 분리)

- [x] **코드 품질 검사**
  - `./gradlew ktlintFormat`
  - `./gradlew ktlintCheck`
  - `./gradlew detekt`
  - `./gradlew check`

- [x] **테크스펙 최종 검토**
  - 구현 결과와 테크스펙 일치 확인
  - 필요시 테크스펙 업데이트

### 예상 산출물
- `infrastructure/src/test/kotlin/com/cryptoquant/infrastructure/IntegrationTestBase.kt`
- `infrastructure/src/test/kotlin/com/cryptoquant/infrastructure/upbit/client/UpbitRestClientWireMockTest.kt`
- (선택) `infrastructure/src/test/kotlin/com/cryptoquant/infrastructure/upbit/gateway/UpbitQuotationGatewayIntegrationTest.kt`

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
- [x] 테크스펙과 구현 일치 확인

---

## 파일 구조 요약

```
subproject/infrastructure/src/
├── main/
│   ├── kotlin/com/cryptoquant/infrastructure/
│   │   ├── common/
│   │   │   └── Pagination.kt
│   │   ├── config/
│   │   │   └── R2dbcConfig.kt
│   │   ├── repository/
│   │   │   ├── OrderEntity.kt
│   │   │   ├── OrderEntityMapper.kt
│   │   │   └── OrderR2dbcRepository.kt
│   │   └── upbit/
│   │       ├── client/
│   │       │   ├── UpbitAuthInterceptor.kt
│   │       │   ├── UpbitRateLimiter.kt
│   │       │   └── UpbitRestClient.kt
│   │       ├── config/
│   │       │   ├── UpbitClientConfig.kt
│   │       │   └── UpbitProperties.kt
│   │       ├── dto/response/
│   │       │   ├── UpbitCandleResponse.kt
│   │       │   ├── UpbitTickerResponse.kt
│   │       │   ├── UpbitOrderbookResponse.kt
│   │       │   ├── UpbitTradeResponse.kt
│   │       │   ├── UpbitOrderResponse.kt
│   │       │   ├── UpbitBalanceResponse.kt
│   │       │   └── UpbitOrderChanceResponse.kt
│   │       ├── error/
│   │       │   └── UpbitApiError.kt
│   │       ├── gateway/
│   │       │   ├── UpbitQuotationGateway.kt
│   │       │   └── UpbitExchangeGateway.kt
│   │       ├── mapper/
│   │       │   └── UpbitDomainMapper.kt
│   │       ├── serializer/
│   │       │   └── BigDecimalSerializer.kt
│   │       └── websocket/
│   │           ├── UpbitWebSocketMessage.kt
│   │           ├── UpbitMessageParser.kt
│   │           ├── UpbitWebSocketClient.kt
│   │           └── UpbitRealtimeStream.kt
│   └── resources/
│       ├── application.yml
│       └── db/migration/
│           └── V1__create_orders_table.sql
└── test/
    └── kotlin/com/cryptoquant/infrastructure/
        ├── IntegrationTestBase.kt
        ├── repository/
        │   └── OrderR2dbcRepositoryIntegrationTest.kt
        └── upbit/
            ├── client/
            │   ├── UpbitAuthInterceptorTest.kt
            │   ├── UpbitRateLimiterTest.kt
            │   └── UpbitRestClientWireMockTest.kt
            ├── gateway/
            │   ├── UpbitQuotationGatewayWireMockTest.kt
            │   └── UpbitExchangeGatewayWireMockTest.kt
            ├── mapper/
            │   └── UpbitDomainMapperTest.kt
            └── websocket/
                └── UpbitMessageParserTest.kt
```
