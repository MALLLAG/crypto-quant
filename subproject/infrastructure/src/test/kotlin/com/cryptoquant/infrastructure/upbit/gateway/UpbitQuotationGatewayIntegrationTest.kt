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
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.codec.json.KotlinSerializationJsonDecoder
import org.springframework.http.codec.json.KotlinSerializationJsonEncoder
import org.springframework.web.reactive.function.client.WebClient

/**
 * 실제 Upbit API를 호출하는 통합 테스트.
 *
 * 이 테스트는 기본적으로 비활성화되어 있습니다.
 * 실제 API 테스트가 필요한 경우 @Disabled 어노테이션을 제거하세요.
 *
 * 주의:
 * - 실제 API를 호출하므로 Rate Limit에 주의하세요
 * - CI/CD 환경에서는 실행하지 마세요
 * - API Key가 필요 없는 시세 조회 API만 테스트합니다
 */
@Disabled("실제 API 호출 테스트 - 필요시 수동 실행")
class UpbitQuotationGatewayIntegrationTest {

    private lateinit var gateway: UpbitQuotationGateway

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @BeforeEach
    fun setup() {
        val webClient = WebClient.builder()
            .baseUrl("https://api.upbit.com")
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

        // 시세 조회는 API Key가 필요 없으므로 더미 값 사용
        val authInterceptor = UpbitAuthInterceptor("dummy-access-key", "dummy-secret-key")
        val rateLimiter = UpbitRateLimiter()
        val client = UpbitRestClient(webClient, authInterceptor, rateLimiter, json)
        val mapper = UpbitDomainMapper()

        gateway = UpbitQuotationGateway(client, mapper)
    }

    @Test
    fun `현재가를 조회할 수 있다`() = runTest {
        // Given
        val btcPair = either<DomainError, TradingPair> { TradingPair("KRW-BTC") }.getOrNull()!!

        // When
        val result = either<GatewayError, _> {
            gateway.getTicker(listOf(btcPair))
        }

        // Then
        result.isRight() shouldBe true
        val tickers = result.getOrNull()!!
        tickers.shouldNotBeEmpty()
        tickers.first().pair.value shouldBe "KRW-BTC"
        tickers.first().tradePrice.value.shouldNotBeNull()
    }

    @Test
    fun `호가를 조회할 수 있다`() = runTest {
        // Given
        val btcPair = either<DomainError, TradingPair> { TradingPair("KRW-BTC") }.getOrNull()!!

        // When
        val result = either<GatewayError, _> {
            gateway.getOrderbook(listOf(btcPair))
        }

        // Then
        result.isRight() shouldBe true
        val orderbooks = result.getOrNull()!!
        orderbooks.shouldNotBeEmpty()
        orderbooks.first().pair.value shouldBe "KRW-BTC"
        orderbooks.first().orderbookUnits.shouldNotBeEmpty()
    }

    @Test
    fun `분봉 캔들을 조회할 수 있다`() = runTest {
        // Given
        val btcPair = either<DomainError, TradingPair> { TradingPair("KRW-BTC") }.getOrNull()!!
        val minuteUnit = either<DomainError, CandleUnit> { CandleUnit.Minutes(1) }.getOrNull()!!

        // When
        val result = either<GatewayError, _> {
            gateway.getCandles(btcPair, minuteUnit, 10)
        }

        // Then
        result.isRight() shouldBe true
        val candles = result.getOrNull()!!
        candles.shouldNotBeEmpty()
        candles.first().pair.value shouldBe "KRW-BTC"
    }

    @Test
    fun `일봉 캔들을 조회할 수 있다`() = runTest {
        // Given
        val btcPair = either<DomainError, TradingPair> { TradingPair("KRW-BTC") }.getOrNull()!!

        // When
        val result = either<GatewayError, _> {
            gateway.getCandles(btcPair, CandleUnit.Day, 10)
        }

        // Then
        result.isRight() shouldBe true
        val candles = result.getOrNull()!!
        candles.shouldNotBeEmpty()
    }

    @Test
    fun `체결 내역을 조회할 수 있다`() = runTest {
        // Given
        val btcPair = either<DomainError, TradingPair> { TradingPair("KRW-BTC") }.getOrNull()!!

        // When
        val result = either<GatewayError, _> {
            gateway.getTrades(btcPair, 10)
        }

        // Then
        result.isRight() shouldBe true
        val trades = result.getOrNull()!!
        trades.shouldNotBeEmpty()
        trades.first().pair.value shouldBe "KRW-BTC"
    }

    @Test
    fun `여러 마켓의 현재가를 동시에 조회할 수 있다`() = runTest {
        // Given
        val btcPair = either<DomainError, TradingPair> { TradingPair("KRW-BTC") }.getOrNull()!!
        val ethPair = either<DomainError, TradingPair> { TradingPair("KRW-ETH") }.getOrNull()!!

        // When
        val result = either<GatewayError, _> {
            gateway.getTicker(listOf(btcPair, ethPair))
        }

        // Then
        result.isRight() shouldBe true
        val tickers = result.getOrNull()!!
        tickers.size shouldBe 2
    }
}
