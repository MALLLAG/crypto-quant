package com.cryptoquant.infrastructure.upbit.client

import arrow.core.raise.context.either
import com.cryptoquant.domain.gateway.GatewayError
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.codec.json.KotlinSerializationJsonDecoder
import org.springframework.http.codec.json.KotlinSerializationJsonEncoder
import org.springframework.web.reactive.function.client.WebClient

class UpbitRestClientWireMockTest {

    private lateinit var wireMockServer: WireMockServer
    private lateinit var client: UpbitRestClient

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

        client = UpbitRestClient(webClient, authInterceptor, rateLimiter, json)
    }

    @AfterEach
    fun tearDown() {
        wireMockServer.stop()
    }

    @Test
    fun `성공적인 응답을 파싱할 수 있다`() = runTest {
        // Given
        wireMockServer.stubFor(
            get(urlPathEqualTo("/v1/test"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"message": "success", "value": 42}""")
                )
        )

        // When
        val result = either<GatewayError, TestResponse> {
            client.getPublic("/v1/test")
        }

        // Then
        result.isRight() shouldBe true
        val response = result.getOrNull()!!
        response.message shouldBe "success"
        response.value shouldBe 42
    }

    @Test
    fun `401 에러가 AuthenticationError로 변환된다`() = runTest {
        // Given
        wireMockServer.stubFor(
            get(urlPathEqualTo("/v1/test"))
                .willReturn(
                    aResponse()
                        .withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"error":{"name":"jwt_verification","message":"Invalid token"}}""")
                )
        )

        // When
        val result = either<GatewayError, TestResponse> {
            client.getPublic("/v1/test")
        }

        // Then
        result.isLeft() shouldBe true
        val error = result.leftOrNull()!!
        error.shouldBeInstanceOf<GatewayError.AuthenticationError>()
        (error as GatewayError.AuthenticationError).code shouldBe "jwt_verification"
        error.message shouldBe "Invalid token"
    }

    @Test
    fun `429 에러가 RateLimitError로 변환된다`() = runTest {
        // Given
        wireMockServer.stubFor(
            get(urlPathEqualTo("/v1/test"))
                .willReturn(
                    aResponse()
                        .withStatus(429)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"error":{"name":"too_many_requests","message":"Too many requests"}}""")
                )
        )

        // When
        val result = either<GatewayError, TestResponse> {
            client.getPublic("/v1/test")
        }

        // Then
        result.isLeft() shouldBe true
        val error = result.leftOrNull()!!
        error.shouldBeInstanceOf<GatewayError.RateLimitError>()
        (error as GatewayError.RateLimitError).code shouldBe "RATE_LIMIT"
    }

    @Test
    fun `400 에러가 ApiError로 변환된다`() = runTest {
        // Given
        wireMockServer.stubFor(
            get(urlPathEqualTo("/v1/test"))
                .willReturn(
                    aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"error":{"name":"invalid_parameter","message":"Invalid market"}}""")
                )
        )

        // When
        val result = either<GatewayError, TestResponse> {
            client.getPublic("/v1/test")
        }

        // Then
        result.isLeft() shouldBe true
        val error = result.leftOrNull()!!
        error.shouldBeInstanceOf<GatewayError.ApiError>()
        (error as GatewayError.ApiError).code shouldBe "invalid_parameter"
        error.message shouldBe "Invalid market"
    }

    @Test
    fun `500 에러가 NetworkError로 변환된다`() = runTest {
        // Given
        wireMockServer.stubFor(
            get(urlPathEqualTo("/v1/test"))
                .willReturn(
                    aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"error":{"name":"internal_error","message":"Internal server error"}}""")
                )
        )

        // When
        val result = either<GatewayError, TestResponse> {
            client.getPublic("/v1/test")
        }

        // Then
        result.isLeft() shouldBe true
        val error = result.leftOrNull()!!
        error.shouldBeInstanceOf<GatewayError.NetworkError>()
        (error as GatewayError.NetworkError).code shouldBe "SERVER_ERROR"
    }

    @Test
    fun `503 에러가 NetworkError로 변환된다`() = runTest {
        // Given
        wireMockServer.stubFor(
            get(urlPathEqualTo("/v1/test"))
                .willReturn(
                    aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"error":{"name":"service_unavailable","message":"Service temporarily unavailable"}}""")
                )
        )

        // When
        val result = either<GatewayError, TestResponse> {
            client.getPublic("/v1/test")
        }

        // Then
        result.isLeft() shouldBe true
        val error = result.leftOrNull()!!
        error.shouldBeInstanceOf<GatewayError.NetworkError>()
    }

    @Test
    fun `에러 응답 본문이 없어도 에러 변환이 된다`() = runTest {
        // Given
        wireMockServer.stubFor(
            get(urlPathEqualTo("/v1/test"))
                .willReturn(
                    aResponse()
                        .withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("")
                )
        )

        // When
        val result = either<GatewayError, TestResponse> {
            client.getPublic("/v1/test")
        }

        // Then
        result.isLeft() shouldBe true
        val error = result.leftOrNull()!!
        error.shouldBeInstanceOf<GatewayError.AuthenticationError>()
        (error as GatewayError.AuthenticationError).code shouldBe "UNAUTHORIZED"
    }

    @Test
    fun `404 에러가 ApiError로 변환된다`() = runTest {
        // Given
        wireMockServer.stubFor(
            get(urlPathEqualTo("/v1/test"))
                .willReturn(
                    aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"error":{"name":"not_found","message":"Resource not found"}}""")
                )
        )

        // When
        val result = either<GatewayError, TestResponse> {
            client.getPublic("/v1/test")
        }

        // Then
        result.isLeft() shouldBe true
        val error = result.leftOrNull()!!
        error.shouldBeInstanceOf<GatewayError.ApiError>()
        (error as GatewayError.ApiError).code shouldBe "not_found"
    }

    @Test
    fun `잘못된 JSON 응답 본문이 있어도 에러 변환이 된다`() = runTest {
        // Given
        wireMockServer.stubFor(
            get(urlPathEqualTo("/v1/test"))
                .willReturn(
                    aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("not a json")
                )
        )

        // When
        val result = either<GatewayError, TestResponse> {
            client.getPublic("/v1/test")
        }

        // Then
        result.isLeft() shouldBe true
        val error = result.leftOrNull()!!
        error.shouldBeInstanceOf<GatewayError.ApiError>()
        (error as GatewayError.ApiError).code shouldBe "CLIENT_ERROR"
    }

    @Serializable
    data class TestResponse(
        val message: String,
        val value: Int,
    )
}
