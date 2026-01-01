# Crypto Quant - 가상자산 자동매매 프로젝트

## 프로젝트 개요
Crypto Quant는 함수형 도메인 주도 설계(Functional DDD) 원칙을 기반으로 한 가상자산 자동매매 프로그램입니다.

## 기술 스택
- **Language**: Kotlin 2.2.20
- **FP Library**: Kotlin Arrow 2.2.1.1 (Context Parameters API)
- **Web Framework**: Spring WebFlux (Reactive)
- **Database**: PostgreSQL with R2DBC

## 프로젝트 구조
```
crypto-quant/
├── subproject/
│   ├── presentation/     # API 엔드포인트, 컨트롤러, DTO
│   ├── application/      # 유스케이스, 명령 핸들러, 작업 흐름 조합
│   ├── domain/           # 도메인 모델, 비즈니스 로직, 순수 함수
│   └── infrastructure/   # 외부 서비스 연동, DB 접근, 영속화
```

---

## 함수형 도메인 주도 설계 원칙

### 1. 타입으로 도메인 모델링하기

#### 단순값은 래퍼 타입으로 모델링
원시 타입을 직접 사용하지 말고, 도메인 의미를 담은 래퍼 타입을 생성합니다.

**래퍼 타입 선택 기준:**

| 필드 개수 | 사용할 타입 | 이유 |
|-----------|-------------|------|
| 1개 | `@JvmInline value class` | 런타임 오버헤드 없음 (힙 할당 없음) |
| 2개 이상 | `data class` | value class는 단일 필드만 허용 |

```kotlin
// 필드 1개: value class 사용
@JvmInline
value class OrderId(val value: String)

@JvmInline
value class Price(val value: BigDecimal)

@JvmInline
value class Quantity(val value: BigDecimal)

// 필드 2개 이상: data class 사용
data class Pair private constructor(
    val market: Market,
    val ticker: String,
) {
    val value: String get() = "${market.name}-$ticker"  // 파생 속성
}

// BAD: 원시 타입 직접 사용
fun placeOrder(orderId: String, price: BigDecimal) // 타입 안정성 없음

// BAD: 파생 속성에서 매번 파싱하는 value class
@JvmInline
value class Pair(val value: String) {
    val market: Market get() = Market.valueOf(value.substringBefore("-"))  // 매번 파싱!
    val ticker: String get() = value.substringAfter("-")  // 매번 파싱!
}
```

> **주의**: value class에서 파생 속성이 문자열 파싱 등 비용이 드는 연산을 수행하면,
> 접근할 때마다 오버헤드가 발생합니다. 이런 경우 data class를 사용하세요.

#### 스마트 생성자로 제약 조건 강제
생성 시점에 비즈니스 규칙을 검증하여 유효하지 않은 값이 생성되지 않도록 합니다.

```kotlin
@JvmInline
value class Quantity private constructor(val value: BigDecimal) {
    companion object {
        context(_: Raise<DomainError>)
        operator fun invoke(value: BigDecimal): Quantity {
            ensure(value > BigDecimal.ZERO) { InvalidQuantity("수량은 0보다 커야 합니다") }
            ensure(value.scale() <= 8) { InvalidQuantity("소수점 8자리까지만 허용됩니다") }
            return Quantity(value)
        }
    }
}
```

#### 선택 타입으로 OR 관계 모델링
sealed interface를 사용하여 도메인의 선택지를 명시적으로 모델링합니다.

```kotlin
sealed interface OrderType {
    data class Market(val quantity: Quantity) : OrderType
    data class Limit(val quantity: Quantity, val price: Price) : OrderType
    data class StopLoss(val quantity: Quantity, val stopPrice: Price) : OrderType
}

sealed interface OrderStatus {
    object Pending : OrderStatus
    object Filled : OrderStatus
    object PartiallyFilled : OrderStatus
    object Cancelled : OrderStatus
}
```

### 2. 상태 전이를 타입으로 표현

각 상태별로 별도의 타입을 정의하여 잘못된 상태 전이를 컴파일 타임에 방지합니다.

```kotlin
// 주문 생명주기를 타입으로 표현
data class UnvalidatedOrder(
    val symbol: String,
    val side: String,
    val quantity: String,
    val price: String?,
)

data class ValidatedOrder(
    val orderId: OrderId,
    val symbol: TradingPair,
    val side: OrderSide,
    val orderType: OrderType,
)

data class ExecutedOrder(
    val orderId: OrderId,
    val symbol: TradingPair,
    val side: OrderSide,
    val orderType: OrderType,
    val executedAt: Instant,
    val executedPrice: Price,
    val fee: Fee,
)
```

### 3. 파이프라인으로 작업 흐름 모델링

비즈니스 프로세스를 순수 함수들의 파이프라인으로 구성합니다.

```kotlin
// 작업 흐름을 함수 타입으로 정의
typealias ValidateOrder =
    suspend UnvalidatedOrder.(CheckSymbolExists, GetCurrentPrice)
    -> Either<ValidationError, ValidatedOrder>

typealias ExecuteOrder =
    suspend ValidatedOrder.(ExchangeClient)
    -> Either<ExecutionError, ExecutedOrder>

typealias CreateOrderEvents =
    ExecutedOrder.() -> List<OrderEvent>

// 파이프라인 조합
context(_: ExchangeGateway)
suspend fun UnvalidatedOrder.placeOrder(
    checkSymbol: CheckSymbolExists,
    getPrice: GetCurrentPrice,
    executeOrder: ExecuteOrder,
): Either<PlaceOrderError, List<OrderEvent>> = either {
    val validated = this@placeOrder.validateOrder(checkSymbol, getPrice).bind()
    val executed = validated.executeOrder(exchangeClient).bind()
    executed.createOrderEvents()
}
```

### 4. Either/Raise로 오류 효과 명시

함수가 실패할 수 있음을 타입 시그니처에 명시적으로 드러냅니다.

```kotlin
// 도메인 오류 정의
sealed interface PlaceOrderError {
    data class ValidationError(val message: String) : PlaceOrderError
    data class InsufficientBalance(val required: BigDecimal, val available: BigDecimal) : PlaceOrderError
    data class ExchangeError(val code: String, val message: String) : PlaceOrderError
}

// Either를 반환하는 함수
suspend fun placeOrder(order: UnvalidatedOrder): Either<PlaceOrderError, ExecutedOrder>

// Raise 컨텍스트 사용 (권장)
context(_: Raise<PlaceOrderError>)
suspend fun UnvalidatedOrder.placeOrder(): ExecutedOrder
```

### 5. 영속화 코드를 가장자리로 밀어내기

도메인 로직은 순수 함수로 유지하고, I/O는 경계에서 처리합니다.

```kotlin
// domain 모듈: 순수 함수만 존재
fun Order.calculateFee(feeRate: FeeRate): Fee {
    return Fee(this.executedPrice.value * feeRate.value)
}

fun Order.applyFee(fee: Fee): OrderWithFee {
    return OrderWithFee(this, fee)
}

// application 모듈: I/O와 순수 로직 조합
suspend fun processOrder(orderId: OrderId) {
    // I/O: 데이터 로드
    val order = orderRepository.findById(orderId)
    val feeRate = feeService.getCurrentRate()

    // 순수: 비즈니스 로직
    val fee = order.calculateFee(feeRate)
    val orderWithFee = order.applyFee(fee)

    // I/O: 결과 저장
    orderRepository.save(orderWithFee)
}
```

### 6. 명령-질의 책임 분리 (CQRS)

쓰기와 읽기를 분리하여 각각 최적화합니다.

```kotlin
// 명령 (Command) - 상태 변경, 반환값 없음
interface OrderCommandRepository {
    suspend fun save(order: Order): Unit
    suspend fun updateStatus(orderId: OrderId, status: OrderStatus): Unit
}

// 질의 (Query) - 상태 조회, 반환값 있음
interface OrderQueryRepository {
    suspend fun findById(orderId: OrderId): Order?
    suspend fun findBySymbol(symbol: TradingPair): List<OrderSummary>
    suspend fun getOpenOrders(): List<OpenOrderView>
}
```

---

## 모듈별 책임

### Domain 모듈
- 순수한 도메인 모델 정의 (엔터티, 값 객체, 집합체)
- 비즈니스 규칙을 타입으로 표현
- 외부 의존성 없음 (Arrow Core만 허용)
- 모든 함수는 순수 함수
- I/O, 영속화, 외부 서비스 호출 금지

```kotlin
// domain 모듈 예시
package com.cryptoquant.domain.order

@JvmInline
value class OrderId(val value: String)

sealed interface Order {
    val orderId: OrderId
    val symbol: TradingPair
    val side: OrderSide
}

data class PendingOrder(...) : Order
data class FilledOrder(...) : Order
```

### Application 모듈
- 유스케이스 구현
- 작업 흐름 조합
- 트랜잭션 관리
- 도메인 이벤트 발행
- Domain 모듈만 의존

```kotlin
// application 모듈 예시
package com.cryptoquant.application.order

class PlaceOrderUseCase(
    private val orderRepository: OrderRepository,
    private val exchangeGateway: ExchangeGateway,
) {
    context(_: Raise<PlaceOrderError>)
    suspend fun execute(command: PlaceOrderCommand): OrderPlaced {
        val validated = command.toUnvalidatedOrder().validate()
        val executed = exchangeGateway.execute(validated)
        orderRepository.save(executed)
        return OrderPlaced(executed.orderId)
    }
}
```

### Infrastructure 모듈
- 외부 서비스 연동 구현
- 데이터베이스 접근 (R2DBC)
- 거래소 API 클라이언트
- 메시지 큐 연동
- Application의 인터페이스 구현

```kotlin
// infrastructure 모듈 예시
package com.cryptoquant.infrastructure.exchange

class BinanceExchangeGateway(
    private val webClient: WebClient,
) : ExchangeGateway {
    override suspend fun execute(order: ValidatedOrder): Either<ExchangeError, ExecutedOrder> =
        either {
            val response = webClient.post()
                .uri("/api/v3/order")
                .bodyValue(order.toRequest())
                .retrieve()
                .awaitBody<BinanceOrderResponse>()
            response.toExecutedOrder()
        }
}
```

### Presentation 모듈
- REST API 엔드포인트
- WebSocket 핸들러
- 요청/응답 DTO
- 입력 검증
- 오류 응답 매핑

```kotlin
// presentation 모듈 예시
package com.cryptoquant.presentation.order

@RestController
@RequestMapping("/api/orders")
class OrderController(
    private val placeOrderUseCase: PlaceOrderUseCase,
) {
    @PostMapping
    suspend fun placeOrder(@RequestBody request: PlaceOrderRequest): ResponseEntity<*> =
        either {
            val command = request.toCommand()
            placeOrderUseCase.execute(command)
        }.fold(
            { error -> ResponseEntity.badRequest().body(error.toResponse()) },
            { result -> ResponseEntity.ok(result.toResponse()) }
        )
}
```

---

## 코딩 컨벤션

### Arrow 사용 패턴 (Context Parameters API)

이 프로젝트는 Arrow 2.2의 Context Parameters 기반 API (`arrow.core.raise.context`)를 사용합니다.

```kotlin
import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.context.bind
import arrow.core.raise.context.either
import arrow.core.raise.context.ensure
import arrow.core.raise.context.raise
import arrow.core.raise.context.withError

// Raise 컨텍스트 사용 (권장)
context(_: Raise<DomainError>)
fun String.toOrderId(): OrderId {
    ensure(this.isNotBlank()) { InvalidOrderId("주문 ID는 비어있을 수 없습니다") }
    return OrderId(this)
}

// Either 빌더로 조합
suspend fun processOrder(): Either<Error, Result> = either {
    val step1 = operation1().bind()
    val step2 = operation2(step1).bind()
    val step3 = operation3(step2).bind()
    step3
}

// 에러 변환 (withError)
suspend fun execute(): Either<UseCaseError, Result> = either {
    val value = withError<UseCaseError, DomainError, Value>(
        { UseCaseError.DomainError(it) }
    ) {
        domainOperation()
    }
    Result(value)
}
```

### 네이밍 컨벤션
- 도메인 타입: `OrderId`, `TradingPair`, `Quantity`
- 작업 흐름: `ValidateOrder`, `ExecuteOrder`, `CreateEvents`
- 유스케이스: `PlaceOrderUseCase`, `CancelOrderUseCase`
- 오류 타입: `ValidationError`, `InsufficientBalance`
- 이벤트: `OrderPlaced`, `OrderFilled`, `OrderCancelled`

### 함수 시그니처 패턴

```kotlin
// 의존성을 컨텍스트로 주입
context(_: ExchangeGateway, _: Raise<OrderError>)
suspend fun ValidatedOrder.execute(): ExecutedOrder

// 또는 명시적 매개변수로 전달
suspend fun ValidatedOrder.execute(
    gateway: ExchangeGateway
): Either<OrderError, ExecutedOrder>
```

---

## 코드 품질 도구

### Detekt (정적 분석)

Detekt는 Kotlin 코드의 정적 분석 도구입니다. 이 프로젝트에서는 Detekt 2.0 (Kotlin 2.2 호환)을 사용하며, 최소 규칙 세트를 활성화해 기본적인 오류와 코루틴 이슈를 잡습니다.

#### 실행 방법

```bash
# 전체 프로젝트 분석
./gradlew detekt

# 특정 모듈만 분석
./gradlew :subproject:domain:detekt
```

### Ktlint (코드 포맷터)

Ktlint는 Kotlin 코드 린터 및 포맷터입니다. `.editorconfig`를 통해 규칙을 관리하며, 핵심 규칙만 활성화해 스타일 안정성을 유지합니다.

#### 실행 방법

```bash
# 린트 검사
./gradlew ktlintCheck

# 자동 포맷팅
./gradlew ktlintFormat
```

#### EditorConfig 설정

프로젝트 루트의 `.editorconfig`에서 규칙을 관리합니다. 핵심 규칙만 활성화되어 있으며 기본적인 스타일만 적용됩니다.

```ini
# .editorconfig 주요 설정
[*.{kt,kts}]
indent_size = 4
indent_style = space
insert_final_newline = true
```

### CI/CD 통합

코드 품질 검사는 빌드 파이프라인에서 자동으로 실행됩니다.

```bash
# 전체 품질 검사 (린트 + 정적 분석 + 테스트)
./gradlew check
```

- `ktlintCheck`: 코드 스타일 검사
- `detekt`: 정적 분석
- `test`: 단위 테스트

---

## 테스트 가이드

### 순수 함수 테스트
도메인 로직은 I/O가 없으므로 단순한 단위 테스트로 검증합니다.

```kotlin
class OrderTest {
    @Test
    fun `수량이 0 이하면 생성에 실패한다`() = runTest {
        val result = either<DomainError, Quantity> { Quantity(BigDecimal.ZERO) }
        result.shouldBeLeft()
    }

    @Test
    fun `주문 수수료가 올바르게 계산된다`() {
        val order = createTestOrder(price = Price(BigDecimal("100")))
        val fee = order.calculateFee(FeeRate(BigDecimal("0.001")))
        fee.value shouldBe BigDecimal("0.1")
    }
}
```

### 작업 흐름 테스트
의존성을 모킹하여 파이프라인 전체를 테스트합니다.

```kotlin
class PlaceOrderUseCaseTest {
    @Test
    fun `유효한 주문이 성공적으로 체결된다`() = runTest {
        val mockGateway = mockk<ExchangeGateway>()
        coEvery { mockGateway.execute(any()) } returns ExecutedOrder(...).right()

        val result = either<PlaceOrderError, ExecutedOrder> {
            unvalidatedOrder.placeOrder(mockGateway)
        }

        result.shouldBeRight()
    }
}
```

### 통합 테스트 (TestContainers)
실제 인프라스트럭처와의 통합을 TestContainers로 검증합니다.

#### 테스트 컨테이너 설정

```kotlin
// 공통 테스트 인프라 설정
abstract class IntegrationTestBase {

    companion object {
        @Container
        @JvmStatic
        val postgresContainer = PostgreSQLContainer<Nothing>("postgres:15-alpine").apply {
            withDatabaseName("cryptoquant_test")
            withUsername("test")
            withPassword("test")
            withReuse(true)
        }

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.r2dbc.url") {
                "r2dbc:postgresql://${postgresContainer.host}:${postgresContainer.firstMappedPort}/${postgresContainer.databaseName}"
            }
            registry.add("spring.r2dbc.username", postgresContainer::getUsername)
            registry.add("spring.r2dbc.password", postgresContainer::getPassword)
        }
    }
}
```

#### Repository 통합 테스트

```kotlin
@DataR2dbcTest
@Testcontainers
@Import(OrderR2dbcRepository::class)
class OrderRepositoryIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Autowired
    private lateinit var databaseClient: DatabaseClient

    @BeforeEach
    fun setup() = runBlocking {
        // 테스트 데이터 초기화
        databaseClient.sql("DELETE FROM orders").await()
    }

    @Test
    fun `주문을 저장하고 조회할 수 있다`() = runTest {
        // Given
        val order = createTestOrder(
            orderId = OrderId("test-order-001"),
            symbol = TradingPair("BTC", "USDT"),
            side = OrderSide.BUY,
            quantity = Quantity(BigDecimal("0.5")),
        )

        // When
        orderRepository.save(order)
        val found = orderRepository.findById(order.orderId)

        // Then
        found.shouldNotBeNull()
        found.orderId shouldBe order.orderId
        found.symbol shouldBe order.symbol
    }

    @Test
    fun `심볼별 미체결 주문 목록을 조회할 수 있다`() = runTest {
        // Given
        val btcOrder1 = createTestOrder(symbol = TradingPair("BTC", "USDT"), status = OrderStatus.Pending)
        val btcOrder2 = createTestOrder(symbol = TradingPair("BTC", "USDT"), status = OrderStatus.Pending)
        val ethOrder = createTestOrder(symbol = TradingPair("ETH", "USDT"), status = OrderStatus.Pending)

        listOf(btcOrder1, btcOrder2, ethOrder).forEach { orderRepository.save(it) }

        // When
        val btcOrders = orderRepository.findOpenOrdersBySymbol(TradingPair("BTC", "USDT"))

        // Then
        btcOrders.size shouldBe 2
        btcOrders.all { it.symbol == TradingPair("BTC", "USDT") } shouldBe true
    }
}
```

#### API 통합 테스트

```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class OrderApiIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @BeforeEach
    fun setup() = runBlocking {
        orderRepository.deleteAll()
    }

    @Test
    fun `POST api_orders - 유효한 주문 요청이 성공한다`() {
        // Given
        val request = PlaceOrderRequest(
            symbol = "BTCUSDT",
            side = "BUY",
            type = "LIMIT",
            quantity = "0.001",
            price = "50000.00",
        )

        // When & Then
        webTestClient.post()
            .uri("/api/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.orderId").isNotEmpty
            .jsonPath("$.status").isEqualTo("PENDING")
    }

    @Test
    fun `POST api_orders - 잘못된 수량이면 400 에러를 반환한다`() {
        // Given
        val request = PlaceOrderRequest(
            symbol = "BTCUSDT",
            side = "BUY",
            type = "LIMIT",
            quantity = "-1",  // 잘못된 수량
            price = "50000.00",
        )

        // When & Then
        webTestClient.post()
            .uri("/api/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("VALIDATION_ERROR")
    }

    @Test
    fun `GET api_orders_{id} - 존재하는 주문을 조회한다`() = runTest {
        // Given
        val order = createTestOrder()
        orderRepository.save(order)

        // When & Then
        webTestClient.get()
            .uri("/api/orders/${order.orderId.value}")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.orderId").isEqualTo(order.orderId.value)
    }
}
```

#### 유스케이스 통합 테스트

```kotlin
@SpringBootTest
@Testcontainers
class PlaceOrderUseCaseIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var placeOrderUseCase: PlaceOrderUseCase

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @MockkBean
    private lateinit var exchangeGateway: ExchangeGateway

    @BeforeEach
    fun setup() = runBlocking {
        orderRepository.deleteAll()
    }

    @Test
    fun `주문이 체결되면 DB에 저장된다`() = runTest {
        // Given
        val command = PlaceOrderCommand(
            symbol = "BTCUSDT",
            side = OrderSide.BUY,
            orderType = OrderType.Market(Quantity(BigDecimal("0.001"))),
        )

        coEvery { exchangeGateway.execute(any()) } returns ExecutedOrder(
            orderId = OrderId("exchange-order-123"),
            executedPrice = Price(BigDecimal("50000")),
            executedAt = Instant.now(),
            fee = Fee(BigDecimal("0.05")),
        ).right()

        // When
        val result = placeOrderUseCase.execute(command)

        // Then
        result.shouldBeRight()
        val savedOrder = orderRepository.findById(result.getOrNull()!!.orderId)
        savedOrder.shouldNotBeNull()
        savedOrder.status shouldBe OrderStatus.Filled
    }

    @Test
    fun `거래소 오류 시 주문이 저장되지 않는다`() = runTest {
        // Given
        val command = PlaceOrderCommand(
            symbol = "BTCUSDT",
            side = OrderSide.BUY,
            orderType = OrderType.Market(Quantity(BigDecimal("0.001"))),
        )

        coEvery { exchangeGateway.execute(any()) } returns
            ExchangeError("INSUFFICIENT_BALANCE", "잔고 부족").left()

        // When
        val result = placeOrderUseCase.execute(command)

        // Then
        result.shouldBeLeft()
        orderRepository.findAll().toList().shouldBeEmpty()
    }
}
```

#### 테스트 유틸리티

```kotlin
// 테스트 픽스처 생성 헬퍼
object TestFixtures {
    fun createTestOrder(
        orderId: OrderId = OrderId(UUID.randomUUID().toString()),
        symbol: TradingPair = TradingPair("BTC", "USDT"),
        side: OrderSide = OrderSide.BUY,
        quantity: Quantity = Quantity(BigDecimal("0.001")),
        price: Price = Price(BigDecimal("50000")),
        status: OrderStatus = OrderStatus.Pending,
    ): Order = PendingOrder(
        orderId = orderId,
        symbol = symbol,
        side = side,
        orderType = OrderType.Limit(quantity, price),
        createdAt = Instant.now(),
    )
}

// R2DBC 트랜잭션 롤백을 위한 확장
@Transactional
annotation class IntegrationTest
```

#### 테스트 계층 구조

```
test/
├── unit/                    # 순수 함수 단위 테스트
│   └── domain/
│       ├── OrderTest.kt
│       └── QuantityTest.kt
├── integration/             # TestContainers 통합 테스트
│   ├── IntegrationTestBase.kt
│   ├── repository/
│   │   └── OrderRepositoryIntegrationTest.kt
│   ├── usecase/
│   │   └── PlaceOrderUseCaseIntegrationTest.kt
│   └── api/
│       └── OrderApiIntegrationTest.kt
└── fixtures/
    └── TestFixtures.kt
```

---

## 참고 사항

### 금지 사항
- Domain 모듈에서 Spring, 데이터베이스 의존성 사용
- 도메인 로직 내 I/O 수행
- 예외를 오류 처리에 사용 (Either/Raise 사용)
- 가변 상태 사용 (불변 데이터 구조 사용)

### 권장 사항
- 모든 도메인 개념은 명시적 타입으로 모델링
- 비즈니스 규칙은 스마트 생성자로 강제
- 함수 시그니처에 모든 효과 명시
- 작은 순수 함수들을 조합하여 큰 작업 흐름 구성
- 테스트 용이성을 위해 의존성 주입 활용
