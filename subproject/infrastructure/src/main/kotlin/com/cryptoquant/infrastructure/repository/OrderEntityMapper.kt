package com.cryptoquant.infrastructure.repository

import arrow.core.raise.Raise
import arrow.core.raise.context.withError
import com.cryptoquant.domain.common.Amount
import com.cryptoquant.domain.common.DomainError
import com.cryptoquant.domain.common.Price
import com.cryptoquant.domain.common.TradingPair
import com.cryptoquant.domain.common.Volume
import com.cryptoquant.domain.order.Order
import com.cryptoquant.domain.order.OrderError
import com.cryptoquant.domain.order.OrderId
import com.cryptoquant.domain.order.OrderSide
import com.cryptoquant.domain.order.OrderState
import com.cryptoquant.domain.order.OrderType
import io.r2dbc.spi.Row
import org.springframework.stereotype.Component
import java.math.BigDecimal

/**
 * 영속화 에러.
 */
sealed interface RepositoryError {
    val message: String

    data class InvalidData(override val message: String) : RepositoryError
}

/**
 * Order 엔티티 ↔ 도메인 모델 변환 매퍼.
 */
@Component
class OrderEntityMapper {

    /**
     * 도메인 객체를 엔티티로 변환.
     */
    fun toEntity(order: Order): OrderEntity {
        val (volume, price, totalPrice) = extractOrderTypeFields(order.orderType)

        return OrderEntity(
            id = order.id.value,
            pair = order.pair.value,
            side = order.side.name,
            orderType = toOrderTypeString(order.orderType),
            state = order.state.name,
            volume = volume,
            price = price,
            totalPrice = totalPrice,
            remainingVolume = order.remainingVolume.value,
            executedVolume = order.executedVolume.value,
            executedAmount = order.executedAmount.value,
            paidFee = order.paidFee.value,
            createdAt = order.createdAt,
            doneAt = order.doneAt,
        )
    }

    /**
     * 엔티티를 도메인 객체로 변환.
     */
    context(_: Raise<RepositoryError>)
    fun toDomain(entity: OrderEntity): Order {
        val orderId = withError<RepositoryError, DomainError, OrderId>({ toInvalidData(it) }) {
            OrderId(entity.id)
        }
        val pair = withError<RepositoryError, DomainError, TradingPair>({ toInvalidData(it) }) {
            TradingPair(entity.pair)
        }
        val side = parseSide(entity.side)
        val state = parseState(entity.state)
        val orderType = parseOrderType(entity)
        val remainingVolume = withError<RepositoryError, DomainError, Volume>({ toInvalidData(it) }) {
            Volume(entity.remainingVolume)
        }
        val executedVolume = withError<RepositoryError, DomainError, Volume>({ toInvalidData(it) }) {
            Volume(entity.executedVolume)
        }
        val executedAmount = withError<RepositoryError, DomainError, Amount>({ toInvalidData(it) }) {
            Amount(entity.executedAmount)
        }
        val paidFee = withError<RepositoryError, DomainError, Amount>({ toInvalidData(it) }) {
            Amount(entity.paidFee)
        }

        return withError<RepositoryError, OrderError, Order>({ toInvalidData(it) }) {
            Order(
                id = orderId,
                pair = pair,
                side = side,
                orderType = orderType,
                state = state,
                remainingVolume = remainingVolume,
                executedVolume = executedVolume,
                executedAmount = executedAmount,
                paidFee = paidFee,
                createdAt = entity.createdAt,
                doneAt = entity.doneAt,
            )
        }
    }

    /**
     * R2DBC Row를 엔티티로 변환.
     */
    fun toEntity(row: Row): OrderEntity = OrderEntity(
        id = row.get("id", String::class.java)!!,
        pair = row.get("pair", String::class.java)!!,
        side = row.get("side", String::class.java)!!,
        orderType = row.get("order_type", String::class.java)!!,
        state = row.get("state", String::class.java)!!,
        volume = row.get("volume", BigDecimal::class.java),
        price = row.get("price", BigDecimal::class.java),
        totalPrice = row.get("total_price", BigDecimal::class.java),
        remainingVolume = row.get("remaining_volume", BigDecimal::class.java)!!,
        executedVolume = row.get("executed_volume", BigDecimal::class.java)!!,
        executedAmount = row.get("executed_amount", BigDecimal::class.java)!!,
        paidFee = row.get("paid_fee", BigDecimal::class.java)!!,
        createdAt = row.get("created_at", java.time.OffsetDateTime::class.java)!!.toInstant(),
        doneAt = row.get("done_at", java.time.OffsetDateTime::class.java)?.toInstant(),
    )

    // ===== Private Helpers =====

    private fun extractOrderTypeFields(orderType: OrderType): Triple<BigDecimal?, BigDecimal?, BigDecimal?> =
        when (orderType) {
            is OrderType.Limit -> Triple(orderType.volume.value, orderType.price.value, null)
            is OrderType.MarketBuy -> Triple(null, null, orderType.totalPrice.value)
            is OrderType.MarketSell -> Triple(orderType.volume.value, null, null)
            is OrderType.Best -> Triple(orderType.volume.value, null, null)
        }

    private fun toOrderTypeString(orderType: OrderType): String = when (orderType) {
        is OrderType.Limit -> "LIMIT"
        is OrderType.MarketBuy -> "MARKET_BUY"
        is OrderType.MarketSell -> "MARKET_SELL"
        is OrderType.Best -> "BEST"
    }

    private fun parseSide(side: String): OrderSide = when (side.uppercase()) {
        "BID" -> OrderSide.BID
        "ASK" -> OrderSide.ASK
        else -> OrderSide.BID
    }

    private fun parseState(state: String): OrderState = when (state.uppercase()) {
        "WAIT" -> OrderState.WAIT
        "WATCH" -> OrderState.WATCH
        "DONE" -> OrderState.DONE
        "CANCEL" -> OrderState.CANCEL
        else -> OrderState.WAIT
    }

    context(_: Raise<RepositoryError>)
    private fun parseOrderType(entity: OrderEntity): OrderType {
        val volumeVal = entity.volume ?: BigDecimal.ZERO
        val priceVal = entity.price ?: BigDecimal.ZERO
        val totalPriceVal = entity.totalPrice ?: BigDecimal.ZERO

        return when (entity.orderType.uppercase()) {
            "LIMIT" -> {
                val volume = withError<RepositoryError, DomainError, Volume>({ toInvalidData(it) }) {
                    Volume(volumeVal)
                }
                val price = withError<RepositoryError, DomainError, Price>({ toInvalidData(it) }) {
                    Price(if (priceVal > BigDecimal.ZERO) priceVal else BigDecimal.ONE)
                }
                withError<RepositoryError, OrderError, OrderType>({ toInvalidData(it) }) {
                    OrderType.Limit(volume, price)
                }
            }
            "MARKET_BUY" -> {
                val totalPrice = withError<RepositoryError, DomainError, Amount>({ toInvalidData(it) }) {
                    Amount(totalPriceVal)
                }
                withError<RepositoryError, OrderError, OrderType>({ toInvalidData(it) }) {
                    OrderType.MarketBuy(totalPrice)
                }
            }
            "MARKET_SELL" -> {
                val volume = withError<RepositoryError, DomainError, Volume>({ toInvalidData(it) }) {
                    Volume(volumeVal)
                }
                withError<RepositoryError, OrderError, OrderType>({ toInvalidData(it) }) {
                    OrderType.MarketSell(volume)
                }
            }
            "BEST" -> {
                val volume = withError<RepositoryError, DomainError, Volume>({ toInvalidData(it) }) {
                    Volume(volumeVal)
                }
                withError<RepositoryError, OrderError, OrderType>({ toInvalidData(it) }) {
                    OrderType.Best(volume)
                }
            }
            else -> {
                val volume = withError<RepositoryError, DomainError, Volume>({ toInvalidData(it) }) {
                    Volume(volumeVal)
                }
                val price = withError<RepositoryError, DomainError, Price>({ toInvalidData(it) }) {
                    Price(if (priceVal > BigDecimal.ZERO) priceVal else BigDecimal.ONE)
                }
                withError<RepositoryError, OrderError, OrderType>({ toInvalidData(it) }) {
                    OrderType.Limit(volume, price)
                }
            }
        }
    }

    private fun toInvalidData(error: DomainError): RepositoryError.InvalidData =
        RepositoryError.InvalidData(error.message)

    private fun toInvalidData(error: OrderError): RepositoryError.InvalidData =
        RepositoryError.InvalidData(error.message)
}
