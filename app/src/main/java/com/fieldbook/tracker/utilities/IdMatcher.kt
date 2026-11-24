package com.fieldbook.tracker.utilities

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Approximate string matching for scanned or typed IDs that did not match exactly,
 * e.g. a barcode read with a dropped, extra, swapped or wrong character, or a truncated read.
 *
 * Candidates are ranked by tier, then by distance:
 *  - [TIER_NORMALIZED]: equal after ignoring case, whitespace and control characters
 *  - [TIER_PARTIAL]: one is contained in the other (truncated or partial scan)
 *  - [TIER_EDIT]: within a small Optimal String Alignment (Damerau-Levenshtein) distance
 */
object IdMatcher {

    const val TIER_NORMALIZED = 0
    const val TIER_PARTIAL = 1
    const val TIER_EDIT = 2

    const val DEFAULT_LIMIT = 5

    /** shortest string allowed to match as a substring of another */
    private const val MIN_PARTIAL_LENGTH = 4

    private const val MAX_EDIT_DISTANCE = 3

    data class Score(val tier: Int, val distance: Int)

    data class Match<T>(val item: T, val text: String, val score: Score)

    fun normalize(s: String?): String {
        if (s == null) return ""
        val builder = StringBuilder(s.length)
        for (c in s) {
            if (!c.isWhitespace() && !c.isISOControl()) builder.append(c.uppercaseChar())
        }
        return builder.toString()
    }

    /** edits allowed for a normalized query of this length */
    fun maxDistance(length: Int): Int = min(MAX_EDIT_DISTANCE, max(1, length / 5))

    /**
     * Scores an already normalized query against an already normalized candidate.
     * @return the score, or null if the candidate is not similar enough
     */
    fun score(query: String, candidate: String): Score? {

        if (query.isEmpty() || candidate.isEmpty()) return null

        if (query == candidate) return Score(TIER_NORMALIZED, 0)

        val shorter = min(query.length, candidate.length)
        val longer = max(query.length, candidate.length)
        if (shorter >= MIN_PARTIAL_LENGTH && shorter * 2 >= longer
            && (query.contains(candidate) || candidate.contains(query))) {
            return Score(TIER_PARTIAL, longer - shorter)
        }

        val limit = maxDistance(query.length)
        val distance = osaDistance(query, candidate, limit)
        return if (distance <= limit) Score(TIER_EDIT, distance) else null
    }

    /**
     * Optimal String Alignment distance between [a] and [b].
     * Stops early and returns [limit] + 1 once the distance must exceed [limit].
     */
    fun osaDistance(a: String, b: String, limit: Int = Int.MAX_VALUE - 1): Int {

        if (abs(a.length - b.length) > limit) return limit + 1

        var prevPrev = IntArray(b.length + 1)
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)

        for (i in 1..a.length) {
            curr[0] = i
            var rowMin = curr[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var value = min(min(prev[j] + 1, curr[j - 1] + 1), prev[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    value = min(value, prevPrev[j - 2] + 1)
                }
                curr[j] = value
                rowMin = min(rowMin, value)
            }
            if (rowMin > limit) return limit + 1
            val recycled = prevPrev
            prevPrev = prev
            prev = curr
            curr = recycled
        }

        return min(prev[b.length], limit + 1)
    }

    /**
     * Ranks [candidates] against [query], keeping each item's best scoring text.
     * @param texts the strings an item can be matched by, e.g. a plot id and its search attribute value
     * @param key identifies an item, so one matched by several texts is only returned once
     */
    fun <T> rank(
        query: String?,
        candidates: Iterable<T>,
        texts: (T) -> List<String?>,
        key: (T) -> Any = { it as Any },
        limit: Int = DEFAULT_LIMIT
    ): List<Match<T>> {

        val normalizedQuery = normalize(query)
        if (normalizedQuery.isEmpty()) return emptyList()

        val best = LinkedHashMap<Any, Match<T>>()

        for (item in candidates) {
            for (text in texts(item)) {
                if (text.isNullOrEmpty()) continue
                val score = score(normalizedQuery, normalize(text)) ?: continue
                val k = key(item)
                val existing = best[k]
                if (existing == null || compare(score, existing.score) < 0) {
                    best[k] = Match(item, text, score)
                }
            }
        }

        return best.values
            .sortedWith { x, y -> compare(x.score, y.score).takeIf { it != 0 } ?: x.text.compareTo(y.text) }
            .take(limit)
    }

    private fun compare(a: Score, b: Score): Int =
        if (a.tier != b.tier) a.tier.compareTo(b.tier) else a.distance.compareTo(b.distance)
}
