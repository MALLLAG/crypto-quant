package com.cryptoquant.infrastructure.common

/**
 * Offset 기반 페이징 요청.
 *
 * Upbit API의 page/limit 파라미터에 매핑됩니다.
 * 도메인 모듈의 [com.cryptoquant.domain.gateway.PageRequest]는 cursor 기반입니다.
 *
 * @property page 페이지 번호 (1부터 시작)
 * @property limit 페이지당 항목 수 (최대 100)
 */
data class OffsetPageRequest(
    val page: Int = 1,
    val limit: Int = 100,
) {
    init {
        require(page >= 1) { "page must be >= 1" }
        require(limit in 1..100) { "limit must be between 1 and 100" }
    }
}

/**
 * Offset 기반 페이징 응답.
 *
 * @property items 조회된 항목들
 * @property nextPage 다음 페이지 번호 (null이면 마지막 페이지)
 */
data class OffsetPageResponse<T>(
    val items: List<T>,
    val nextPage: Int?,
) {
    /** 다음 페이지가 있는지 여부 */
    val hasNext: Boolean get() = nextPage != null

    /** 조회된 항목 개수 */
    val size: Int get() = items.size

    /** 항목이 없는지 여부 */
    val isEmpty: Boolean get() = items.isEmpty()

    companion object {
        /** 빈 페이지 응답 생성 */
        fun <T> empty(): OffsetPageResponse<T> = OffsetPageResponse(emptyList(), null)

        /** 단일 페이지 응답 생성 (다음 페이지 없음) */
        fun <T> of(items: List<T>): OffsetPageResponse<T> = OffsetPageResponse(items, null)
    }
}
