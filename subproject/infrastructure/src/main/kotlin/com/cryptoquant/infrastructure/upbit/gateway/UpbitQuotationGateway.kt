package com.cryptoquant.infrastructure.upbit.gateway

import arrow.core.raise.Raise
import arrow.core.raise.context.raise
import com.cryptoquant.domain.common.TradeSequentialId
import com.cryptoquant.domain.common.TradingPair
import com.cryptoquant.domain.gateway.GatewayError
import com.cryptoquant.domain.gateway.QuotationGateway
import com.cryptoquant.domain.quotation.Candle
import com.cryptoquant.domain.quotation.CandleUnit
import com.cryptoquant.domain.quotation.Orderbook
import com.cryptoquant.domain.quotation.Ticker
import com.cryptoquant.domain.quotation.Trade
import com.cryptoquant.infrastructure.upbit.client.UpbitRestClient
import com.cryptoquant.infrastructure.upbit.dto.response.UpbitCandleResponse
import com.cryptoquant.infrastructure.upbit.dto.response.UpbitOrderbookResponse
import com.cryptoquant.infrastructure.upbit.dto.response.UpbitTickerResponse
import com.cryptoquant.infrastructure.upbit.dto.response.UpbitTradeResponse
import com.cryptoquant.infrastructure.upbit.mapper.UpbitDomainMapper
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * QuotationGateway 구현.
 *
 * 업비트 시세 API를 호출하여 도메인 객체로 변환합니다.
 *
 * @see <a href="https://docs.upbit.com/kr/reference/list-candles-minutes.md">업비트 분(Minute) 캔들</a>
 * @see <a href="https://docs.upbit.com/kr/reference/list-tickers.md">업비트 현재가 정보</a>
 */
@Component
class UpbitQuotationGateway(
    private val client: UpbitRestClient,
    private val mapper: UpbitDomainMapper,
) : QuotationGateway {

    context(_: Raise<GatewayError>)
    override suspend fun getCandles(
        pair: TradingPair,
        unit: CandleUnit,
        count: Int,
        to: Instant?,
    ): List<Candle> {
        val path = when (unit) {
            is CandleUnit.Seconds -> raise(
                GatewayError.ApiError(
                    code = "UNSUPPORTED",
                    message = "초봉은 REST API에서 지원하지 않습니다. WebSocket을 사용하세요.",
                )
            )
            is CandleUnit.Minutes -> "/v1/candles/minutes/${unit.minutes}"
            CandleUnit.Day -> "/v1/candles/days"
            CandleUnit.Week -> "/v1/candles/weeks"
            CandleUnit.Month -> "/v1/candles/months"
        }

        val params = buildMap {
            put("market", pair.value)
            put("count", count.coerceIn(1, 200))
            to?.let { put("to", it.toString()) }
        }

        val response: List<UpbitCandleResponse> = client.getPublic(path, params)
        return response.map { mapper.toCandle(it, pair, unit) }
    }

    context(_: Raise<GatewayError>)
    override suspend fun getTicker(pairs: List<TradingPair>): List<Ticker> {
        if (pairs.isEmpty()) {
            return emptyList()
        }

        val markets = pairs.joinToString(",") { it.value }
        val response: List<UpbitTickerResponse> = client.getPublic(
            "/v1/ticker",
            mapOf("markets" to markets),
        )
        return response.map { mapper.toTicker(it) }
    }

    context(_: Raise<GatewayError>)
    override suspend fun getOrderbook(pairs: List<TradingPair>): List<Orderbook> {
        if (pairs.isEmpty()) {
            return emptyList()
        }

        val markets = pairs.joinToString(",") { it.value }
        val response: List<UpbitOrderbookResponse> = client.getPublic(
            "/v1/orderbook",
            mapOf("markets" to markets),
        )
        return response.map { mapper.toOrderbook(it) }
    }

    context(_: Raise<GatewayError>)
    override suspend fun getTrades(
        pair: TradingPair,
        count: Int,
        cursor: TradeSequentialId?,
    ): List<Trade> {
        val params = buildMap {
            put("market", pair.value)
            put("count", count.coerceIn(1, 500))
            cursor?.let { put("cursor", it.value) }
        }

        val response: List<UpbitTradeResponse> = client.getPublic("/v1/trades/ticks", params)
        return response.map { mapper.toTrade(it, pair) }
    }
}
