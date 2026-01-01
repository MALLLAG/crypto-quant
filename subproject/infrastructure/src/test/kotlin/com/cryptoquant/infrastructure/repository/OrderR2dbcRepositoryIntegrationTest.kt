package com.cryptoquant.infrastructure.repository

import arrow.core.raise.context.either
import com.cryptoquant.domain.common.Amount
import com.cryptoquant.domain.common.DomainError
import com.cryptoquant.domain.common.Price
import com.cryptoquant.domain.common.TradingPair
import com.cryptoquant.domain.common.Volume
import com.cryptoquant.domain.gateway.PageRequest
import com.cryptoquant.domain.order.Order
import com.cryptoquant.domain.order.OrderError
import com.cryptoquant.domain.order.OrderId
import com.cryptoquant.domain.order.OrderSide
import com.cryptoquant.domain.order.OrderState
import com.cryptoquant.domain.order.OrderType
import com.cryptoquant.infrastructure.IntegrationTestBase
import com.cryptoquant.infrastructure.config.R2dbcConfig
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest
import org.springframework.context.annotation.Import
import org.springframework.r2dbc.core.DatabaseClient
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * OrderRepository R2DBC 통합 테스트.
 *
 * 이 테스트는 PostgreSQL TestContainer를 사용합니다.
 * Docker가 실행 중이어야 합니다.
 */
@DataR2dbcTest
@Import(R2dbcConfig::class, OrderR2dbcRepository::class, OrderEntityMapper::class)
class OrderR2dbcRepositoryIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var repository: OrderR2dbcRepository

    @Autowired
    private lateinit var databaseClient: DatabaseClient

    @BeforeEach
    fun setup() = runTest {
        // 테이블 생성 (TestContainers는 매번 새로운 DB를 사용)
        databaseClient.sql(
            """
            CREATE TABLE IF NOT EXISTS orders (
                id                  VARCHAR(36) PRIMARY KEY,
                pair                VARCHAR(20) NOT NULL,
                side                VARCHAR(10) NOT NULL,
                order_type          VARCHAR(20) NOT NULL,
                state               VARCHAR(20) NOT NULL,
                volume              DECIMAL(20, 8),
                price               DECIMAL(20, 8),
                total_price         DECIMAL(20, 8),
                remaining_volume    DECIMAL(20, 8) NOT NULL,
                executed_volume     DECIMAL(20, 8) NOT NULL,
                executed_amount     DECIMAL(20, 8) NOT NULL,
                paid_fee            DECIMAL(20, 8) NOT NULL,
                created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
                done_at             TIMESTAMP WITH TIME ZONE
            )
            """.trimIndent()
        ).fetch().rowsUpdated().awaitFirstOrNull()

        // 테스트 데이터 초기화
        databaseClient.sql("DELETE FROM orders").fetch().rowsUpdated().awaitFirstOrNull()
    }

    @Test
    fun `주문을 저장하고 조회할 수 있다`() = runTest {
        // Given
        val order = createTestOrder(
            id = "test-order-001",
            pair = "KRW-BTC",
            side = OrderSide.BID,
            state = OrderState.WAIT,
        )

        // When
        repository.save(order)
        val found = repository.findById(order.id)

        // Then
        found.shouldNotBeNull()
        found.id.value shouldBe order.id.value
        found.pair.value shouldBe order.pair.value
        found.side shouldBe order.side
        found.state shouldBe order.state
    }

    @Test
    fun `존재하지 않는 주문 조회 시 null을 반환한다`() = runTest {
        // Given
        val orderId = either<DomainError, OrderId> { OrderId("non-existent") }.getOrNull()!!

        // When
        val found = repository.findById(orderId)

        // Then
        found.shouldBeNull()
    }

    @Test
    fun `주문을 업데이트할 수 있다`() = runTest {
        // Given
        val order = createTestOrder(
            id = "test-order-002",
            state = OrderState.WAIT,
        )
        repository.save(order)

        // When - 체결 완료 상태로 업데이트
        val updatedOrder = createTestOrder(
            id = "test-order-002",
            state = OrderState.DONE,
            remainingVolume = BigDecimal.ZERO,
            executedVolume = BigDecimal("0.001"),
            doneAt = Instant.now(),
        )
        repository.save(updatedOrder)

        // Then
        val found = repository.findById(order.id)
        found.shouldNotBeNull()
        found.state shouldBe OrderState.DONE
        found.remainingVolume.value.compareTo(BigDecimal.ZERO) shouldBe 0
    }

    @Test
    fun `미체결 주문 목록을 조회할 수 있다`() = runTest {
        // Given - 3개의 미체결 주문, 1개의 완료된 주문
        val waitOrder1 = createTestOrder(id = "order-1", state = OrderState.WAIT)
        val waitOrder2 = createTestOrder(id = "order-2", state = OrderState.WAIT)
        val watchOrder = createTestOrder(id = "order-3", state = OrderState.WATCH)
        val doneOrder = createTestOrder(
            id = "order-4",
            state = OrderState.DONE,
            remainingVolume = BigDecimal.ZERO,
            executedVolume = BigDecimal("0.001"),
            doneAt = Instant.now()
        )

        listOf(waitOrder1, waitOrder2, watchOrder, doneOrder).forEach { repository.save(it) }

        // When
        val result = repository.findOpenOrders()

        // Then - 미체결 주문만 조회됨
        result.items shouldHaveSize 3
        result.items.all { it.isOpen } shouldBe true
    }

    @Test
    fun `마켓별로 미체결 주문을 필터링할 수 있다`() = runTest {
        // Given
        val btcOrder1 = createTestOrder(id = "btc-1", pair = "KRW-BTC", state = OrderState.WAIT)
        val btcOrder2 = createTestOrder(id = "btc-2", pair = "KRW-BTC", state = OrderState.WAIT)
        val ethOrder = createTestOrder(id = "eth-1", pair = "KRW-ETH", state = OrderState.WAIT)

        listOf(btcOrder1, btcOrder2, ethOrder).forEach { repository.save(it) }

        val btcPair = either<DomainError, TradingPair> { TradingPair("KRW-BTC") }.getOrNull()!!

        // When
        val result = repository.findOpenOrders(pair = btcPair)

        // Then
        result.items shouldHaveSize 2
        result.items.all { it.pair.value == "KRW-BTC" } shouldBe true
    }

    @Test
    fun `페이지네이션이 동작한다`() = runTest {
        // Given - 5개의 주문 생성
        repeat(5) { i ->
            val order = createTestOrder(
                id = "order-${i.toString().padStart(3, '0')}",
                state = OrderState.WAIT,
                createdAt = Instant.now().minusSeconds(i.toLong() * 60) // 시간 순서대로
            )
            repository.save(order)
        }

        // When - 첫 페이지 (2개)
        val page1 = repository.findOpenOrders(page = PageRequest(limit = 2))

        // Then
        page1.items shouldHaveSize 2
        page1.nextCursor.shouldNotBeNull()

        // When - 두 번째 페이지
        val page2 = repository.findOpenOrders(page = PageRequest(limit = 2, cursor = page1.nextCursor))

        // Then
        page2.items shouldHaveSize 2
        page2.nextCursor.shouldNotBeNull()

        // When - 마지막 페이지
        val page3 = repository.findOpenOrders(page = PageRequest(limit = 2, cursor = page2.nextCursor))

        // Then
        page3.items shouldHaveSize 1
        page3.nextCursor.shouldBeNull() // 더 이상 페이지 없음
    }

    @Test
    fun `미체결 주문이 없으면 빈 목록을 반환한다`() = runTest {
        // When
        val result = repository.findOpenOrders()

        // Then
        result.items.shouldBeEmpty()
        result.nextCursor.shouldBeNull()
    }

    // ===== Test Fixtures =====

    private fun createTestOrder(
        id: String = UUID.randomUUID().toString(),
        pair: String = "KRW-BTC",
        side: OrderSide = OrderSide.BID,
        state: OrderState = OrderState.WAIT,
        volume: BigDecimal = BigDecimal("0.001"),
        price: BigDecimal = BigDecimal("50000000"),
        remainingVolume: BigDecimal = volume,
        executedVolume: BigDecimal = BigDecimal.ZERO,
        executedAmount: BigDecimal = BigDecimal.ZERO,
        paidFee: BigDecimal = BigDecimal.ZERO,
        createdAt: Instant = Instant.now(),
        doneAt: Instant? = null,
    ): Order {
        val orderId = either<DomainError, OrderId> { OrderId(id) }.getOrNull()!!
        val tradingPair = either<DomainError, TradingPair> { TradingPair(pair) }.getOrNull()!!
        val orderVolume = either<DomainError, Volume> { Volume(volume) }.getOrNull()!!
        val orderPrice = either<DomainError, Price> { Price(price) }.getOrNull()!!
        val remaining = either<DomainError, Volume> { Volume(remainingVolume) }.getOrNull()!!
        val executed = either<DomainError, Volume> { Volume(executedVolume) }.getOrNull()!!
        val amount = either<DomainError, Amount> { Amount(executedAmount) }.getOrNull()!!
        val fee = either<DomainError, Amount> { Amount(paidFee) }.getOrNull()!!
        val orderType = either<OrderError, OrderType> { OrderType.Limit(orderVolume, orderPrice) }.getOrNull()!!

        return either<OrderError, Order> {
            Order(
                id = orderId,
                pair = tradingPair,
                side = side,
                orderType = orderType,
                state = state,
                remainingVolume = remaining,
                executedVolume = executed,
                executedAmount = amount,
                paidFee = fee,
                createdAt = createdAt,
                doneAt = doneAt,
            )
        }.getOrNull()!!
    }
}
