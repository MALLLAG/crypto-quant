package com.cryptoquant.infrastructure.repository

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.math.BigDecimal
import java.time.Instant

/**
 * R2DBC 기반 Order 엔티티.
 *
 * 도메인 모델과 데이터베이스 스키마 간의 매핑을 담당합니다.
 * OrderEntityMapper를 통해 도메인 객체와 변환됩니다.
 */
@Table("orders")
data class OrderEntity(
    @Id
    val id: String,

    val pair: String,

    val side: String,

    @Column("order_type")
    val orderType: String,

    val state: String,

    val volume: BigDecimal?,

    val price: BigDecimal?,

    @Column("total_price")
    val totalPrice: BigDecimal?,

    @Column("remaining_volume")
    val remainingVolume: BigDecimal,

    @Column("executed_volume")
    val executedVolume: BigDecimal,

    @Column("executed_amount")
    val executedAmount: BigDecimal,

    @Column("paid_fee")
    val paidFee: BigDecimal,

    @Column("created_at")
    val createdAt: Instant,

    @Column("done_at")
    val doneAt: Instant?,
)
