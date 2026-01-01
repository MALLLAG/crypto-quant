package com.cryptoquant.infrastructure.upbit.gateway

import arrow.core.raise.context.either
import com.cryptoquant.domain.common.Amount
import com.cryptoquant.domain.common.DomainError
import com.cryptoquant.domain.common.Price
import com.cryptoquant.domain.common.TradingPair
import com.cryptoquant.domain.common.Volume
import com.cryptoquant.domain.gateway.GatewayError
import com.cryptoquant.domain.order.OrderError
import com.cryptoquant.domain.order.OrderId
import com.cryptoquant.domain.order.OrderSide
import com.cryptoquant.domain.order.OrderState
import com.cryptoquant.domain.order.OrderType
import com.cryptoquant.domain.order.ValidatedOrderRequest
import com.cryptoquant.infrastructure.upbit.client.UpbitAuthInterceptor
import com.cryptoquant.infrastructure.upbit.client.UpbitRateLimiter
import com.cryptoquant.infrastructure.upbit.client.UpbitRestClient
import com.cryptoquant.infrastructure.upbit.mapper.UpbitDomainMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.codec.json.KotlinSerializationJsonDecoder
import org.springframework.http.codec.json.KotlinSerializationJsonEncoder
import org.springframework.web.reactive.function.client.WebClient
import java.math.BigDecimal

class UpbitExchangeGatewayWireMockTest {

    private lateinit var wireMockServer: WireMockServer
    private lateinit var gateway: UpbitExchangeGateway

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @BeforeEach
    fun setup() {
        wireMockServer = WireMockServer(wireMockConfig().dynamicPort())
        wireMockServer.start()

        val webClient = WebClient.builder()
            .baseUrl("http://localhost:${wireMockServer.port()}")
            .codecs { configurer ->
                configurer.defaultCodecs().maxInMemorySize(1024 * 1024)
                configurer.defaultCodecs().kotlinSerializationJsonDecoder(
                    KotlinSerializationJsonDecoder(json)
                )
                configurer.defaultCodecs().kotlinSerializationJsonEncoder(
                    KotlinSerializationJsonEncoder(json)
                )
            }
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build()

        val authInterceptor = UpbitAuthInterceptor("test-access-key", "test-secret-key")
        val rateLimiter = UpbitRateLimiter()
        val client = UpbitRestClient(webClient, authInterceptor, rateLimiter, json)
        val mapper = UpbitDomainMapper()

        gateway = UpbitExchangeGateway(client, mapper)
    }

    @AfterEach
    fun tearDown() {
        wireMockServer.stop()
    }

    @Test
    fun `주문을 생성할 수 있다`() = runTest {
        // Given
        wireMockServer.stubFor(
            post(urlPathEqualTo("/v1/orders"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            {
                                "uuid": "order-123",
                                "side": "bid",
                                "ord_type": "limit",
                                "price": "50000000",
                                "state": "wait",
                                "market": "KRW-BTC",
                                "created_at": "2024-01-01T00:00:00",
                                "volume": "0.001",
                                "remaining_volume": "0.001",
                                "executed_volume": "0",
                                "trades_count": 0,
                                "paid_fee": "0"
                            }
                            """.trimIndent()
                        )
                )
        )

        val request = createValidatedOrderRequest()

        // When
        val result = either<GatewayError, _> { gateway.placeOrder(request) }

        // Then
        val order = result.shouldBeRight()
        order.id.value shouldBe "order-123"
        order.side shouldBe OrderSide.BID
        order.state shouldBe OrderState.WAIT
    }

    @Test
    fun `주문을 취소할 수 있다`() = runTest {
        // Given
        wireMockServer.stubFor(
            delete(urlPathEqualTo("/v1/order"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            {
                                "uuid": "order-123",
                                "side": "bid",
                                "ord_type": "limit",
                                "price": "50000000",
                                "state": "cancel",
                                "market": "KRW-BTC",
                                "created_at": "2024-01-01T00:00:00",
                                "volume": "0.001",
                                "remaining_volume": "0",
                                "executed_volume": "0.001",
                                "trades_count": 1,
                                "paid_fee": "25"
                            }
                            """.trimIndent()
                        )
                )
        )

        val orderId = either<DomainError, OrderId> { OrderId("order-123") }.getOrNull()!!

        // When
        val result = either<GatewayError, _> { gateway.cancelOrder(orderId) }

        // Then
        val order = result.shouldBeRight()
        order.id.value shouldBe "order-123"
        order.state shouldBe OrderState.CANCEL
    }

    @Test
    fun `주문을 조회할 수 있다`() = runTest {
        // Given
        wireMockServer.stubFor(
            get(urlPathEqualTo("/v1/order"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            {
                                "uuid": "order-123",
                                "side": "bid",
                                "ord_type": "limit",
                                "price": "50000000",
                                "state": "done",
                                "market": "KRW-BTC",
                                "created_at": "2024-01-01T00:00:00",
                                "volume": "0.001",
                                "remaining_volume": "0",
                                "executed_volume": "0.001",
                                "trades_count": 1,
                                "executed_funds": "50000",
                                "paid_fee": "25"
                            }
                            """.trimIndent()
                        )
                )
        )

        val orderId = either<DomainError, OrderId> { OrderId("order-123") }.getOrNull()!!

        // When
        val result = either<GatewayError, _> { gateway.getOrder(orderId) }

        // Then
        val order = result.shouldBeRight()
        order.id.value shouldBe "order-123"
        order.state shouldBe OrderState.DONE
    }

    @Test
    fun `미체결 주문 목록을 조회할 수 있다`() = runTest {
        // Given
        wireMockServer.stubFor(
            get(urlPathEqualTo("/v1/orders/open"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            [
                                {
                                    "uuid": "order-1",
                                    "side": "bid",
                                    "ord_type": "limit",
                                    "price": "50000000",
                                    "state": "wait",
                                    "market": "KRW-BTC",
                                    "created_at": "2024-01-01T00:00:00",
                                    "volume": "0.001",
                                    "remaining_volume": "0.001",
                                    "executed_volume": "0",
                                    "trades_count": 0,
                                    "paid_fee": "0"
                                },
                                {
                                    "uuid": "order-2",
                                    "side": "ask",
                                    "ord_type": "limit",
                                    "price": "51000000",
                                    "state": "wait",
                                    "market": "KRW-BTC",
                                    "created_at": "2024-01-01T00:01:00",
                                    "volume": "0.002",
                                    "remaining_volume": "0.002",
                                    "executed_volume": "0",
                                    "trades_count": 0,
                                    "paid_fee": "0"
                                }
                            ]
                            """.trimIndent()
                        )
                )
        )

        // When
        val result = either<GatewayError, _> { gateway.getOpenOrders() }

        // Then
        val orders = result.shouldBeRight()
        orders.items shouldHaveSize 2
        orders.items.first().id.value shouldBe "order-1"
    }

    @Test
    fun `잔고를 조회할 수 있다`() = runTest {
        // Given
        wireMockServer.stubFor(
            get(urlPathEqualTo("/v1/accounts"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            [
                                {
                                    "currency": "KRW",
                                    "balance": "1000000",
                                    "locked": "0",
                                    "avg_buy_price": 0,
                                    "avg_buy_price_modified": false,
                                    "unit_currency": "KRW"
                                },
                                {
                                    "currency": "BTC",
                                    "balance": "1.5",
                                    "locked": "0.5",
                                    "avg_buy_price": 50000000,
                                    "avg_buy_price_modified": false,
                                    "unit_currency": "KRW"
                                }
                            ]
                            """.trimIndent()
                        )
                )
        )

        // When
        val result = either<GatewayError, _> { gateway.getBalances() }

        // Then
        val balances = result.shouldBeRight()
        balances.shouldNotBeEmpty()
    }

    @Test
    fun `인증 에러가 발생하면 AuthenticationError로 변환된다`() = runTest {
        // Given
        wireMockServer.stubFor(
            get(urlPathEqualTo("/v1/accounts"))
                .willReturn(
                    aResponse()
                        .withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"error":{"name":"jwt_verification","message":"Invalid token"}}""")
                )
        )

        // When
        val result = either<GatewayError, _> { gateway.getBalances() }

        // Then
        val error = result.shouldBeLeft()
        error.shouldBeInstanceOf<GatewayError.AuthenticationError>()
    }

    @Test
    fun `Rate Limit 에러가 발생하면 RateLimitError로 변환된다`() = runTest {
        // Given
        wireMockServer.stubFor(
            get(urlPathEqualTo("/v1/accounts"))
                .willReturn(
                    aResponse()
                        .withStatus(429)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"error":{"name":"too_many_requests","message":"Too many requests"}}""")
                )
        )

        // When
        val result = either<GatewayError, _> { gateway.getBalances() }

        // Then
        val error = result.shouldBeLeft()
        error.shouldBeInstanceOf<GatewayError.RateLimitError>()
    }

    // ===== Test Fixtures =====

    private fun createValidatedOrderRequest(): ValidatedOrderRequest {
        val pair = either<DomainError, TradingPair> { TradingPair("KRW-BTC") }.getOrNull()!!
        val volume = either<DomainError, Volume> { Volume(BigDecimal("0.001")) }.getOrNull()!!
        val price = either<DomainError, Price> { Price(BigDecimal("50000000")) }.getOrNull()!!
        val orderType = either<OrderError, OrderType> { OrderType.Limit(volume, price) }.getOrNull()!!

        return ValidatedOrderRequest(
            pair = pair,
            side = OrderSide.BID,
            orderType = orderType,
        )
    }
}
