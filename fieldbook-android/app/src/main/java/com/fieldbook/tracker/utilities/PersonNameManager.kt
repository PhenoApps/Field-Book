package com.fieldbook.tracker.utilities

import android.content.SharedPreferences
import android.util.Log
import com.fieldbook.tracker.preferences.PreferenceKeys
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import androidx.core.content.edit
import com.fieldbook.tracker.preferences.GeneralKeys
import javax.inject.Inject

/**
 * Utility class for managing person names
 * Saves person names as an array of JSON string
 * eg. [{"firstName": "John", "lastName": "Doe" ]
 */
class PersonNameManager @Inject constructor(private val preferences: SharedPreferences) {

    data class PersonName(val firstName: String, val lastName: String) {
        fun fullName(): String = "$firstName $lastName"
    }

    companion object {
        const val TAG = "PersonNameManager"
        const val FIRST_NAME_KEY = "firstName"
        const val LAST_NAME_KEY = "lastName"
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
                put(FIRST_NAME_KEY, firstName)
                put(LAST_NAME_KEY, lastName)
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
                val firstName = nameObj.getString(FIRST_NAME_KEY)
                val lastName = nameObj.getString(LAST_NAME_KEY)
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
                if (nameObj.getString(FIRST_NAME_KEY).equals(firstName)
                    && nameObj.getString(LAST_NAME_KEY).equals(lastName)) {
                    return true
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
        return false
    }

    /**
     * Migrate existing person name to the preference
     * Useful when the existing person on the device is not already present in the JSONArray
     */
    fun migrateExistingPersonName() {
        val firstName = preferences.getString(GeneralKeys.FIRST_NAME, "") ?: ""
        val lastName = preferences.getString(GeneralKeys.LAST_NAME, "") ?: ""

        if ((firstName.isNotEmpty() == true || lastName.isNotEmpty() == true)) {
            savePersonName(firstName, lastName)
        }
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