package com.fieldbook.shared.brapi

class BrapiPaginationManager(
    initialPage: Int = 0,
    initialPageSize: Int = DEFAULT_PAGE_SIZE,
) {
    var page: Int = initialPage.coerceAtLeast(0)
        private set

    var pageSize: Int = initialPageSize.coerceAtLeast(1)
        private set

    var totalPages: Int = 1
        private set

    val canMovePrevious: Boolean
        get() = page > 0

    val canMoveNext: Boolean
        get() = page < totalPages - 1

    val pageLabel: String
        get() = "Page ${page + 1} of $totalPages"

    fun reset(pageSize: Int = this.pageSize) {
        page = 0
        this.pageSize = pageSize.coerceAtLeast(1)
        totalPages = 1
    }

    fun previousPage() {
        page = (page - 1).coerceAtLeast(0)
    }

    fun nextPage() {
        page = (page + 1).coerceAtMost(totalPages - 1)
    }

    fun updatePageInfo(
        totalPages: Int?,
        currentPage: Int? = null,
        pageSize: Int? = null,
    ) {
        this.totalPages = (totalPages ?: 1).coerceAtLeast(1)
        pageSize?.let { this.pageSize = it.coerceAtLeast(1) }
        page = (currentPage ?: page).coerceIn(0, this.totalPages - 1)
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 50
    }
}
