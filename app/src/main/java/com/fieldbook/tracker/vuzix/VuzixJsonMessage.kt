package com.fieldbook.tracker.vuzix

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class VuzixJsonMessage(private val optInputMessage: String? = null) {

    companion object {
        private const val keyPrefix = "com.fieldbook.tracker.vuzix.json"
        const val TRAITS_KEY = "$keyPrefix.traits"
        const val COMMAND_KEY = "$keyPrefix.command"
        const val COMMAND_KEY_VALUE = "$keyPrefix.command_value"
        const val FIRST_PREFIX_KEY = "$keyPrefix.first_prefix"
        const val SECOND_PREFIX_KEY = "$keyPrefix.second_prefix"
        const val FIRST_PREFIX_VALUE_KEY = "$keyPrefix.first_prefix_value"
        const val SECOND_PREFIX_VALUE_KEY = "$keyPrefix.second_prefix_value"
        const val TRAIT_NAME_KEY = "$keyPrefix.trait_name"
        const val TRAIT_VALUE_KEY = "$keyPrefix.trait_value"
        const val TRAIT_FORMAT_KEY = "$keyPrefix.trait_format"

        enum class Commands {
            Update, NextPlot, PrevPlot, NextTrait, PrevTrait, EnterValue, SelectTrait, Disconnect
        }
    }

    private lateinit var json: JSONObject

    init {

        optInputMessage?.let { input ->

            json = JSONObject(input)
        }

        if (!this::json.isInitialized) json = JSONObject()
    }

    fun put(key: String, value: String) {
        json.put(key, value)
    }

    fun put(key: String, value: Array<String>) {
        val array = JSONArray()
        value.forEach {
            array.put(it)
        }
        json.put(key, array)
    }

    fun getArray(key: String): Array<String> = try {
        val array = json.getJSONArray(key)
        val list = arrayListOf<String>()
        for (i in 0 until array.length()) {
            list.add(array.getString(i))
        }
        list.toTypedArray()
    } catch (e: JSONException) {
        e.printStackTrace()
        arrayOf()
    }

    fun get(key: String): String? = try {
        json.getString(key)
    } catch (e: JSONException) {
        e.printStackTrace()
        null
    }

    fun keys() = json.keys().iterator().asSequence()

    override fun toString(): String {
        return json.toString(0)
    }
}