package com.fieldbook.tracker.activities.brapi.io.sync

import android.content.SharedPreferences
import androidx.core.content.edit
import com.fieldbook.tracker.activities.brapi.io.sync.BrapiSyncEngine.Conflict
import com.fieldbook.tracker.activities.brapi.io.sync.BrapiSyncEngine.LocalStatus
import com.fieldbook.tracker.brapi.model.Observation
import com.fieldbook.tracker.preferences.PreferenceKeys
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The review choices that leave an observation waiting to upload, kept so they are not asked again
 * every time the sync screen opens. Choosing the server's value, or linking a match to its server
 * copy, is written to the observation at once and needs nothing kept.
 *
 * - Keep local: a conflict settled in favour of this device's edit. It holds until the edit is
 *   uploaded, or the server copy changes again and raises a new conflict.
 * - Upload as new: a re-sync match to send again as a separate observation, until it is uploaded.
 *
 * The sync screen and automatic syncs both follow them. Stored as JSON in one preference, by study.
 */
@Singleton
class BrapiReviewDecisions @Inject constructor(
    private val preferences: SharedPreferences
) {

    companion object {
        private const val KEEP_LOCAL = "keepLocal"
        private const val UPLOAD_AS_NEW = "uploadAsNew"

        // The server copy a keep local choice was made against, a later one is a new conflict.
        private fun serverVersion(server: Observation) = server.timestamp?.toString().orEmpty()

        // Re-sync choice keys start with the local observation's Field Book id, see
        // BrapiSyncEngine.matchResyncCandidates.
        private fun localIdOf(choiceKey: String) = choiceKey.substringBefore('|')
    }

    /**
     * @param keepLocal BrAPI ids of observations kept locally, each with the server version the
     * choice was made against
     * @param uploadAsNew re-sync choice keys to upload again
     */
    data class Decisions(
        val keepLocal: Map<String, String> = emptyMap(),
        val uploadAsNew: Set<String> = emptySet()
    )

    @Synchronized
    fun get(studyId: Int): Decisions = read()[studyId] ?: Decisions()

    /**
     * The conflicts the user has not already settled by keeping the local value.
     */
    fun unsettled(studyId: Int, conflicts: List<Conflict>): List<Conflict> {

        val keepLocal = get(studyId).keepLocal

        return conflicts.filter { conflict ->
            keepLocal[conflict.localObservation.dbId] != serverVersion(conflict.serverObservation)
        }
    }

    @Synchronized
    fun keepLocal(studyId: Int, conflicts: List<Conflict>) = update(studyId) { decisions ->
        decisions.copy(
            keepLocal = decisions.keepLocal + conflicts.mapNotNull { conflict ->
                conflict.localObservation.dbId?.let { it to serverVersion(conflict.serverObservation) }
            }
        )
    }

    @Synchronized
    fun uploadAsNew(studyId: Int, choiceKeys: Collection<String>) = update(studyId) { decisions ->
        decisions.copy(uploadAsNew = decisions.uploadAsNew + choiceKeys)
    }

    /**
     * Drops the choices that no longer apply: a kept edit or a match that has since been uploaded,
     * or whose observation was deleted.
     */
    @Synchronized
    fun prune(studyId: Int, local: LocalStatus) = update(studyId) { decisions ->

        val editedIds = local.editedObservations.mapNotNull { it.dbId }.toSet()
        val newIds = local.newObservations.mapNotNull { it.fieldBookDbId }.toSet()

        Decisions(
            keepLocal = decisions.keepLocal.filterKeys { it in editedIds },
            uploadAsNew = decisions.uploadAsNew.filter { localIdOf(it) in newIds }.toSet()
        )
    }

    private fun update(studyId: Int, change: (Decisions) -> Decisions) {

        val all = read().toMutableMap()
        val current = all[studyId] ?: Decisions()
        val changed = change(current)

        if (changed == current) return

        if (changed.keepLocal.isEmpty() && changed.uploadAsNew.isEmpty()) {
            all.remove(studyId)
        } else {
            all[studyId] = changed
        }

        write(all)
    }

    private fun read(): Map<Int, Decisions> {

        val json = preferences.getString(PreferenceKeys.BRAPI_SYNC_REVIEW_DECISIONS, null)
            ?: return emptyMap()

        return try {

            val root = JSONObject(json)

            root.keys().asSequence().mapNotNull { key ->

                val studyId = key.toIntOrNull() ?: return@mapNotNull null
                val study = root.getJSONObject(key)

                val keepLocal = study.optJSONObject(KEEP_LOCAL)?.let { obj ->
                    obj.keys().asSequence().associateWith { obj.getString(it) }
                } ?: emptyMap()

                val uploadAsNew = study.optJSONArray(UPLOAD_AS_NEW)?.let { array ->
                    (0 until array.length()).map { array.getString(it) }.toSet()
                } ?: emptySet()

                studyId to Decisions(keepLocal, uploadAsNew)

            }.toMap()

        } catch (e: JSONException) {

            // Unreadable choices are only asked again, nothing is uploaded without them.
            emptyMap()
        }
    }

    private fun write(all: Map<Int, Decisions>) {

        val root = JSONObject()

        all.forEach { (studyId, decisions) ->
            root.put(studyId.toString(), JSONObject().apply {
                put(KEEP_LOCAL, JSONObject(decisions.keepLocal))
                put(UPLOAD_AS_NEW, JSONArray(decisions.uploadAsNew.toList()))
            })
        }

        preferences.edit { putString(PreferenceKeys.BRAPI_SYNC_REVIEW_DECISIONS, root.toString()) }
    }
}
