package com.cryptoquant.domain.gateway

/**
 * 페이지네이션 요청 파라미터.
 *
 * @property limit 조회할 최대 개수 (1~200, 기본 100)
 * @property cursor 페이지 커서 (이전 응답의 마지막 ID)
 */
data class PageRequest(
    val limit: Int = 100,
    val cursor: String? = null,
) {
    init {
        require(limit in 1..200) { "limit은 1~200 범위여야 합니다" }
    }
}

/**
 * 페이지네이션 응답.
 *
 * @property items 조회된 항목들
 * @property nextCursor 다음 페이지 커서 (null이면 마지막 페이지)
 */
data class PageResponse<T>(
    val items: List<T>,
    val nextCursor: String?,
) {
    /** 다음 페이지가 있는지 여부 */
    val hasNext: Boolean get() = nextCursor != null

    /** 조회된 항목 개수 */
    val size: Int get() = items.size

    /** 항목이 없는지 여부 */
    val isEmpty: Boolean get() = items.isEmpty()

    companion object {
        /** 빈 페이지 응답 생성 */
        fun <T> empty(): PageResponse<T> = PageResponse(emptyList(), null)

        /** 단일 페이지 응답 생성 (다음 페이지 없음) */
        fun <T> of(items: List<T>): PageResponse<T> = PageResponse(items, null)
    }
}
