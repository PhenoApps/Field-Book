package com.fieldbook.tracker.utilities

import androidx.room.*
import com.fieldbook.tracker.cropontology.models.Response
import io.ktor.client.*
import io.ktor.client.request.*

class CropontologyImporter {

    companion object {

        /**
         * Extension function that automatically returns all pages from BrAPI endpoint
         * if allPages is set to false, then it will just return the regular get call.
         * @param url the url to the endpoint
         * @param allPages flag for whether or not to multi-query for all pages read in metadata
         */
        suspend fun HttpClient.getAll(url: String, pages: Int = 1): List<Response> {

            val json = arrayListOf<Response>(get(url))

            if (json.size > 0) {
                with (json.first().metadata?.pagination) { //get metadata of the response's first page
                    if (this?.totalPages ?: 1 > 1) { //if there are anymore pages then iteratively get them
                        val start = (this?.currentPage ?: 0) + 1 //start at the next page
                        val end = this?.totalPages ?: 0 //end at the total pages
                        for (i in start until end) { //iterate over remaining pages and change url query params
                            println("Getting Page: $i")
                            try {

                                json.add(get("$url/?page=$i"))

                            } catch (e: Exception) {
                                println("Page: $i Failed")
                            }
                            if (i == pages) break
                        }
                    }
                }
            }

            return json //if totalCount = 1 then just return the json from first get
        }
    }
}