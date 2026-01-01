package com.cryptoquant.infrastructure.repository

import arrow.core.raise.context.either
import com.cryptoquant.domain.common.TradingPair
import com.cryptoquant.domain.gateway.PageRequest
import com.cryptoquant.domain.gateway.PageResponse
import com.cryptoquant.domain.order.Order
import com.cryptoquant.domain.order.OrderId
import com.cryptoquant.domain.repository.OrderRepository
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.bind
import org.springframework.stereotype.Repository
import java.time.Instant

/**
 * OrderRepository R2DBC 구현.
 *
 * PostgreSQL R2DBC를 사용한 비동기 주문 저장소입니다.
 * cursor 기반 페이징을 사용하여 대용량 데이터에서도 일관된 성능을 제공합니다.
 */
@Repository
class OrderR2dbcRepository(
    private val databaseClient: DatabaseClient,
    private val mapper: OrderEntityMapper,
) : OrderRepository {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 주문을 저장합니다.
     *
     * UPSERT를 사용하여 새 주문이면 삽입, 기존 주문이면 업데이트합니다.
     */
    override suspend fun save(order: Order) {
        val entity = mapper.toEntity(order)

        databaseClient.sql(
            """
            INSERT INTO orders (
                id, pair, side, order_type, state,
                volume, price, total_price,
                remaining_volume, executed_volume, executed_amount, paid_fee,
                created_at, done_at
            ) VALUES (
                :id, :pair, :side, :orderType, :state,
                :volume, :price, :totalPrice,
                :remainingVolume, :executedVolume, :executedAmount, :paidFee,
                :createdAt, :doneAt
            )
            ON CONFLICT (id) DO UPDATE SET
                state = EXCLUDED.state,
                remaining_volume = EXCLUDED.remaining_volume,
                executed_volume = EXCLUDED.executed_volume,
                executed_amount = EXCLUDED.executed_amount,
                paid_fee = EXCLUDED.paid_fee,
                done_at = EXCLUDED.done_at
            """.trimIndent()
        )
            .bind("id", entity.id)
            .bind("pair", entity.pair)
            .bind("side", entity.side)
            .bind("orderType", entity.orderType)
            .bind("state", entity.state)
            .bind("volume", entity.volume)
            .bind("price", entity.price)
            .bind("totalPrice", entity.totalPrice)
            .bind("remainingVolume", entity.remainingVolume)
            .bind("executedVolume", entity.executedVolume)
            .bind("executedAmount", entity.executedAmount)
            .bind("paidFee", entity.paidFee)
            .bind("createdAt", entity.createdAt)
            .bind("doneAt", entity.doneAt)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    /**
     * 주문 ID로 주문을 조회합니다.
     */
    override suspend fun findById(orderId: OrderId): Order? {
        val entity = databaseClient.sql("SELECT * FROM orders WHERE id = :id")
            .bind("id", orderId.value)
            .map { row, _ -> mapper.toEntity(row) }
            .first()
            .awaitFirstOrNull()
            ?: return null

        return either { mapper.toDomain(entity) }
            .onLeft { logger.error("Failed to convert entity to domain: ${it.message}") }
            .getOrNull()
    }

    /**
     * 미체결 주문 목록을 조회합니다.
     *
     * cursor 기반 페이징을 사용합니다.
     * cursor는 마지막 조회 항목의 createdAt (ISO 8601 형식)입니다.
     */
    override suspend fun findOpenOrders(
        pair: TradingPair?,
        page: PageRequest,
    ): PageResponse<Order> {
        val sql = buildString {
            append("SELECT * FROM orders WHERE state IN ('WAIT', 'WATCH')")
            pair?.let { append(" AND pair = :pair") }
            page.cursor?.let { append(" AND created_at < :cursor") }
            append(" ORDER BY created_at DESC LIMIT :limit")
        }

        var spec = databaseClient.sql(sql)
            .bind("limit", page.limit + 1) // 다음 페이지 확인용

        pair?.let { spec = spec.bind("pair", it.value) }
        page.cursor?.let { spec = spec.bind("cursor", Instant.parse(it)) }

        val entities = spec.map { row, _ -> mapper.toEntity(row) }
            .all()
            .asFlow()
            .toList()

        val hasNext = entities.size > page.limit
        val resultEntities = entities.take(page.limit)

        val items = resultEntities.mapNotNull { entity ->
            either { mapper.toDomain(entity) }
                .onLeft { logger.error("Failed to convert entity to domain: ${it.message}") }
                .getOrNull()
        }

        return PageResponse(
            items = items,
            nextCursor = if (hasNext) items.lastOrNull()?.createdAt?.toString() else null
        )
    }
}
