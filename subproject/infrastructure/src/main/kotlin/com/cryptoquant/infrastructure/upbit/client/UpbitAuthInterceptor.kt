package com.cryptoquant.infrastructure.upbit.client

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.security.MessageDigest
import java.util.UUID

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
 *
 * @property accessKey Upbit API 접근 키
 * @property secretKey Upbit API 비밀 키
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
     * @return Bearer 토큰에 사용할 JWT 문자열
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
            if (!paramsToHash.isNullOrEmpty()) {
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

    companion object {
        /**
         * Map을 쿼리 문자열로 변환.
         *
         * 주의: URL 인코딩하지 않음 (Upbit 요구사항)
         * 주의: 파라미터 순서 유지 (LinkedHashMap 사용 권장)
         */
        internal fun Map<String, Any>.toQueryString(): String =
            entries.flatMap { (key, value) ->
                when (value) {
                    is List<*> -> value.map { "$key[]=$it" }
                    else -> listOf("$key=$value")
                }
            }.joinToString("&")

        /**
         * 문자열의 SHA-512 해시를 16진수 문자열로 반환.
         */
        internal fun String.sha512(): String =
            MessageDigest.getInstance("SHA-512")
                .digest(toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
