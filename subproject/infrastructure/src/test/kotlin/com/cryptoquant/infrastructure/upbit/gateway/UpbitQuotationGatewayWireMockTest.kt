package com.cryptoquant.infrastructure.upbit.gateway

import arrow.core.raise.context.either
import com.cryptoquant.domain.common.DomainError
import com.cryptoquant.domain.common.TradingPair
import com.cryptoquant.domain.gateway.GatewayError
import com.cryptoquant.domain.quotation.CandleUnit
import com.cryptoquant.infrastructure.upbit.client.UpbitAuthInterceptor
import com.cryptoquant.infrastructure.upbit.client.UpbitRateLimiter
import com.cryptoquant.infrastructure.upbit.client.UpbitRestClient
import com.cryptoquant.infrastructure.upbit.mapper.UpbitDomainMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldHaveSize
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

class UpbitQuotationGatewayWireMockTest {

    private lateinit var wireMockServer: WireMockServer
    private lateinit var gateway: UpbitQuotationGateway

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

        gateway = UpbitQuotationGateway(client, mapper)
    }

    @AfterEach
    fun tearDown() {
        wireMockServer.stop()
    }

    @Test
    fun `현재가를 조회할 수 있다`() = runTest {
        // Given
        wireMockServer.stubFor(
            get(urlPathEqualTo("/v1/ticker"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            [
                                {
                                    "market": "KRW-BTC",
                                    "trade_price": 50500000,
                                    "opening_price": 50000000,
                                    "high_price": 51000000,
                                    "low_price": 49000000,
                                    "prev_closing_price": 50000000,
                                    "change": "RISE",
                                    "change_price": 500000,
                                    "signed_change_price": 500000,
                                    "change_rate": 0.01,
                                    "signed_change_rate": 0.01,
                                    "trade_volume": 0.5,
                                    "acc_trade_price_24h": 50000000000,
                                    "acc_trade_volume_24h": 1000,
                                    "timestamp": 1704067200000
                                }
                            ]
                            """.trimIndent()
                        )
                )
        )

        val btcPair = either<DomainError, TradingPair> { TradingPair("KRW-BTC") }.getOrNull()!!

        // When
        val result = either<GatewayError, _> { gateway.getTicker(listOf(btcPair)) }

        // Then
        val tickers = result.shouldBeRight()
        tickers shouldHaveSize 1
        tickers.first().pair.value shouldBe "KRW-BTC"
        tickers.first().tradePrice.value.toInt() shouldBe 50500000
    }

    @Test
    fun `호가를 조회할 수 있다`() = runTest {
        // Given
        wireMockServer.stubFor(
            get(urlPathEqualTo("/v1/orderbook"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            [
                                {
                                    "market": "KRW-BTC",
                                    "timestamp": 1704067200000,
                                    "total_ask_size": 10.5,
                                    "total_bid_size": 12.3,
                                    "orderbook_units": [
                                        {
                                            "ask_price": 50600000,
                                            "bid_price": 50500000,
                                            "ask_size": 1.5,
                                            "bid_size": 2.0
                                        }
                                    ]
                                }
                            ]
                            """.trimIndent()
                        )
                )
        )

        val btcPair = either<DomainError, TradingPair> { TradingPair("KRW-BTC") }.getOrNull()!!

        // When
        val result = either<GatewayError, _> { gateway.getOrderbook(listOf(btcPair)) }

        // Then
        val orderbooks = result.shouldBeRight()
        orderbooks shouldHaveSize 1
        orderbooks.first().pair.value shouldBe "KRW-BTC"
        orderbooks.first().orderbookUnits shouldHaveSize 1
    }

    @Test
    fun `분봉 캔들을 조회할 수 있다`() = runTest {
        // Given
        wireMockServer.stubFor(
            get(urlPathEqualTo("/v1/candles/minutes/1"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            [
                                {
                                    "market": "KRW-BTC",
                                    "candle_date_time_utc": "2024-01-01T00:00:00",
                                    "candle_date_time_kst": "2024-01-01T09:00:00",
                                    "opening_price": 50000000,
                                    "high_price": 51000000,
                                    "low_price": 49000000,
                                    "trade_price": 50500000,
                                    "timestamp": 1704067200000,
                                    "candle_acc_trade_price": 1000000000,
                                    "candle_acc_trade_volume": 20,
                                    "unit": 1
                                }
                            ]
                            """.trimIndent()
                        )
                )
        )

        val btcPair = either<DomainError, TradingPair> { TradingPair("KRW-BTC") }.getOrNull()!!
        val minuteUnit = either<DomainError, CandleUnit> { CandleUnit.Minutes(1) }.getOrNull()!!

        // When
        val result = either<GatewayError, _> { gateway.getCandles(btcPair, minuteUnit, 1) }

        // Then
        val candles = result.shouldBeRight()
        candles shouldHaveSize 1
        candles.first().pair.value shouldBe "KRW-BTC"
    }

    @Test
    fun `체결 내역을 조회할 수 있다`() = runTest {
        // Given
        wireMockServer.stubFor(
            get(urlPathEqualTo("/v1/trades/ticks"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            [
                                {
                                    "market": "KRW-BTC",
                                    "trade_date_utc": "2024-01-01",
                                    "trade_time_utc": "00:00:00",
                                    "timestamp": 1704067200000,
                                    "trade_price": 50500000,
                                    "trade_volume": 0.001,
                                    "prev_closing_price": 50000000,
                                    "change_price": 500000,
                                    "ask_bid": "BID",
                                    "change": "RISE",
                                    "sequential_id": 123456789
                                }
                            ]
                            """.trimIndent()
                        )
                )
        )

        val btcPair = either<DomainError, TradingPair> { TradingPair("KRW-BTC") }.getOrNull()!!

        // When
        val result = either<GatewayError, _> { gateway.getTrades(btcPair, 1) }

        // Then
        val trades = result.shouldBeRight()
        trades shouldHaveSize 1
        trades.first().pair.value shouldBe "KRW-BTC"
    }

    @Test
    fun `API 에러가 발생하면 GatewayError로 변환된다`() = runTest {
        // Given
        wireMockServer.stubFor(
            get(urlPathEqualTo("/v1/ticker"))
                .willReturn(
                    aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"error":{"name":"invalid_parameter","message":"Invalid market"}}""")
                )
        )

        val btcPair = either<DomainError, TradingPair> { TradingPair("KRW-BTC") }.getOrNull()!!

        // When
        val result = either<GatewayError, _> { gateway.getTicker(listOf(btcPair)) }

        // Then
        val error = result.shouldBeLeft()
        error.shouldBeInstanceOf<GatewayError.ApiError>()
        (error as GatewayError.ApiError).code shouldBe "invalid_parameter"
    }
}
