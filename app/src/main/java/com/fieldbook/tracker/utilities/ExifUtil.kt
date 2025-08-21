package com.fieldbook.tracker.utilities

import android.content.Context
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.fieldbook.tracker.database.models.ObservationUnitModel
import com.fieldbook.tracker.database.models.ObservationVariableModel
import com.fieldbook.tracker.database.models.StudyModel
import com.fieldbook.tracker.traits.PhotoTraitLayout
import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import com.google.gson.JsonObject

/**
 * Utility class for handling ExifInterface on images.
 */
@RequiresApi(Build.VERSION_CODES.N)
class ExifUtil {

    companion object {

        inline fun <reified T> saveJsonToExif(context: Context, model: T, uri: Uri) {

            try {

                context.contentResolver.openFileDescriptor(uri, "rw").use { fd ->

                    fd?.let { desc ->

                        val exif = ExifInterface(desc.fileDescriptor)

                        val gson = Gson().toJson(
                            model,
                            object : TypeToken<T>() {}.type
                        )

                        exif.setAttribute(ExifInterface.TAG_USER_COMMENT, gson.toString())
                        exif.saveAttributes()
                    }
                }

            } catch (e: Exception) {

                e.printStackTrace()

                Log.d(PhotoTraitLayout.TAG, "EXIF data failed to write.")
            }
        }

        fun saveStringToExif(context: Context, data: String, uri: Uri) {

            try {

                context.contentResolver.openFileDescriptor(uri, "rw").use { fd ->

                    fd?.let { desc ->

                        val exif = ExifInterface(desc.fileDescriptor)

                        val userCommentCharsetPrefix = byteArrayOf(0x41, 0x53, 0x43, 0x49, 0x49, 0x00, 0x00, 0x00) // ASCII
                        val userComment = userCommentCharsetPrefix + data.toByteArray(Charsets.US_ASCII)
                        exif.setAttribute(ExifInterface.TAG_USER_COMMENT, String(userComment, Charsets.US_ASCII))
                        exif.saveAttributes()
                    }
                }

            } catch (e: Exception) {

                e.printStackTrace()

                Log.d(PhotoTraitLayout.TAG, "EXIF data failed to write.")
            }
        }

        /**
         * Json example output saved in TAG_USER_COMMENT EXIF attribute
         *
         * {"study": {
         *  "study_name":"field_sample",
         *  "study_alias":"field_sample",
         *  "study_unique_id_name":"plot_id",
         *  "study_primary_id_name":"row",
         *  "study_secondary_id_name":"plot",
         *  "date_import":"2017-06-15 05:32:44-0700",
         *  "count":200,"internal_id_study":1
         *  },
         *
         * "observation_unit":
         *  {"observation_unit_db_id":"13RPN00002",
         *  "primary_id":"1",
         *  "secondary_id":"2"
         *  },
         *
         *  "observation_variable":{
         *  "observation_variable_name":"Maize Height",
         *  "observation_variable_field_book_format":"photo",
         *  "default_value":"",
         *  "external_db_id":"variable1",
         *  "trait_data_source":"test-server.brapi.org",
         *  "observation_variable_details":"Test"},
         *  "collector": "FirstName LastName",
         *  "timestamp": "yyyy-MM-dd-hh-mm-ss"
         *  }
         */
        fun saveVariableUnitModelToExif(
            context: Context,
            collector: String,
            timestamp: String,
            study: StudyModel?,
            unit: ObservationUnitModel?,
            variable: ObservationVariableModel?,
            uri: Uri,
            rotationModel: SensorHelper.RotationModel?
        ) {

            val modelToSave = JsonObject()

            if (study != null) {

                val studyGson = Gson().toJsonTree(study, object : TypeToken<StudyModel>() {}.type)

                val studyData = studyGson.asJsonObject.getAsJsonObject("map")

                studyData.remove("internal_id_study")

                modelToSave.add("study", studyData)
            }

            if (unit != null) {

                val unitGson =
                    Gson().toJsonTree(unit, object : TypeToken<ObservationUnitModel>() {}.type)

                val unitData = unitGson.asJsonObject.getAsJsonObject("map")

                unitData.remove("study_id")
                unitData.remove("internal_id_observation_unit")

                modelToSave.add("observation_unit", unitData)

            }

            if (variable != null) {

                val variableGson = Gson().toJsonTree(
                    variable,
                    object : TypeToken<ObservationVariableModel>() {}.type
                )

                val variableData = variableGson.asJsonObject.getAsJsonObject("map")

                variableData.remove("visible")
                variableData.remove("position")
                variableData.remove("internal_id_observation_variable")

                modelToSave.add("observation_variable", variableData)
            }

            if (rotationModel != null) {

                val rotationGson = Gson().toJsonTree(
                    rotationModel,
                    object : TypeToken<SensorHelper.RotationModel>() {}.type
                )

                val rotationData = rotationGson.asJsonObject

                modelToSave.add("rotation", rotationData)
            }

            modelToSave.addProperty("collector", collector)
            modelToSave.addProperty("timestamp", timestamp)

            saveStringToExif(context, modelToSave.toString(), uri)
        }
    }
}