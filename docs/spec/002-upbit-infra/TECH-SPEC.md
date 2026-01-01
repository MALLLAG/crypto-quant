# 업비트 인프라 레이어 연동 테크스펙

> **작성일**: 2026-01-01
> **작성자**: Claude

---

## 1. 개요

### 1.1 목적

- **문제 정의**: 도메인 모델(`001-upbit-domain`)이 정의되었으나, 실제 업비트 API 및 데이터베이스와 연동하는 인프라 구현체가 없음
- **해결 방안**: 도메인 계층의 Gateway/Repository 인터페이스를 구현하여 업비트 API 및 PostgreSQL과 연동
- **기대 효과**:
  - 업비트 API를 통한 실시간 시세 조회 및 자동매매
  - 주문 내역의 영속화를 통한 거래 이력 관리
  - WebSocket을 통한 실시간 시세/주문 스트림 수신

### 1.2 범위

#### 포함 (In Scope)
- Upbit REST API 연동 (시세 + 거래)
- Upbit WebSocket 연동 (실시간 스트림)
- PostgreSQL R2DBC 연동 (주문 영속화)
- Rate Limit 관리
- JWT 인증 구현

#### 제외 (Out of Scope)
- 입출금 API 연동 (향후 확장)
- 트래블룰 검증 (향후 확장)
- 캔들 히스토리 저장 (주문만 저장)
- 계정 잔고 캐싱

### 1.3 용어 정의

| 용어 | 설명 | 예시 |
|------|------|------|
| Gateway | 외부 서비스와의 통신 인터페이스 | ExchangeGateway, QuotationGateway |
| Repository | 데이터 영속화 인터페이스 | OrderRepository |
| DTO | Data Transfer Object, API 요청/응답 모델 | UpbitTickerResponse |
| Rate Limit | API 요청 수 제한 | 초당 10회 |

### 1.4 관련 문서
- [업비트 도메인 모델 테크스펙](../001-upbit-domain/TECH-SPEC.md)
- [업비트 문서](../../upbit.md)

---

## 2. 요구사항

### 2.1 기능적 요구사항

#### FR-001: 시세 조회 (QuotationGateway)
- **설명**: 캔들, 현재가, 호가, 체결 내역 조회
- **입력**: TradingPair, CandleUnit, 조회 개수
- **출력**: Candle, Ticker, Orderbook, Trade 도메인 객체
- **검증 기준**: WireMock으로 API 응답 모킹 후 도메인 변환 검증

#### FR-002: 주문 실행 (ExchangeGateway)
- **설명**: 주문 생성, 조회, 취소
- **입력**: ValidatedOrderRequest, OrderId
- **출력**: Order 도메인 객체
- **검증 기준**: JWT 인증 토큰 생성 및 API 호출 검증

#### FR-003: 잔고 조회 (ExchangeGateway)
- **설명**: 계정 잔고 및 주문 가능 정보 조회
- **입력**: TradingPair (주문 가능 정보)
- **출력**: Balance, OrderChance 도메인 객체
- **검증 기준**: API 응답을 도메인 모델로 변환 검증

#### FR-004: 실시간 스트림 (RealtimeStream)
- **설명**: WebSocket을 통한 실시간 데이터 수신
- **입력**: 구독할 TradingPair 목록
- **출력**: Flow<Ticker>, Flow<Orderbook>, Flow<Trade>, Flow<OrderEvent>
- **검증 기준**: WebSocket 연결 및 메시지 파싱 검증

#### FR-005: 주문 영속화 (OrderRepository)
- **설명**: 주문 저장 및 조회
- **입력**: Order 도메인 객체
- **출력**: 저장 결과, 조회된 Order
- **검증 기준**: TestContainers PostgreSQL로 CRUD 검증

### 2.2 비기능적 요구사항

#### NFR-001: 성능
- **응답 시간**: API 호출 < 500ms (p95)
- **처리량**: Rate Limit 범위 내 최대 처리
- **측정 방법**: Micrometer 메트릭 수집

#### NFR-002: 보안
- **인증**: JWT HS512 서명, Secret Key 안전한 보관
- **데이터 보호**: API Key는 환경변수로 주입, 로그에 마스킹

#### NFR-003: 가용성
- **재연결**: WebSocket 끊김 시 자동 재연결 (exponential backoff)
- **장애 복구**: API 오류 시 적절한 오류 타입 반환

#### NFR-004: 확장성
- **수평 확장**: 무상태 설계, 여러 인스턴스 실행 가능
- **제한 사항**: Rate Limit은 계정 단위로 공유됨

### 2.3 제약 조건
- Upbit API Rate Limit 준수 필수
- WebSocket 120초 무활동 시 연결 종료
- R2DBC 논블로킹 I/O 사용

---

## 3. 시스템 설계

### 3.1 아키텍처 개요

```
┌──────────────────────────────────────────────────────────────────┐
│                        Application Layer                          │
│  (UseCase: PlaceOrder, CancelOrder, GetQuotation, GetAccount)     │
└─────────────────────────────┬────────────────────────────────────┘
                              │ uses interfaces
┌─────────────────────────────▼────────────────────────────────────┐
│                         Domain Layer                              │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │  Interfaces (Ports)                                         │ │
│  │  - QuotationGateway                                         │ │
│  │  - ExchangeGateway                                          │ │
│  │  - RealtimeStream                                           │ │
│  │  - OrderRepository                                          │ │
│  └─────────────────────────────────────────────────────────────┘ │
└─────────────────────────────┬────────────────────────────────────┘
                              │ implements
┌─────────────────────────────▼────────────────────────────────────┐
│                      Infrastructure Layer                         │
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────────┐  │
│  │ UpbitQuotation  │  │UpbitExchange    │  │ UpbitRealtime    │  │
│  │    Gateway      │  │   Gateway       │  │    Stream        │  │
│  └────────┬────────┘  └────────┬────────┘  └────────┬─────────┘  │
│           │                    │                     │            │
│  ┌────────▼────────────────────▼─────────────────────▼─────────┐ │
│  │                    UpbitRestClient                          │ │
│  │  - JWT 인증 (UpbitAuthInterceptor)                          │ │
│  │  - Rate Limit (UpbitRateLimiter)                            │ │
│  │  - WebClient                                                │ │
│  └─────────────────────────────────────────────────────────────┘ │
│                                                                   │
│  ┌──────────────────┐  ┌──────────────────────────────────────┐  │
│  │ OrderR2dbc       │  │ UpbitWebSocketClient                 │  │
│  │ Repository       │  │  - 연결 관리, 재연결                 │  │
│  └────────┬─────────┘  └──────────────────────────────────────┘  │
│           │                                                       │
│  ┌────────▼─────────┐                                            │
│  │   PostgreSQL     │                                            │
│  │   (R2DBC)        │                                            │
│  └──────────────────┘                                            │
└──────────────────────────────────────────────────────────────────┘
```

### 3.2 패키지 구조

```
subproject/infrastructure/src/main/kotlin/com/cryptoquant/infrastructure/
├── upbit/
│   ├── client/
│   │   ├── UpbitRestClient.kt           # WebClient 기반 HTTP 클라이언트
│   │   ├── UpbitAuthInterceptor.kt      # JWT 토큰 생성 및 요청 인터셉터
│   │   └── UpbitRateLimiter.kt          # Rate Limit 관리 (Bucket4j)
│   ├── gateway/
│   │   ├── UpbitQuotationGateway.kt     # QuotationGateway 구현
│   │   └── UpbitExchangeGateway.kt      # ExchangeGateway 구현
│   ├── websocket/
│   │   ├── UpbitWebSocketClient.kt      # WebSocket 연결 관리
│   │   ├── UpbitRealtimeStream.kt       # RealtimeStream 구현
│   │   └── UpbitMessageParser.kt        # JSON 메시지 파싱
│   ├── dto/
│   │   ├── request/
│   │   │   └── UpbitOrderRequest.kt     # 주문 요청 DTO
│   │   └── response/
│   │       ├── UpbitCandleResponse.kt   # 캔들 응답 DTO
│   │       ├── UpbitTickerResponse.kt   # 현재가 응답 DTO
│   │       ├── UpbitOrderbookResponse.kt # 호가 응답 DTO
│   │       ├── UpbitTradeResponse.kt    # 체결 응답 DTO
│   │       ├── UpbitOrderResponse.kt    # 주문 응답 DTO
│   │       ├── UpbitBalanceResponse.kt  # 잔고 응답 DTO
│   │       └── UpbitOrderChanceResponse.kt # 주문 가능 정보 DTO
│   ├── mapper/
│   │   └── UpbitDomainMapper.kt         # DTO <-> 도메인 모델 변환
│   ├── error/
│   │   └── UpbitApiError.kt             # Upbit API 에러 타입
│   └── config/
│       ├── UpbitProperties.kt           # 설정 프로퍼티
│       └── UpbitClientConfig.kt         # WebClient Bean 설정
├── repository/
│   ├── OrderR2dbcRepository.kt          # OrderRepository 구현
│   ├── OrderEntity.kt                   # R2DBC 엔티티
│   └── OrderEntityMapper.kt             # 엔티티 <-> 도메인 변환
└── config/
    └── R2dbcConfig.kt                   # R2DBC 설정
```

### 3.3 REST API 클라이언트

#### 3.3.1 JWT 인증 구현

```kotlin
/**
 * Upbit API 인증을 위한 JWT 토큰 생성기.
 *
 * - 알고리즘: HS512 (권장)
 * - Payload: access_key, nonce, query_hash (필요시), query_hash_alg
 * - 서명: Secret Key로 HMAC-SHA512
 *
 * @see <a href="https://docs.upbit.com/kr/reference/auth.md">Upbit 인증 문서</a>
 *
 * 주의사항:
 * - query_hash는 URL 인코딩 되지 않은 쿼리 문자열 기준으로 생성
 * - 파라미터 순서를 변경하거나 재정렬하지 않음
 * - 배열 파라미터는 key[]=value 형식 사용
 */
class UpbitAuthInterceptor(
    private val accessKey: String,
    private val secretKey: String,
) {
    /**
     * JWT 토큰 생성.
     *
     * @param queryParams GET/DELETE 요청의 쿼리 파라미터 (해싱 대상)
     * @param bodyParams POST 요청의 본문 파라미터 (해싱 대상, Map 형태)
     */
    fun generateToken(
        queryParams: Map<String, Any>? = null,
        bodyParams: Map<String, Any>? = null,
    ): String {
        val payload = buildMap {
            put("access_key", accessKey)
            put("nonce", UUID.randomUUID().toString())

            // 쿼리 해싱 (파라미터가 있는 경우)
            val paramsToHash = queryParams ?: bodyParams
            if (paramsToHash != null && paramsToHash.isNotEmpty()) {
                val queryString = paramsToHash.toQueryString()
                val hash = queryString.sha512()
                put("query_hash", hash)
                put("query_hash_alg", "SHA512")
            }
        }

        return JWT.create()
            .withHeader(mapOf("alg" to "HS512", "typ" to "JWT"))
            .withPayload(payload)
            .sign(Algorithm.HMAC512(secretKey))
    }

    /**
     * Map을 쿼리 문자열로 변환.
     *
     * 주의: URL 인코딩하지 않음 (Upbit 요구사항)
     * 주의: 파라미터 순서 유지 (LinkedHashMap 사용 권장)
     */
    private fun Map<String, Any>.toQueryString(): String =
        entries.flatMap { (key, value) ->
            when (value) {
                is List<*> -> value.map { "$key[]=$it" }
                else -> listOf("$key=$value")
            }
        }.joinToString("&")

    private fun String.sha512(): String =
        MessageDigest.getInstance("SHA-512")
            .digest(toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
```

#### 3.3.2 Rate Limiter 구현

```kotlin
/**
 * Upbit API Rate Limit 관리.
 *
 * API 그룹별 Rate Limit:
 * - quotation: 10/sec (IP 단위)
 * - exchange-default: 30/sec (계정 단위)
 * - order: 8/sec (계정 단위)
 */
class UpbitRateLimiter {
    private val buckets = mapOf(
        ApiGroup.QUOTATION to createBucket(10),
        ApiGroup.EXCHANGE_DEFAULT to createBucket(30),
        ApiGroup.ORDER to createBucket(8),
    )

    enum class ApiGroup {
        QUOTATION,
        EXCHANGE_DEFAULT,
        ORDER,
    }

    private fun createBucket(limit: Long): Bucket =
        Bucket.builder()
            .addLimit(Bandwidth.simple(limit, Duration.ofSeconds(1)))
            .build()

    /**
     * Rate Limit 확인 후 토큰 소비.
     * 제한 초과 시 GatewayError.RateLimitError 발생.
     */
    context(_: Raise<GatewayError>)
    suspend fun <T> withRateLimit(group: ApiGroup, block: suspend () -> T): T {
        val bucket = buckets[group] ?: error("Unknown API group: $group")

        if (!bucket.tryConsume(1)) {
            raise(GatewayError.RateLimitError(
                code = "RATE_LIMIT_EXCEEDED",
                message = "API 요청 제한 초과: $group"
            ))
        }

        return block()
    }
}
```

#### 3.3.3 REST 클라이언트

```kotlin
/**
 * Upbit REST API 클라이언트.
 *
 * - WebClient 기반 논블로킹 HTTP 클라이언트
 * - 자동 JWT 인증 헤더 추가
 * - Rate Limit 관리
 * - HTTP 에러를 GatewayError로 변환
 */
@Component
class UpbitRestClient(
    private val webClient: WebClient,
    private val authInterceptor: UpbitAuthInterceptor,
    private val rateLimiter: UpbitRateLimiter,
    private val json: Json,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 인증이 필요 없는 GET 요청 (시세 조회).
     */
    context(_: Raise<GatewayError>)
    suspend inline fun <reified T> getPublic(
        path: String,
        params: Map<String, Any> = emptyMap(),
    ): T = rateLimiter.withRateLimit(ApiGroup.QUOTATION) {
        executeWithErrorHandling {
            webClient.get()
                .uri { builder ->
                    builder.path(path)
                    params.forEach { (key, value) -> builder.queryParam(key, value) }
                    builder.build()
                }
                .retrieve()
                .awaitBody<T>()
        }
    }

    /**
     * 인증이 필요한 GET 요청 (잔고, 주문 조회).
     */
    context(_: Raise<GatewayError>)
    suspend inline fun <reified T> getPrivate(
        path: String,
        params: Map<String, Any> = emptyMap(),
        group: ApiGroup = ApiGroup.EXCHANGE_DEFAULT,
    ): T = rateLimiter.withRateLimit(group) {
        val token = authInterceptor.generateToken(queryParams = params)

        executeWithErrorHandling {
            webClient.get()
                .uri { builder ->
                    builder.path(path)
                    params.forEach { (key, value) -> builder.queryParam(key, value) }
                    builder.build()
                }
                .header("Authorization", "Bearer $token")
                .retrieve()
                .awaitBody<T>()
        }
    }

    /**
     * 인증이 필요한 POST 요청 (주문 생성).
     *
     * @param bodyParams 요청 본문 파라미터 (JWT 해싱에 사용)
     * @param bodyJson JSON 직렬화된 요청 본문 (buildJsonObject로 생성)
     *
     * 주의: kotlinx.serialization은 Map<String, Any>를 직렬화할 수 없으므로,
     * 호출부에서 buildJsonObject를 사용해 JsonObject를 생성한 후 toString()으로 전달합니다.
     */
    context(_: Raise<GatewayError>)
    suspend inline fun <reified R> postPrivate(
        path: String,
        bodyParams: Map<String, Any>,
        bodyJson: String,
        group: ApiGroup = ApiGroup.ORDER,
    ): R = rateLimiter.withRateLimit(group) {
        val token = authInterceptor.generateToken(bodyParams = bodyParams)

        executeWithErrorHandling {
            webClient.post()
                .uri(path)
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(bodyJson)
                .retrieve()
                .awaitBody<R>()
        }
    }

    /**
     * 인증이 필요한 DELETE 요청 (주문 취소).
     */
    context(_: Raise<GatewayError>)
    suspend inline fun <reified T> deletePrivate(
        path: String,
        params: Map<String, Any> = emptyMap(),
        group: ApiGroup = ApiGroup.ORDER,
    ): T = rateLimiter.withRateLimit(group) {
        val token = authInterceptor.generateToken(queryParams = params)

        executeWithErrorHandling {
            webClient.delete()
                .uri { builder ->
                    builder.path(path)
                    params.forEach { (key, value) -> builder.queryParam(key, value) }
                    builder.build()
                }
                .header("Authorization", "Bearer $token")
                .retrieve()
                .awaitBody<T>()
        }
    }

    /**
     * HTTP 에러를 GatewayError로 변환.
     *
     * WebClient의 예외를 캐치하여 Raise 컨텍스트로 전환합니다.
     *
     * @see <a href="https://docs.upbit.com/kr/reference/rest-api-guide.md">Upbit REST API 에러 안내</a>
     */
    context(_: Raise<GatewayError>)
    private suspend inline fun <T> executeWithErrorHandling(block: () -> T): T {
        return try {
            block()
        } catch (e: WebClientResponseException) {
            val errorBody = e.responseBodyAsString
            val upbitError = runCatching {
                json.decodeFromString<UpbitErrorResponse>(errorBody)
            }.getOrNull()

            val gatewayError = when (e.statusCode.value()) {
                401 -> GatewayError.AuthenticationError(
                    code = upbitError?.error?.name ?: "UNAUTHORIZED",
                    message = upbitError?.error?.message ?: e.message ?: "Authentication failed"
                )
                429 -> GatewayError.RateLimitError(
                    code = "RATE_LIMIT",
                    message = upbitError?.error?.message ?: "Too many requests"
                )
                in 400..499 -> GatewayError.ApiError(
                    code = upbitError?.error?.name ?: "CLIENT_ERROR",
                    message = upbitError?.error?.message ?: e.message ?: "Client error"
                )
                in 500..599 -> GatewayError.NetworkError(
                    code = "SERVER_ERROR",
                    message = upbitError?.error?.message ?: e.message ?: "Server error"
                )
                else -> GatewayError.ApiError(
                    code = "UNKNOWN",
                    message = e.message ?: "Unknown error"
                )
            }

            logger.warn("Upbit API error: ${e.statusCode} - $errorBody", e)
            raise(gatewayError)
        } catch (e: Exception) {
            logger.error("Unexpected error during API call", e)
            raise(GatewayError.NetworkError(
                code = "NETWORK_ERROR",
                message = e.message ?: "Network error"
            ))
        }
    }
}

/**
 * Upbit API 에러 응답.
 */
@Serializable
data class UpbitErrorResponse(
    val error: ErrorDetail,
) {
    @Serializable
    data class ErrorDetail(
        val name: String,
        val message: String,
    )
}
```

### 3.4 Gateway 구현

#### 3.4.1 QuotationGateway 구현

```kotlin
/**
 * QuotationGateway 구현.
 *
 * 업비트 시세 API를 호출하여 도메인 객체로 변환합니다.
 */
@Component
class UpbitQuotationGateway(
    private val client: UpbitRestClient,
    private val mapper: UpbitDomainMapper,
) : QuotationGateway {

    context(_: Raise<GatewayError>)
    override suspend fun getCandles(
        pair: TradingPair,
        unit: CandleUnit,
        count: Int,
        to: Instant?,
    ): List<Candle> {
        val path = when (unit) {
            is CandleUnit.Seconds -> raise(GatewayError.ApiError(
                code = "UNSUPPORTED",
                message = "초봉은 REST API에서 지원하지 않습니다. WebSocket을 사용하세요."
            ))
            is CandleUnit.Minutes -> "/v1/candles/minutes/${unit.minutes}"
            CandleUnit.Day -> "/v1/candles/days"
            CandleUnit.Week -> "/v1/candles/weeks"
            CandleUnit.Month -> "/v1/candles/months"
        }

        val params = buildMap {
            put("market", pair.value)
            put("count", count.coerceIn(1, 200))
            to?.let { put("to", it.toString()) }
        }

        val response: List<UpbitCandleResponse> = client.getPublic(path, params)
        return response.map { mapper.toCandle(it, pair, unit) }
    }

    context(_: Raise<GatewayError>)
    override suspend fun getTicker(pairs: List<TradingPair>): List<Ticker> {
        val markets = pairs.joinToString(",") { it.value }
        val response: List<UpbitTickerResponse> = client.getPublic(
            "/v1/ticker",
            mapOf("markets" to markets)
        )
        return response.map { mapper.toTicker(it) }
    }

    context(_: Raise<GatewayError>)
    override suspend fun getOrderbook(pairs: List<TradingPair>): List<Orderbook> {
        val markets = pairs.joinToString(",") { it.value }
        val response: List<UpbitOrderbookResponse> = client.getPublic(
            "/v1/orderbook",
            mapOf("markets" to markets)
        )
        return response.map { mapper.toOrderbook(it) }
    }

    context(_: Raise<GatewayError>)
    override suspend fun getTrades(
        pair: TradingPair,
        count: Int,
        cursor: TradeSequentialId?,
    ): List<Trade> {
        val params = buildMap {
            put("market", pair.value)
            put("count", count.coerceIn(1, 500))
            cursor?.let { put("cursor", it.value) }
        }

        val response: List<UpbitTradeResponse> = client.getPublic("/v1/trades/ticks", params)
        return response.map { mapper.toTrade(it, pair) }
    }
}
```

#### 3.4.2 ExchangeGateway 구현

```kotlin
/**
 * ExchangeGateway 구현.
 *
 * 업비트 거래 API를 호출하여 주문, 잔고 조회 등을 수행합니다.
 */
@Component
class UpbitExchangeGateway(
    private val client: UpbitRestClient,
    private val mapper: UpbitDomainMapper,
) : ExchangeGateway {

    context(_: Raise<GatewayError>)
    override suspend fun placeOrder(request: ValidatedOrderRequest): Order {
        // LinkedHashMap으로 파라미터 순서 보장 (JWT 해싱에 중요)
        val bodyParams = linkedMapOf<String, Any>(
            "market" to request.pair.value,
            "side" to request.side.toUpbitSide(),
            "ord_type" to request.orderType.toUpbitOrdType(),
        )
        request.orderType.volumeOrNull()?.let { bodyParams["volume"] = it.value.toPlainString() }
        request.orderType.priceOrNull()?.let { bodyParams["price"] = it.value.toPlainString() }

        // kotlinx.serialization은 Map<String, Any> 직렬화 불가 → buildJsonObject 사용
        val bodyJson = buildJsonObject {
            put("market", request.pair.value)
            put("side", request.side.toUpbitSide())
            put("ord_type", request.orderType.toUpbitOrdType())
            request.orderType.volumeOrNull()?.let { put("volume", it.value.toPlainString()) }
            request.orderType.priceOrNull()?.let { put("price", it.value.toPlainString()) }
        }.toString()

        val response: UpbitOrderResponse = client.postPrivate("/v1/orders", bodyParams, bodyJson)
        return mapper.toOrder(response)
    }

    context(_: Raise<GatewayError>)
    override suspend fun cancelOrder(orderId: OrderId): Order {
        val response: UpbitOrderResponse = client.deletePrivate(
            "/v1/order",
            mapOf("uuid" to orderId.value)
        )
        return mapper.toOrder(response)
    }

    context(_: Raise<GatewayError>)
    override suspend fun getOrder(orderId: OrderId): Order {
        val response: UpbitOrderResponse = client.getPrivate(
            "/v1/order",
            mapOf("uuid" to orderId.value)
        )
        return mapper.toOrder(response)
    }

    /**
     * 체결 대기 주문 목록 조회.
     *
     * @see <a href="https://docs.upbit.com/kr/reference/list-open-orders.md">Upbit 체결 대기 주문 목록 조회</a>
     *
     * 페이징: page/limit 기반 (offset 방식) - Upbit API가 제공하는 방식
     * - page: 페이지 번호 (1부터 시작, 기본값 1)
     * - limit: 페이지당 항목 수 (기본값 100, 최대 100)
     * - order_by: 정렬 방식 (asc/desc, 기본값 desc)
     */
    context(_: Raise<GatewayError>)
    override suspend fun getOpenOrders(
        pair: TradingPair?,
        page: OffsetPageRequest,
    ): OffsetPageResponse<Order> {
        val params = buildMap {
            pair?.let { put("market", it.value) }
            put("page", page.page)
            put("limit", page.limit.coerceAtMost(100))
            put("order_by", "desc")
        }

        val response: List<UpbitOrderResponse> = client.getPrivate("/v1/orders/open", params)
        val orders = response.map { mapper.toOrder(it) }

        // page 기반 페이징: limit만큼 반환되면 다음 페이지 존재 가능
        val hasNext = orders.size >= page.limit
        return OffsetPageResponse(
            items = orders,
            nextPage = if (hasNext) page.page + 1 else null,
        )
    }

    context(_: Raise<GatewayError>)
    override suspend fun getBalances(): List<Balance> {
        val response: List<UpbitBalanceResponse> = client.getPrivate("/v1/accounts")
        return response.mapNotNull { mapper.toBalance(it) }
    }

    context(_: Raise<GatewayError>)
    override suspend fun getOrderChance(pair: TradingPair): OrderChance {
        val response: UpbitOrderChanceResponse = client.getPrivate(
            "/v1/orders/chance",
            mapOf("market" to pair.value)
        )
        return mapper.toOrderChance(response, pair)
    }
}
```

### 3.5 WebSocket 연동

#### 3.5.1 WebSocket 클라이언트

```kotlin
/**
 * Upbit WebSocket 클라이언트.
 *
 * @see <a href="https://docs.upbit.com/kr/reference/websocket-guide.md">Upbit WebSocket 안내</a>
 *
 * 연결 정보:
 * - Public: wss://api.upbit.com/websocket/v1 (시세 데이터)
 * - Private: wss://api.upbit.com/websocket/v1/private (내 주문/자산)
 *
 * 특징:
 * - 자동 재연결 (exponential backoff) - 예외 발생 및 정상 종료 모두 재연결 시도
 * - 120초 무활동 시 자동 종료 (Ping/Pong으로 유지)
 * - 메시지 파싱 및 Flow 변환
 *
 * 재연결 정책:
 * - 클라이언트가 명시적으로 종료한 경우 (code=1000): 재연결 안함
 * - 서버가 종료하거나 오류 발생: 재연결 시도
 */
@Component
class UpbitWebSocketClient(
    private val properties: UpbitProperties,
    private val authInterceptor: UpbitAuthInterceptor,
    private val parser: UpbitMessageParser,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 공개 WebSocket 연결 및 구독 (시세 데이터).
     *
     * @param subscribeMessage 구독 메시지 (JSON 배열 형식)
     */
    fun connectAndSubscribe(
        subscribeMessage: String,
        authenticated: Boolean = false,
    ): Flow<UpbitWebSocketMessage> = flow {
        val url = if (authenticated) properties.websocket.privateUrl else properties.websocket.url
        connect(url, authenticated, subscribeMessage).collect { emit(it) }
    }.retryWithBackoff()

    private suspend fun connect(
        url: String,
        authenticated: Boolean,
        subscribeMessage: String,
    ): Flow<UpbitWebSocketMessage> = callbackFlow {
        val client = OkHttpClient.Builder()
            .pingInterval(properties.websocket.pingInterval)
            .build()

        val requestBuilder = Request.Builder().url(url)
        if (authenticated) {
            val token = authInterceptor.generateToken()
            requestBuilder.header("Authorization", "Bearer $token")
        }

        val webSocket = client.newWebSocket(requestBuilder.build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                logger.info("WebSocket connected: $url")
                // 연결 성공 시 구독 메시지 전송
                val sent = webSocket.send(subscribeMessage)
                if (sent) {
                    logger.info("Subscription message sent: $subscribeMessage")
                } else {
                    logger.error("Failed to send subscription message")
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                try {
                    val message = parser.parse(bytes.toByteArray())
                    trySend(message)
                } catch (e: Exception) {
                    logger.error("Failed to parse message", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                logger.error("WebSocket error", t)
                close(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                logger.info("WebSocket closed: $code $reason")
                // 클라이언트가 명시적으로 종료한 경우 (1000)가 아니면 재연결을 위해 예외 발생
                if (code != 1000) {
                    close(WebSocketClosedException(code, reason))
                } else {
                    close()
                }
            }
        })

        awaitClose { webSocket.close(1000, "Client closed") }
    }

    private fun <T> Flow<T>.retryWithBackoff(): Flow<T> = retryWhen { cause, attempt ->
        // 클라이언트가 명시적으로 종료한 경우는 재시도하지 않음
        if (cause is CancellationException) {
            return@retryWhen false
        }

        if (attempt >= properties.websocket.maxReconnectAttempts) {
            logger.error("Max reconnect attempts reached", cause)
            return@retryWhen false
        }
        val multiplier = (1L shl attempt.toInt().coerceAtMost(5))
        val delayDuration = properties.websocket.reconnectDelay.multipliedBy(multiplier)
        logger.warn("Reconnecting in ${delayDuration.toMillis()}ms (attempt ${attempt + 1})", cause)
        delay(delayDuration.toMillis())
        true
    }
}

/**
 * WebSocket이 서버에 의해 종료되었을 때 발생하는 예외.
 * 재연결 로직을 트리거하기 위해 사용됩니다.
 */
class WebSocketClosedException(
    val code: Int,
    val reason: String,
) : Exception("WebSocket closed by server: $code $reason")
```

#### 3.5.2 RealtimeStream 구현

```kotlin
/**
 * RealtimeStream 구현.
 *
 * @see <a href="https://docs.upbit.com/kr/reference/websocket-ticker.md">Upbit WebSocket 현재가</a>
 *
 * WebSocket을 통한 실시간 데이터 스트림 제공.
 *
 * 구독 메시지 형식:
 * [
 *   {"ticket": "unique-ticket-id"},
 *   {"type": "ticker", "codes": ["KRW-BTC", "KRW-ETH"]},
 *   {"format": "DEFAULT"}
 * ]
 */
@Component
class UpbitRealtimeStream(
    private val client: UpbitWebSocketClient,
    private val mapper: UpbitDomainMapper,
) : RealtimeStream {

    override fun subscribeTicker(pairs: List<TradingPair>): Flow<Ticker> {
        val message = buildSubscribeMessage("ticker", pairs)
        return client.connectAndSubscribe(message, authenticated = false)
            .filterIsInstance<UpbitWebSocketMessage.Ticker>()
            .map { mapper.toTicker(it) }
    }

    override fun subscribeOrderbook(pairs: List<TradingPair>): Flow<Orderbook> {
        val message = buildSubscribeMessage("orderbook", pairs)
        return client.connectAndSubscribe(message, authenticated = false)
            .filterIsInstance<UpbitWebSocketMessage.Orderbook>()
            .map { mapper.toOrderbook(it) }
    }

    override fun subscribeTrade(pairs: List<TradingPair>): Flow<Trade> {
        val message = buildSubscribeMessage("trade", pairs)
        return client.connectAndSubscribe(message, authenticated = false)
            .filterIsInstance<UpbitWebSocketMessage.Trade>()
            .map { mapper.toTrade(it) }
    }

    /**
     * 내 주문 및 체결 이벤트 구독.
     *
     * @see <a href="https://docs.upbit.com/kr/reference/websocket-myorder.md">Upbit WebSocket 내 주문</a>
     */
    override fun subscribeMyOrder(): Flow<OrderEvent> {
        val message = buildSubscribeMessage("myOrder", emptyList())
        return client.connectAndSubscribe(message, authenticated = true)
            .filterIsInstance<UpbitWebSocketMessage.MyOrder>()
            .map { mapper.toOrderEvent(it) }
    }

    /**
     * 구독 메시지 생성.
     *
     * @param type 데이터 타입 (ticker, trade, orderbook, myOrder 등)
     * @param pairs 구독할 페어 목록 (빈 목록이면 codes 필드 생략)
     */
    private fun buildSubscribeMessage(type: String, pairs: List<TradingPair>): String =
        Json.encodeToString(listOf(
            mapOf("ticket" to UUID.randomUUID().toString()),
            buildMap {
                put("type", type)
                if (pairs.isNotEmpty()) {
                    put("codes", pairs.map { it.value })
                }
            },
            mapOf("format" to "DEFAULT")
        ))
}
```

### 3.6 데이터베이스 스키마

#### 3.6.1 Orders 테이블

```sql
-- 주문 테이블
CREATE TABLE orders (
    -- 기본 식별자
    id                  VARCHAR(36) PRIMARY KEY,

    -- 거래 정보
    pair                VARCHAR(20) NOT NULL,
    side                VARCHAR(10) NOT NULL,
    order_type          VARCHAR(20) NOT NULL,
    state               VARCHAR(20) NOT NULL,

    -- 주문 수량/가격 (주문 타입별로 다름)
    volume              DECIMAL(20, 8),      -- Limit, MarketSell, Best
    price               DECIMAL(20, 8),      -- Limit
    total_price         DECIMAL(20, 8),      -- MarketBuy

    -- 체결 정보
    remaining_volume    DECIMAL(20, 8) NOT NULL,
    executed_volume     DECIMAL(20, 8) NOT NULL,
    executed_amount     DECIMAL(20, 8) NOT NULL,
    paid_fee            DECIMAL(20, 8) NOT NULL,

    -- 시간 정보
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    done_at             TIMESTAMP WITH TIME ZONE,

    -- 제약 조건
    CONSTRAINT chk_side CHECK (side IN ('BID', 'ASK')),
    CONSTRAINT chk_order_type CHECK (order_type IN ('LIMIT', 'MARKET_BUY', 'MARKET_SELL', 'BEST')),
    CONSTRAINT chk_state CHECK (state IN ('WAIT', 'WATCH', 'DONE', 'CANCEL'))
);

-- 인덱스
CREATE INDEX idx_orders_pair ON orders(pair);
CREATE INDEX idx_orders_state ON orders(state);
CREATE INDEX idx_orders_created_at ON orders(created_at DESC);
CREATE INDEX idx_orders_pair_state ON orders(pair, state);
```

#### 3.6.2 R2DBC Repository 구현

```kotlin
/**
 * R2DBC 기반 Order 엔티티.
 */
@Table("orders")
data class OrderEntity(
    @Id val id: String,
    val pair: String,
    val side: String,
    val orderType: String,
    val state: String,
    val volume: BigDecimal?,
    val price: BigDecimal?,
    val totalPrice: BigDecimal?,
    val remainingVolume: BigDecimal,
    val executedVolume: BigDecimal,
    val executedAmount: BigDecimal,
    val paidFee: BigDecimal,
    val createdAt: Instant,
    val doneAt: Instant?,
)

/**
 * OrderRepository R2DBC 구현.
 */
@Repository
class OrderR2dbcRepository(
    private val databaseClient: DatabaseClient,
    private val mapper: OrderEntityMapper,
) : OrderRepository {

    override suspend fun save(order: Order) {
        val entity = mapper.toEntity(order)

        databaseClient.sql("""
            INSERT INTO orders (
                id, pair, side, order_type, state,
                volume, price, total_price,
                remaining_volume, executed_volume, executed_amount, paid_fee,
                created_at, done_at
            ) VALUES (
                :id, :pair, :side, :orderType, :state,
                :volume, :price, :totalPrice,
                :remainingVolume, :executedVolume, :executedAmount, :paidFee,
                :createdAt, :doneAt
            )
            ON CONFLICT (id) DO UPDATE SET
                state = :state,
                remaining_volume = :remainingVolume,
                executed_volume = :executedVolume,
                executed_amount = :executedAmount,
                paid_fee = :paidFee,
                done_at = :doneAt
        """.trimIndent())
            .bind("id", entity.id)
            .bind("pair", entity.pair)
            .bind("side", entity.side)
            .bind("orderType", entity.orderType)
            .bind("state", entity.state)
            .bind("volume", entity.volume)
            .bind("price", entity.price)
            .bind("totalPrice", entity.totalPrice)
            .bind("remainingVolume", entity.remainingVolume)
            .bind("executedVolume", entity.executedVolume)
            .bind("executedAmount", entity.executedAmount)
            .bind("paidFee", entity.paidFee)
            .bind("createdAt", entity.createdAt)
            .bind("doneAt", entity.doneAt)
            .await()
    }

    override suspend fun findById(orderId: OrderId): Order? {
        return databaseClient.sql("SELECT * FROM orders WHERE id = :id")
            .bind("id", orderId.value)
            .map { row -> mapper.toEntity(row) }
            .awaitOneOrNull()
            ?.let { mapper.toDomain(it) }
    }

    /**
     * 미체결 주문 목록 조회.
     *
     * 페이징: cursor/limit 기반 (cursor 방식) - 대용량 데이터에 효율적
     * - cursor: 마지막 조회 항목의 createdAt (Instant)
     * - limit: 조회할 항목 수
     *
     * cursor 기반 페이징은 offset 기반보다 일관된 결과를 제공합니다.
     * (페이징 중 데이터 추가/삭제 시에도 누락/중복 없음)
     */
    override suspend fun findOpenOrders(
        pair: TradingPair?,
        page: CursorPageRequest,
    ): CursorPageResponse<Order> {
        val sql = buildString {
            append("SELECT * FROM orders WHERE state IN ('WAIT', 'WATCH')")
            pair?.let { append(" AND pair = :pair") }
            page.cursor?.let { append(" AND created_at < :cursor") }
            append(" ORDER BY created_at DESC LIMIT :limit")
        }

        var spec = databaseClient.sql(sql)
            .bind("limit", page.limit + 1) // 다음 페이지 확인용

        pair?.let { spec = spec.bind("pair", it.value) }
        // cursor를 Instant로 변환하여 타입 안전성 보장
        page.cursor?.let { spec = spec.bind("cursor", Instant.parse(it)) }

        val entities = spec.map { row -> mapper.toEntity(row) }.flow().toList()

        val hasNext = entities.size > page.limit
        val items = entities.take(page.limit).map { mapper.toDomain(it) }

        return CursorPageResponse(
            items = items,
            nextCursor = if (hasNext) items.lastOrNull()?.createdAt?.toString() else null
        )
    }
}
```

### 3.7 DTO 정의

#### 3.7.1 응답 DTO

```kotlin
/**
 * BigDecimal 직렬화를 위한 커스텀 Serializer.
 *
 * kotlinx.serialization은 BigDecimal을 기본 지원하지 않으므로
 * 커스텀 Serializer로 정밀도 손실을 방지합니다.
 *
 * 역직렬화 시 JSON 숫자형과 문자열 모두 지원합니다.
 * - Upbit REST API: 숫자형 (예: "price": 145831000)
 * - 일부 필드: 문자열 (예: "balance": "1000.0")
 */
object BigDecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor = PrimitiveSerialDescriptor("BigDecimal", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: BigDecimal) =
        encoder.encodeString(value.toPlainString())

    override fun deserialize(decoder: Decoder): BigDecimal {
        val jsonDecoder = decoder as? JsonDecoder
            ?: return BigDecimal(decoder.decodeString())

        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> when {
                element.isString -> BigDecimal(element.content)
                else -> BigDecimal(element.content) // JSON number
            }
            else -> throw SerializationException("Expected JsonPrimitive for BigDecimal")
        }
    }
}

/**
 * 캔들 응답 DTO.
 *
 * 가격/수량 필드는 BigDecimalSerializer를 통해 JSON 숫자형을 BigDecimal로 변환합니다.
 * Upbit API는 숫자를 JSON number로 반환하므로, Serializer가 이를 처리합니다.
 */
@Serializable
data class UpbitCandleResponse(
    val market: String,
    val candle_date_time_utc: String,
    val candle_date_time_kst: String,
    @Serializable(with = BigDecimalSerializer::class)
    val opening_price: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val high_price: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val low_price: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val trade_price: BigDecimal,
    val timestamp: Long,
    @Serializable(with = BigDecimalSerializer::class)
    val candle_acc_trade_price: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val candle_acc_trade_volume: BigDecimal,
    val unit: Int? = null, // 분봉 단위
)

@Serializable
data class UpbitTickerResponse(
    val market: String,
    @Serializable(with = BigDecimalSerializer::class)
    val trade_price: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val opening_price: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val high_price: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val low_price: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val prev_closing_price: BigDecimal,
    val change: String,
    @Serializable(with = BigDecimalSerializer::class)
    val change_price: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val change_rate: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val signed_change_price: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val signed_change_rate: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val trade_volume: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val acc_trade_price_24h: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val acc_trade_volume_24h: BigDecimal,
    val timestamp: Long,
)

@Serializable
data class UpbitOrderbookResponse(
    val market: String,
    val timestamp: Long,
    @Serializable(with = BigDecimalSerializer::class)
    val total_ask_size: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val total_bid_size: BigDecimal,
    val orderbook_units: List<OrderbookUnitDto>,
) {
    @Serializable
    data class OrderbookUnitDto(
        @Serializable(with = BigDecimalSerializer::class)
        val ask_price: BigDecimal,
        @Serializable(with = BigDecimalSerializer::class)
        val bid_price: BigDecimal,
        @Serializable(with = BigDecimalSerializer::class)
        val ask_size: BigDecimal,
        @Serializable(with = BigDecimalSerializer::class)
        val bid_size: BigDecimal,
    )
}

@Serializable
data class UpbitOrderResponse(
    val uuid: String,
    val side: String,
    val ord_type: String,
    val price: String?,
    val state: String,
    val market: String,
    val volume: String?,
    val remaining_volume: String,
    val executed_volume: String,
    val trades_count: Int,
    val paid_fee: String,
    val created_at: String,
    val locked: String?,
)

@Serializable
data class UpbitBalanceResponse(
    val currency: String,
    val balance: String,
    val locked: String,
    val avg_buy_price: String,
    val avg_buy_price_modified: Boolean,
    val unit_currency: String,
)

@Serializable
data class UpbitOrderChanceResponse(
    val bid_fee: String,
    val ask_fee: String,
    val market: MarketDto,
    val bid_account: AccountDto,
    val ask_account: AccountDto,
) {
    @Serializable
    data class MarketDto(
        val id: String,
        val name: String,
        val order_types: List<String>,
        val ask: OrderConstraintDto,
        val bid: OrderConstraintDto,
        val state: String,
    )

    @Serializable
    data class OrderConstraintDto(
        val currency: String,
        val min_total: String,
    )

    @Serializable
    data class AccountDto(
        val currency: String,
        val balance: String,
        val locked: String,
        val avg_buy_price: String,
        val avg_buy_price_modified: Boolean,
        val unit_currency: String,
    )
}
```

#### 3.7.2 페이징 타입

```kotlin
/**
 * 페이징 요청/응답 타입.
 *
 * 외부 API와 내부 저장소의 페이징 방식이 다르므로 별도 타입으로 구분합니다:
 * - OffsetPageRequest/Response: Upbit API용 (page/limit 기반)
 * - CursorPageRequest/Response: Repository용 (cursor/limit 기반)
 *
 * Offset 기반 페이징:
 * - 장점: 직관적, 특정 페이지로 바로 이동 가능
 * - 단점: 대용량 데이터에서 성능 저하, 페이징 중 데이터 변경 시 누락/중복 가능
 *
 * Cursor 기반 페이징:
 * - 장점: 대용량 데이터에서도 일정한 성능, 데이터 변경에 안전
 * - 단점: 특정 페이지로 바로 이동 불가
 */

// ===== Offset 기반 페이징 (Upbit API용) =====

/**
 * Offset 기반 페이징 요청.
 * Upbit API의 page/limit 파라미터에 매핑됩니다.
 */
data class OffsetPageRequest(
    val page: Int = 1,
    val limit: Int = 100,
) {
    init {
        require(page >= 1) { "page must be >= 1" }
        require(limit in 1..100) { "limit must be between 1 and 100" }
    }
}

/**
 * Offset 기반 페이징 응답.
 */
data class OffsetPageResponse<T>(
    val items: List<T>,
    val nextPage: Int?,
) {
    val hasNext: Boolean get() = nextPage != null
}

// ===== Cursor 기반 페이징 (Repository용) =====

/**
 * Cursor 기반 페이징 요청.
 * 내부 저장소의 cursor/limit 방식에 사용됩니다.
 *
 * @param cursor 마지막 조회 항목의 식별자 (ISO 8601 형식의 createdAt)
 * @param limit 조회할 항목 수
 */
data class CursorPageRequest(
    val cursor: String? = null,
    val limit: Int = 100,
) {
    init {
        require(limit >= 1) { "limit must be >= 1" }
    }
}

/**
 * Cursor 기반 페이징 응답.
 */
data class CursorPageResponse<T>(
    val items: List<T>,
    val nextCursor: String?,
) {
    val hasNext: Boolean get() = nextCursor != null
}
```

### 3.8 에러 처리

```kotlin
/**
 * Upbit API 에러.
 *
 * 인프라 계층 내부에서만 사용되며, 외부로는 GatewayError로 변환됩니다.
 */
sealed interface UpbitApiError {
    val code: String
    val message: String

    data class NetworkError(
        override val code: String = "NETWORK_ERROR",
        override val message: String,
        val cause: Throwable,
    ) : UpbitApiError

    data class AuthenticationError(
        override val code: String,
        override val message: String,
    ) : UpbitApiError

    data class RateLimitError(
        override val code: String = "RATE_LIMIT",
        override val message: String,
        val retryAfter: Duration? = null,
    ) : UpbitApiError

    data class ApiError(
        override val code: String,
        override val message: String,
    ) : UpbitApiError
}

/**
 * UpbitApiError -> GatewayError 변환.
 */
fun UpbitApiError.toGatewayError(): GatewayError = when (this) {
    is UpbitApiError.NetworkError -> GatewayError.NetworkError(code, message)
    is UpbitApiError.AuthenticationError -> GatewayError.AuthenticationError(code, message)
    is UpbitApiError.RateLimitError -> GatewayError.RateLimitError(code, message)
    is UpbitApiError.ApiError -> GatewayError.ApiError(code, message)
}
```

---

## 4. 기술적 결정사항

### 4.1 기술 선택

| 항목 | 선택 | 대안 | 선택 이유 |
|------|------|------|-----------|
| HTTP 클라이언트 | WebClient | Ktor Client, RestTemplate | Spring WebFlux 통합, 논블로킹 지원 |
| WebSocket 클라이언트 | OkHttp | Spring WebSocket, Ktor | 안정성, 자동 재연결 지원 |
| Rate Limiter | Bucket4j | Resilience4j, Guava | 경량, 토큰 버킷 알고리즘 |
| JSON 직렬화 | kotlinx.serialization | Jackson | Kotlin 친화적, 컴파일 타임 안전성 |
| DB 접근 | R2DBC | JDBC, jOOQ | 논블로킹, WebFlux 일관성 |
| JWT 라이브러리 | java-jwt | jjwt, nimbus-jose | 간결한 API |

---

## 5. 엣지 케이스 및 예외 처리

### 5.1 엣지 케이스

| 케이스 | 상황 | 예상 동작 | 처리 방법 |
|--------|------|-----------|-----------|
| EC-001 | Rate Limit 초과 | 429 에러 | RateLimitError 반환, 재시도 안함 |
| EC-002 | WebSocket 연결 끊김 | 스트림 중단 | exponential backoff 재연결 |
| EC-003 | API 응답 파싱 실패 | 데이터 누락 | InvalidResponse 에러, 로깅 |
| EC-004 | JWT 토큰 만료 | 인증 실패 | 매 요청마다 새 토큰 생성 |
| EC-005 | DB 연결 실패 | 저장 실패 | 예외 전파, 트랜잭션 롤백 |
| EC-006 | 주문 중복 저장 | 무결성 위반 | UPSERT로 처리 |

### 5.2 오류 복구 전략

```kotlin
// 재시도 정책
object RetryPolicy {
    // 네트워크 오류: 3회 재시도, exponential backoff
    val networkError = RetrySpec.backoff(3, Duration.ofMillis(100))
        .filter { it is UpbitApiError.NetworkError }

    // Rate Limit: 재시도 안함 (즉시 실패)
    val rateLimitError = RetrySpec.max(0)
        .filter { it is UpbitApiError.RateLimitError }

    // 인증 오류: 재시도 안함 (설정 확인 필요)
    val authError = RetrySpec.max(0)
        .filter { it is UpbitApiError.AuthenticationError }
}
```

---

## 6. 위험 요소 및 완화 방안

| ID | 위험 요소 | 발생 확률 | 영향도 | 완화 방안 | 대응 계획 |
|----|-----------|-----------|--------|-----------|-----------|
| R-001 | Rate Limit 초과로 인한 거래 실패 | 중간 | 높음 | Rate Limiter로 사전 차단 | 요청 큐잉 또는 지연 |
| R-002 | WebSocket 불안정 | 중간 | 중간 | 자동 재연결, 상태 모니터링 | 폴링 폴백 |
| R-003 | API 스펙 변경 | 낮음 | 높음 | DTO 버전 관리 | 문서 모니터링 |
| R-004 | Secret Key 노출 | 낮음 | 높음 | 환경변수 주입, 로그 마스킹 | Key 즉시 재발급 |
| R-005 | DB 성능 저하 | 낮음 | 중간 | 인덱스 최적화 | 읽기 복제본 추가 |

---

## 7. 테스트 전략

### 7.1 단위 테스트

```kotlin
class UpbitAuthInterceptorTest {
    @Test
    fun `JWT 토큰이 올바른 형식으로 생성된다`() {
        val interceptor = UpbitAuthInterceptor("access-key", "secret-key")
        val token = interceptor.generateToken()

        token.shouldNotBeBlank()
        token.split(".").size shouldBe 3
    }

    @Test
    fun `쿼리 파라미터가 올바르게 해싱된다`() {
        val interceptor = UpbitAuthInterceptor("access-key", "secret-key")
        val token = interceptor.generateToken(
            queryParams = mapOf("market" to "KRW-BTC", "count" to 10)
        )

        val payload = JWT.decode(token)
        payload.getClaim("query_hash").asString().shouldNotBeNull()
        payload.getClaim("query_hash_alg").asString() shouldBe "SHA512"
    }
}

class UpbitDomainMapperTest {
    @Test
    fun `캔들 응답이 도메인 객체로 변환된다`() = runTest {
        val response = UpbitCandleResponse(
            market = "KRW-BTC",
            candle_date_time_utc = "2024-01-01T00:00:00",
            candle_date_time_kst = "2024-01-01T09:00:00",
            opening_price = BigDecimal("50000000"),
            high_price = BigDecimal("51000000"),
            low_price = BigDecimal("49000000"),
            trade_price = BigDecimal("50500000"),
            timestamp = 1704067200000,
            candle_acc_trade_price = BigDecimal("1000000000"),
            candle_acc_trade_volume = BigDecimal("20"),
        )

        val candle = mapper.toCandle(response, TradingPair("KRW-BTC"), CandleUnit.Day)

        candle.pair.value shouldBe "KRW-BTC"
        candle.openingPrice.value shouldBe BigDecimal("50000000")
    }
}
```

### 7.2 통합 테스트

```kotlin
@SpringBootTest
@Testcontainers
class OrderR2dbcRepositoryIntegrationTest {
    companion object {
        @Container
        val postgres = PostgreSQLContainer<Nothing>("postgres:15-alpine").apply {
            withDatabaseName("cryptoquant_test")
        }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.r2dbc.url") {
                "r2dbc:postgresql://${postgres.host}:${postgres.firstMappedPort}/${postgres.databaseName}"
            }
        }
    }

    @Autowired
    lateinit var repository: OrderR2dbcRepository

    @Test
    fun `주문을 저장하고 조회할 수 있다`() = runTest {
        // Given
        val order = createTestOrder()

        // When
        repository.save(order)
        val found = repository.findById(order.id)

        // Then
        found.shouldNotBeNull()
        found.id shouldBe order.id
    }
}

@SpringBootTest
class UpbitQuotationGatewayIntegrationTest {
    @Autowired
    lateinit var gateway: UpbitQuotationGateway

    @Test
    fun `현재가를 조회할 수 있다`() = runTest {
        // When
        val result = either { gateway.getTicker(listOf(TradingPair("KRW-BTC"))) }

        // Then
        result.shouldBeRight()
        result.getOrNull()!!.size shouldBe 1
    }
}
```

### 7.3 WireMock을 이용한 API 테스트

```kotlin
@SpringBootTest
@AutoConfigureWireMock(port = 0)
class UpbitRestClientWireMockTest {
    @Autowired
    lateinit var client: UpbitRestClient

    @Test
    fun `401 에러가 AuthenticationError로 변환된다`() = runTest {
        // Given
        stubFor(get(urlPathEqualTo("/v1/ticker"))
            .willReturn(aResponse()
                .withStatus(401)
                .withBody("""{"error":{"name":"jwt_verification","message":"Invalid token"}}""")))

        // When
        val result = either { client.getPublic<List<UpbitTickerResponse>>("/v1/ticker") }

        // Then
        result.shouldBeLeft()
        val error = result.leftOrNull()
        error.shouldBeInstanceOf<GatewayError.AuthenticationError>()
        (error as GatewayError.AuthenticationError).code shouldBe "jwt_verification"
    }

    @Test
    fun `429 에러가 RateLimitError로 변환된다`() = runTest {
        // Given
        stubFor(get(urlPathEqualTo("/v1/ticker"))
            .willReturn(aResponse()
                .withStatus(429)
                .withBody("""{"error":{"name":"too_many_requests","message":"Too many requests"}}""")))

        // When
        val result = either { client.getPublic<List<UpbitTickerResponse>>("/v1/ticker") }

        // Then
        result.shouldBeLeft()
        result.leftOrNull().shouldBeInstanceOf<GatewayError.RateLimitError>()
    }

    @Test
    fun `500 에러가 NetworkError로 변환된다`() = runTest {
        // Given
        stubFor(get(urlPathEqualTo("/v1/ticker"))
            .willReturn(aResponse()
                .withStatus(500)
                .withBody("""{"error":{"name":"internal_error","message":"Internal server error"}}""")))

        // When
        val result = either { client.getPublic<List<UpbitTickerResponse>>("/v1/ticker") }

        // Then
        result.shouldBeLeft()
        result.leftOrNull().shouldBeInstanceOf<GatewayError.NetworkError>()
    }

    @Test
    fun `400 에러가 ApiError로 변환된다`() = runTest {
        // Given
        stubFor(get(urlPathEqualTo("/v1/ticker"))
            .willReturn(aResponse()
                .withStatus(400)
                .withBody("""{"error":{"name":"invalid_parameter","message":"Invalid market"}}""")))

        // When
        val result = either { client.getPublic<List<UpbitTickerResponse>>("/v1/ticker") }

        // Then
        result.shouldBeLeft()
        val error = result.leftOrNull()
        error.shouldBeInstanceOf<GatewayError.ApiError>()
        (error as GatewayError.ApiError).code shouldBe "invalid_parameter"
    }
}
```

---

## 8. 설정

### 8.1 application.yml

```yaml
upbit:
  api:
    base-url: https://api.upbit.com
    access-key: ${UPBIT_ACCESS_KEY}
    secret-key: ${UPBIT_SECRET_KEY}
  websocket:
    url: wss://api.upbit.com/websocket/v1
    private-url: wss://api.upbit.com/websocket/v1/private
    ping-interval: 60s
    reconnect-delay: 5s
    max-reconnect-attempts: 10

spring:
  r2dbc:
    url: r2dbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:cryptoquant}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
    pool:
      initial-size: 5
      max-size: 20
      max-idle-time: 30m
```

### 8.2 Gradle 의존성

```kotlin
// infrastructure/build.gradle.kts
dependencies {
    // Spring WebFlux
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // R2DBC PostgreSQL
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.postgresql:r2dbc-postgresql")

    // JSON (kotlinx.serialization + Spring WebFlux 통합)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")
    implementation("org.springframework.boot:spring-boot-starter-json")

    // JWT
    implementation("com.auth0:java-jwt:4.4.0")

    // Rate Limiter
    implementation("com.bucket4j:bucket4j-core:8.7.0")

    // WebSocket
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Test
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.wiremock:wiremock-standalone:3.3.1")
}
```

### 8.3 UpbitClientConfig

```kotlin
/**
 * Upbit API 클라이언트 설정.
 *
 * - kotlinx.serialization 코덱 설정
 * - WebClient 기본 설정
 */
@Configuration
class UpbitClientConfig(
    private val properties: UpbitProperties,
) {
    private val json = Json {
        ignoreUnknownKeys = true      // API 응답에 알 수 없는 필드 무시
        isLenient = true              // 유연한 파싱 허용
        coerceInputValues = true      // null을 기본값으로 변환
        explicitNulls = false         // null 필드 직렬화 생략
    }

    @Bean
    fun upbitWebClient(): WebClient = WebClient.builder()
        .baseUrl(properties.api.baseUrl)
        .codecs { configurer ->
            configurer.defaultCodecs().maxInMemorySize(1024 * 1024) // 1MB
            configurer.defaultCodecs().kotlinSerializationJsonDecoder(
                KotlinSerializationJsonDecoder(json)
            )
            configurer.defaultCodecs().kotlinSerializationJsonEncoder(
                KotlinSerializationJsonEncoder(json)
            )
        }
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .build()

    @Bean
    fun upbitAuthInterceptor(): UpbitAuthInterceptor = UpbitAuthInterceptor(
        accessKey = properties.api.accessKey,
        secretKey = properties.api.secretKey,
    )

    @Bean
    fun upbitRateLimiter(): UpbitRateLimiter = UpbitRateLimiter()
}

/**
 * Upbit 설정 프로퍼티.
 */
@ConfigurationProperties(prefix = "upbit")
data class UpbitProperties(
    val api: ApiProperties,
    val websocket: WebSocketProperties,
) {
    data class ApiProperties(
        val baseUrl: String,
        val accessKey: String,
        val secretKey: String,
    )

    data class WebSocketProperties(
        val url: String,
        val privateUrl: String,
        val pingInterval: Duration,
        val reconnectDelay: Duration,
        val maxReconnectAttempts: Int,
    )
}
```
