package com.fieldbook.tracker.utilities.sort

import java.math.BigDecimal
import java.util.Locale

/**
 * A value prepared for natural ordering, so "A2" sorts before "A10" and "9" before "10".
 *
 * The value is split into number and text runs which are compared in turn:
 * numbers by value (a leading minus sign and a decimal fraction are part of the number),
 * text case insensitively, and a number before text.
 * Values that are still equal fall back to plain string order, so only identical strings compare as equal.
 *
 * Splitting happens once in [of] rather than on every comparison.
 */
class NaturalSortKey private constructor(
    private val raw: String,
    private val tokens: List<Any>,
) : Comparable<NaturalSortKey> {

    override fun compareTo(other: NaturalSortKey): Int {

        for (i in 0 until minOf(tokens.size, other.tokens.size)) {
            val result = compareTokens(tokens[i], other.tokens[i])
            if (result != 0) return result
        }

        if (tokens.size != other.tokens.size) return tokens.size.compareTo(other.tokens.size)

        return raw.compareTo(other.raw)
    }

    override fun equals(other: Any?) = other is NaturalSortKey && raw == other.raw

    override fun hashCode() = raw.hashCode()

    override fun toString() = raw

    companion object {

        fun of(value: String) = NaturalSortKey(value, tokenize(value.trim()))

        private fun compareTokens(a: Any, b: Any): Int = when {
            a is BigDecimal && b is BigDecimal -> a.compareTo(b)
            a is BigDecimal -> -1
            b is BigDecimal -> 1
            else -> (a as String).compareTo(b as String)
        }

        private fun tokenize(value: String): List<Any> {

            val tokens = mutableListOf<Any>()

            var i = 0
            while (i < value.length) {

                val start = i

                if (startsNumber(value, i)) {

                    if (value[i] == '-') i++

                    while (i < value.length && isDigit(value[i])) i++

                    if (i + 1 < value.length && value[i] == '.' && isDigit(value[i + 1])) {
                        i++
                        while (i < value.length && isDigit(value[i])) i++
                    }

                    tokens.add(BigDecimal(value.substring(start, i)))

                } else {

                    while (i < value.length && !startsNumber(value, i)) i++

                    tokens.add(value.substring(start, i).lowercase(Locale.ROOT))
                }
            }

            return tokens
        }

        //a minus sign only belongs to a number at the very start of the value, elsewhere it is a separator e.g. "A-10"
        private fun startsNumber(value: String, i: Int) = isDigit(value[i])
                || (i == 0 && value[i] == '-' && value.length > 1 && isDigit(value[1]))

        private fun isDigit(c: Char) = c in '0'..'9'
    }
}
