package com.fieldbook.tracker.utilities

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.database.dao.ObservationDao
import com.fieldbook.tracker.database.dao.ObservationVariableDao
import com.fieldbook.tracker.database.models.ObservationModel
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.traits.PhotoTraitLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class OldPhotosMigrator {

    companion object {

        val TAG = OldPhotosMigrator::class.simpleName

        /**
         * In v5.3 a directory change happened, where each trait gets it's own media directory for photos.
         * This function is called to query for existing observations that have a uri in the old photos directory, which used to hold all photos.
         * This will update the database obs. value to a new uri after copying it to its respective trait folder, and delete the old photo from the photos dir.
         */
        fun migrateOldPhotosDir(context: Context, database: DataHelper) {

            try {

                val scope = CoroutineScope(Dispatchers.IO)

                val prefs = PreferenceManager.getDefaultSharedPreferences(context)

                scope.launch {

                    ObservationVariableDao.getAllTraitObjects().filter { it.format == PhotoTraitLayout.type }.forEach { t ->

                        val timeStamp = SimpleDateFormat(
                            "yyyy-MM-dd-hh-mm-ss", Locale.getDefault()
                        )

                        val expId = prefs.getInt(GeneralKeys.SELECTED_FIELD_ID, 0).toString()

                        val traitPhotos = database.getAllObservations(expId).filter { it.observation_variable_name == t.trait }

                        if (t.trait != "photos") { //edge case where trait name is actually photos

                            val photoDir = DocumentTreeUtil.getFieldMediaDirectory(context, t.id)
                            val oldPhotos = DocumentTreeUtil.getFieldMediaDirectory(context, "photos")

                            traitPhotos.forEach { photo ->

                                val repeatedValue = database.getRep(expId, photo.observation_unit_id, t.trait)
                                val generatedName =
                                    photo.observation_unit_id + "_" + t.trait + "_" + repeatedValue + "_" + timeStamp.format(
                                        Calendar.getInstance().time
                                    ) + ".jpg"

                                //load uri and check if its parent is "photos" old photo dir
                                oldPhotos?.findFile(photo.value)?.let { photoFile ->

                                    photoDir?.createFile("*/jpg", photoFile.name ?: generatedName)?.let { newFile ->

                                        context.contentResolver?.openInputStream(photoFile.uri)?.use { input ->

                                            context.contentResolver?.openOutputStream(newFile.uri)?.use { output ->

                                                input.copyTo(output)

                                            }
                                        }

                                        ObservationDao.updateObservation(ObservationModel(
                                            photo.createMap().apply {
                                                this["value"] = newFile.uri.toString()
                                            }
                                        ))
                                    }

                                    photoFile.delete()

                                }
                            }
                        }
                    }
                }

            } catch (e: Exception) {

                Log.e(TAG, "Error during photo migration", e)

            }
        }
    }
}