package com.cryptoquant.infrastructure.upbit.client

import com.auth0.jwt.JWT
import com.cryptoquant.infrastructure.upbit.client.UpbitAuthInterceptor.Companion.sha512
import com.cryptoquant.infrastructure.upbit.client.UpbitAuthInterceptor.Companion.toQueryString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeBlank

class UpbitAuthInterceptorTest : DescribeSpec({

    val accessKey = "test-access-key"
    val secretKey = "test-secret-key"
    val interceptor = UpbitAuthInterceptor(accessKey, secretKey)

    describe("generateToken") {
        context("파라미터 없이 호출하면") {
            it("유효한 JWT 토큰을 생성한다") {
                val token = interceptor.generateToken()

                token.shouldNotBeBlank()
                token.split(".").size shouldBe 3

                val decoded = JWT.decode(token)
                decoded.getClaim("access_key").asString() shouldBe accessKey
                decoded.getClaim("nonce").asString().shouldNotBeBlank()
                decoded.getClaim("query_hash").isMissing shouldBe true
            }
        }

        context("쿼리 파라미터와 함께 호출하면") {
            it("query_hash와 query_hash_alg가 포함된 토큰을 생성한다") {
                val params = mapOf("market" to "KRW-BTC", "count" to 10)
                val token = interceptor.generateToken(queryParams = params)

                val decoded = JWT.decode(token)
                decoded.getClaim("access_key").asString() shouldBe accessKey
                decoded.getClaim("nonce").asString().shouldNotBeBlank()
                decoded.getClaim("query_hash").asString().shouldNotBeBlank()
                decoded.getClaim("query_hash_alg").asString() shouldBe "SHA512"
            }
        }

        context("바디 파라미터와 함께 호출하면") {
            it("query_hash와 query_hash_alg가 포함된 토큰을 생성한다") {
                val params = mapOf("market" to "KRW-BTC", "side" to "bid", "ord_type" to "limit")
                val token = interceptor.generateToken(bodyParams = params)

                val decoded = JWT.decode(token)
                decoded.getClaim("query_hash").asString().shouldNotBeBlank()
                decoded.getClaim("query_hash_alg").asString() shouldBe "SHA512"
            }
        }

        context("매번 호출할 때마다") {
            it("다른 nonce 값을 가진 토큰을 생성한다") {
                val token1 = interceptor.generateToken()
                val token2 = interceptor.generateToken()

                val nonce1 = JWT.decode(token1).getClaim("nonce").asString()
                val nonce2 = JWT.decode(token2).getClaim("nonce").asString()

                nonce1 shouldNotBe nonce2
            }
        }
    }

    describe("toQueryString") {
        context("단순 파라미터를 변환하면") {
            it("key=value 형식의 문자열을 반환한다") {
                val params = mapOf("market" to "KRW-BTC", "count" to 10)
                val queryString = params.toQueryString()

                queryString shouldBe "market=KRW-BTC&count=10"
            }
        }

        context("배열 파라미터를 변환하면") {
            it("key[]=value 형식의 문자열을 반환한다") {
                val params = mapOf("uuids" to listOf("uuid1", "uuid2"))
                val queryString = params.toQueryString()

                queryString shouldBe "uuids[]=uuid1&uuids[]=uuid2"
            }
        }

        context("혼합 파라미터를 변환하면") {
            it("올바른 형식의 문자열을 반환한다") {
                val params = linkedMapOf<String, Any>(
                    "market" to "KRW-BTC",
                    "uuids" to listOf("uuid1", "uuid2"),
                    "count" to 10,
                )
                val queryString = params.toQueryString()

                queryString shouldBe "market=KRW-BTC&uuids[]=uuid1&uuids[]=uuid2&count=10"
            }
        }
    }

    describe("sha512") {
        it("올바른 SHA-512 해시를 반환한다") {
            val input = "market=KRW-BTC&count=10"
            val hash = input.sha512()

            // SHA-512 해시는 128자의 16진수 문자열
            hash.length shouldBe 128
            hash.all { it in '0'..'9' || it in 'a'..'f' } shouldBe true
        }

        it("동일한 입력에 대해 동일한 해시를 반환한다") {
            val input = "test-input"
            val hash1 = input.sha512()
            val hash2 = input.sha512()

            hash1 shouldBe hash2
        }
    }
})
