package com.fieldbook.tracker.utilities

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

open class JsonUtil {

    companion object {

        //just pulled this from here https://stackoverflow.com/questions/10174898/how-to-check-whether-a-given-string-is-valid-json-in-java
        fun isJsonValid(test: String?): Boolean {
            if (test == null) return false
            try {
                JSONObject(test)
            } catch (ex: JSONException) {
                // edited, to include @Arthur's comment
                // e.g. in case JSONArray is valid as well...
                try {
                    JSONArray(test)
                } catch (ex1: JSONException) {
                    return false
                }
            }
            return true
        }
    }
}