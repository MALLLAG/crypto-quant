package com.cryptoquant.infrastructure.upbit.gateway

import arrow.core.raise.Raise
import com.cryptoquant.domain.account.Balance
import com.cryptoquant.domain.account.OrderChance
import com.cryptoquant.domain.common.TradingPair
import com.cryptoquant.domain.common.Volume
import com.cryptoquant.domain.gateway.ExchangeGateway
import com.cryptoquant.domain.gateway.GatewayError
import com.cryptoquant.domain.gateway.PageRequest
import com.cryptoquant.domain.gateway.PageResponse
import com.cryptoquant.domain.order.Order
import com.cryptoquant.domain.order.OrderId
import com.cryptoquant.domain.order.OrderSide
import com.cryptoquant.domain.order.OrderType
import com.cryptoquant.domain.order.ValidatedOrderRequest
import com.cryptoquant.infrastructure.upbit.client.UpbitRestClient
import com.cryptoquant.infrastructure.upbit.dto.response.UpbitBalanceResponse
import com.cryptoquant.infrastructure.upbit.dto.response.UpbitOrderChanceResponse
import com.cryptoquant.infrastructure.upbit.dto.response.UpbitOrderResponse
import com.cryptoquant.infrastructure.upbit.mapper.UpbitDomainMapper
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.springframework.stereotype.Component
import java.math.BigDecimal

/**
 * ExchangeGateway 구현.
 *
 * 업비트 거래 API를 호출하여 주문, 잔고 조회 등을 수행합니다.
 *
 * @see <a href="https://docs.upbit.com/kr/reference/new-order.md">업비트 주문하기</a>
 * @see <a href="https://docs.upbit.com/kr/reference/get-balance.md">업비트 전체 계좌 조회</a>
 */
@Component
class UpbitExchangeGateway(
    private val client: UpbitRestClient,
    private val mapper: UpbitDomainMapper,
) : ExchangeGateway {

    context(_: Raise<GatewayError>)
    override suspend fun placeOrder(request: ValidatedOrderRequest): Order {
        // LinkedHashMap으로 파라미터 순서 보장 (JWT 해싱에 중요)
        val bodyParams = linkedMapOf<String, Any>(
            "market" to request.pair.value,
            "side" to request.side.toUpbitSide(),
            "ord_type" to request.orderType.toUpbitOrdType(),
        )
        request.orderType.volumeOrNull()?.let { bodyParams["volume"] = it.value.toPlainString() }
        request.orderType.priceOrNull()?.let { bodyParams["price"] = it.toPlainString() }

        // kotlinx.serialization은 Map<String, Any> 직렬화 불가 → buildJsonObject 사용
        val bodyJson = buildJsonObject {
            put("market", request.pair.value)
            put("side", request.side.toUpbitSide())
            put("ord_type", request.orderType.toUpbitOrdType())
            request.orderType.volumeOrNull()?.let { put("volume", it.value.toPlainString()) }
            request.orderType.priceOrNull()?.let { put("price", it.toPlainString()) }
        }.toString()

        val response: UpbitOrderResponse = client.postPrivate("/v1/orders", bodyParams, bodyJson)
        return mapper.toOrder(response)
    }

    context(_: Raise<GatewayError>)
    override suspend fun cancelOrder(orderId: OrderId): Order {
        val response: UpbitOrderResponse = client.deletePrivate(
            "/v1/order",
            mapOf("uuid" to orderId.value),
        )
        return mapper.toOrder(response)
    }

    context(_: Raise<GatewayError>)
    override suspend fun getOrder(orderId: OrderId): Order {
        val response: UpbitOrderResponse = client.getPrivate(
            "/v1/order",
            mapOf("uuid" to orderId.value),
        )
        return mapper.toOrder(response)
    }

    /**
     * 체결 대기 주문 목록 조회.
     *
     * 참고: 업비트 API는 page/limit 기반 페이징을 제공하지만,
     * 도메인은 cursor 기반 페이징을 사용합니다.
     * 여기서는 cursor를 page 번호로 변환하여 처리합니다.
     *
     * @see <a href="https://docs.upbit.com/kr/reference/list-open-orders.md">업비트 체결 대기 주문 목록 조회</a>
     */
    context(_: Raise<GatewayError>)
    override suspend fun getOpenOrders(
        pair: TradingPair?,
        page: PageRequest,
    ): PageResponse<Order> {
        // cursor를 page 번호로 변환 (cursor가 null이면 1페이지)
        val pageNumber = page.cursor?.toIntOrNull() ?: 1

        val params = buildMap {
            pair?.let { put("market", it.value) }
            put("page", pageNumber)
            put("limit", page.limit.coerceAtMost(100))
            put("order_by", "desc")
        }

        val response: List<UpbitOrderResponse> = client.getPrivate("/v1/orders/open", params)
        val orders = response.map { mapper.toOrder(it) }

        // page 기반 페이징: limit만큼 반환되면 다음 페이지 존재 가능
        val hasNext = orders.size >= page.limit
        return PageResponse(
            items = orders,
            nextCursor = if (hasNext) (pageNumber + 1).toString() else null,
        )
    }

    context(_: Raise<GatewayError>)
    override suspend fun getBalances(): List<Balance> {
        val response: List<UpbitBalanceResponse> = client.getPrivate("/v1/accounts")
        return response.mapNotNull { mapper.toBalance(it) }
    }

    context(_: Raise<GatewayError>)
    override suspend fun getOrderChance(pair: TradingPair): OrderChance {
        val response: UpbitOrderChanceResponse = client.getPrivate(
            "/v1/orders/chance",
            mapOf("market" to pair.value),
        )
        return mapper.toOrderChance(response, pair)
    }

    // ===== Extension Functions =====

    private fun OrderSide.toUpbitSide(): String = when (this) {
        OrderSide.BID -> "bid"
        OrderSide.ASK -> "ask"
    }

    private fun OrderType.toUpbitOrdType(): String = when (this) {
        is OrderType.Limit -> "limit"
        is OrderType.MarketBuy -> "price"
        is OrderType.MarketSell -> "market"
        is OrderType.Best -> "best"
    }

    private fun OrderType.volumeOrNull(): Volume? = when (this) {
        is OrderType.Limit -> volume
        is OrderType.MarketSell -> volume
        is OrderType.Best -> volume
        is OrderType.MarketBuy -> null
    }

    private fun OrderType.priceOrNull(): BigDecimal? = when (this) {
        is OrderType.Limit -> price.value
        is OrderType.MarketBuy -> totalPrice.value
        is OrderType.MarketSell -> null
        is OrderType.Best -> null
    }
}
