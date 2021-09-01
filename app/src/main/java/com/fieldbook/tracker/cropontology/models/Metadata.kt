package com.fieldbook.tracker.cropontology.models

import com.google.gson.JsonArray

data class Metadata(val datafiles: JsonArray?,
                    val status: JsonArray?,
                    val pagination: Pagination) {
    data class Pagination(val pageSize: Int,
                          val totalCount: Int,
                          val totalPages: Int,
                          val currentPage: Int)
}