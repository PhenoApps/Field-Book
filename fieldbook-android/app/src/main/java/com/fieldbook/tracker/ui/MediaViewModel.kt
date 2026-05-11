package com.fieldbook.tracker.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.database.models.ObservationModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class MediaEntry(val uri: String, val observationId: Int, val type: MediaType)

@HiltViewModel
class MediaViewModel @Inject constructor(
    private val database: DataHelper
) : ViewModel() {

    private val _audio = MutableStateFlow<List<MediaEntry>>(emptyList())
    val audio: StateFlow<List<MediaEntry>> = _audio

    private val _video = MutableStateFlow<List<MediaEntry>>(emptyList())
    val video: StateFlow<List<MediaEntry>> = _video

    private val _photo = MutableStateFlow<List<MediaEntry>>(emptyList())
    val photo: StateFlow<List<MediaEntry>> = _photo

    private val TAG = "MediaViewModel"

    /**
     * Loads observations for the provided study/unit/trait and extracts any non-null media URIs.
     */
    fun loadMediaFor(studyId: String, observationUnit: String, traitDbId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val obs: Array<ObservationModel> = database.getAllObservations(studyId, observationUnit, traitDbId)

            val aud = mutableListOf<MediaEntry>()
            val vid = mutableListOf<MediaEntry>()
            val pho = mutableListOf<MediaEntry>()

            for (o in obs) {
                try {
                    val map = o.map
                    val a = map["audio_uri"] as? String
                    val v = map["video_uri"] as? String
                    val p = map["photo_uri"] as? String

                    val id = try { o.internal_id_observation } catch (_: Exception) { -1 }

                    if (!a.isNullOrBlank()) aud.add(MediaEntry(a, id, MediaType.AUDIO))
                    if (!v.isNullOrBlank()) vid.add(MediaEntry(v, id, MediaType.VIDEO))
                    if (!p.isNullOrBlank()) pho.add(MediaEntry(p, id, MediaType.PHOTO))
                } catch (_: Exception) {
                    // ignore malformed rows
                }
            }

            _audio.emit(aud)
            _video.emit(vid)
            _photo.emit(pho)
        }
    }

    /**
     * Deletes the given media file and clears the corresponding URI in the database.
     * Returns the Job so callers can wait for completion if needed.
     */
    fun deleteMedia(mediaEntry: MediaEntry): Job {
        return viewModelScope.launch(Dispatchers.IO) {
            try {
                val model = try {
                    database.getObservationById(mediaEntry.observationId.toString())
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load observation ${mediaEntry.observationId}: ${e.message}")
                    null
                }

                //delete the file and update the database
                val uri = when (mediaEntry.type) {
                    MediaType.AUDIO -> model?.audio_uri
                    MediaType.VIDEO -> model?.video_uri
                    else -> model?.photo_uri
                }

                if (!uri.isNullOrBlank()) {
                    try {
                        val file = File(uri)
                        if (file.exists()) file.delete()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to delete file $uri: ${e.message}")
                    }
                }

                when (mediaEntry.type) {
                    MediaType.AUDIO -> model?.audio_uri = null
                    MediaType.VIDEO -> model?.video_uri = null
                    else -> model?.photo_uri = null
                }

                model?.let {
                    try {
                        database.updateObservationMediaUris(it)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to update DB for observation ${it.internal_id_observation}: ${e.message}")
                    }
                }

                // Update the in-memory lists so UI reflects the deletion immediately
                when (mediaEntry.type) {
                    MediaType.AUDIO -> _audio.emit(_audio.value.filter { !(it.observationId == mediaEntry.observationId && it.uri == mediaEntry.uri) })
                    MediaType.VIDEO -> _video.emit(_video.value.filter { !(it.observationId == mediaEntry.observationId && it.uri == mediaEntry.uri) })
                    MediaType.PHOTO -> _photo.emit(_photo.value.filter { !(it.observationId == mediaEntry.observationId && it.uri == mediaEntry.uri) })
                }
            } catch (e: Exception) {
                Log.w(TAG, "Unexpected error deleting media: ${e.message}")
            }
        }
    }
}