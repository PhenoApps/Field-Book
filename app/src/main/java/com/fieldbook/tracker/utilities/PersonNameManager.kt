package com.fieldbook.tracker.utilities

import android.content.SharedPreferences
import android.util.Log
import com.fieldbook.tracker.preferences.PreferenceKeys
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import androidx.core.content.edit

/**
 * Utility class for managing person names
 * Saves person names as an array of JSON string
 * eg. [{"firstName": "John", "lastName": "Doe" ]
 */
class PersonNameManager(private val preferences: SharedPreferences) {

    data class PersonName(val firstName: String, val lastName: String) {
        fun fullName(): String = "$firstName $lastName"
    }

    companion object {
        const val TAG = "PersonNameManager"
    }

    /**
     * Try to save a person name
     *
     * @param firstName first name
     * @param lastName last name
     * @return true if the name was added (wasn't a duplicate), false otherwise
     */
    fun savePersonName(firstName: String, lastName: String): Boolean {

        try {
            val namesArray = getCurrentNamesArray()

            val newPerson = JSONObject().apply {
                put("firstName", firstName)
                put("lastName", lastName)
            }

            // check if name already exists
            if (nameExists(namesArray, firstName, lastName)) {
                return false
            }

            namesArray.put(newPerson)
            preferences.edit {
                putString(
                    PreferenceKeys.LIST_OF_PERSON_NAMES, namesArray.toString()
                )
            }

            return true
        } catch (e: JSONException) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * Get all saved person names
     */
    fun getPersonNames(): List<PersonName> {
        val namesList = mutableListOf<PersonName>()

        try {
            val namesArray = getCurrentNamesArray()

            for (i in 0 until namesArray.length()) {
                val nameObj = namesArray.getJSONObject(i)
                val firstName = nameObj.getString("firstName")
                val lastName = nameObj.getString("lastName")
                namesList.add(PersonName(firstName, lastName))
            }
        } catch (e: JSONException) {
            Log.e(TAG, "Error parsing person name from JSON: ${e.message}", e)
        }

        return namesList
    }

    /**
     * Clear all saved person names
     */
    fun clearPersonNames() {
        preferences.edit {
            putString(PreferenceKeys.LIST_OF_PERSON_NAMES, "[]")
        }
    }

    /**
     * Check if a person name already exists in preferences
     */
    private fun nameExists(namesArray: JSONArray, firstName: String, lastName: String): Boolean {
        for (i in 0 until namesArray.length()) {
            try {
                val nameObj = namesArray.getJSONObject(i)
                if (nameObj.getString("firstName").equals(firstName)
                    && nameObj.getString("lastName").equals(lastName)) {
                    return true
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
        return false
    }

    /**
     * Get the current JSONArray of names from preferences
     */
    private fun getCurrentNamesArray(): JSONArray {
        val namesJson = preferences.getString(PreferenceKeys.LIST_OF_PERSON_NAMES, "[]") ?: "[]"
        return try {
            JSONArray(namesJson)
        } catch (e: JSONException) {
            Log.e(TAG, "Error parsing person names JSON: ${e.message}", e)
            JSONArray()
        }
    }
}