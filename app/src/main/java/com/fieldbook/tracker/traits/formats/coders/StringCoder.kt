package com.fieldbook.tracker.traits.formats.coders

/**
 * The role of the observation value coder is to encode and decode the representations of an observation.
 * This is used to convert the values of an observation to a format that can be stored in a database as a string.
 */
interface StringCoder {
    fun encode(value: Any): String
    fun decode(value: String): Any
}