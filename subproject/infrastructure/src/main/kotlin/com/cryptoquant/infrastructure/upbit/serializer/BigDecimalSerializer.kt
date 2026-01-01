package com.cryptoquant.infrastructure.upbit.serializer

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal

/**
 * BigDecimal 직렬화를 위한 커스텀 Serializer.
 *
 * kotlinx.serialization은 BigDecimal을 기본 지원하지 않으므로
 * 커스텀 Serializer로 정밀도 손실을 방지합니다.
 *
 * 역직렬화 시 JSON 숫자형과 문자열 모두 지원합니다:
 * - Upbit REST API 응답: 숫자형 (예: "price": 145831000)
 * - 일부 필드: 문자열 (예: "balance": "1000.0")
 *
 * 직렬화 시에는 항상 문자열로 출력하여 정밀도를 보장합니다.
 */
object BigDecimalSerializer : KSerializer<BigDecimal> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("BigDecimal", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: BigDecimal) {
        encoder.encodeString(value.toPlainString())
    }

    override fun deserialize(decoder: Decoder): BigDecimal {
        val jsonDecoder = decoder as? JsonDecoder
            ?: return BigDecimal(decoder.decodeString())

        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> when {
                element.isString -> BigDecimal(element.content)
                else -> BigDecimal(element.content) // JSON number
            }
            else -> throw SerializationException("Expected JsonPrimitive for BigDecimal, got: $element")
        }
    }
}

/**
 * Nullable BigDecimal 직렬화를 위한 커스텀 Serializer.
 *
 * null 값을 허용하며, 그 외에는 [BigDecimalSerializer]와 동일하게 동작합니다.
 */
@OptIn(ExperimentalSerializationApi::class)
object NullableBigDecimalSerializer : KSerializer<BigDecimal?> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("NullableBigDecimal", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: BigDecimal?) {
        if (value == null) {
            encoder.encodeNull()
        } else {
            encoder.encodeString(value.toPlainString())
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override fun deserialize(decoder: Decoder): BigDecimal? {
        val jsonDecoder = decoder as? JsonDecoder
            ?: return try {
                BigDecimal(decoder.decodeString())
            } catch (e: NumberFormatException) {
                null
            }

        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> when {
                element.content == "null" -> null
                element.isString -> BigDecimal(element.content)
                else -> BigDecimal(element.content) // JSON number
            }
            else -> null
        }
    }
}
