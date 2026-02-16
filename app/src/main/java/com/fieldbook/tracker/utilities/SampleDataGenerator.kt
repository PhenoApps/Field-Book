package com.fieldbook.tracker.utilities

import android.content.Context
import android.util.Log
import com.fieldbook.tracker.database.dao.ObservationDao
import com.fieldbook.tracker.database.dao.ObservationUnitDao
import com.fieldbook.tracker.database.dao.ObservationVariableDao
import com.fieldbook.tracker.database.dao.StudyDao
import com.fieldbook.tracker.database.getTime
import com.fieldbook.tracker.database.models.TraitAttributes
import com.fieldbook.tracker.objects.FieldObject
import com.fieldbook.tracker.objects.TraitObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.brapi.v2.model.pheno.BrAPIScaleValidValuesCategories
import java.io.InputStreamReader
import kotlin.random.Random
import org.threeten.bp.Instant
import org.threeten.bp.OffsetDateTime
import org.threeten.bp.ZoneId

/**
 * Generates a sample field with artificial observations for testing and demonstration purposes.
 *
 * - Creates a field named "field_sample" from field_sample.csv asset
 * - Ensures sample traits are loaded before creating observations
 * - Only adds observations for fields whose names match sample CSV file names
 * - Observation timestamps are randomized over the last three months
 * - Keyboard-like traits (numeric, text, disease rating) use random values from their layout options
 * - Media traits (photo, audio) use artificial filenames without creating actual files
 */
class SampleDataGenerator(private val context: Context) {

    companion object {
        private const val TAG = "SampleDataGenerator"

        /** Names matching the sample CSV files shipped with Field Book */
        val SAMPLE_FIELD_NAMES = setOf(
            "field_sample", "field_sample2", "field_sample3",
            "rtk_sample", "training_sample"
        )

        /** Severity codes matching the disease rating layout buttons */
        private val DISEASE_SEVERITY_OPTIONS = listOf(
            "0", "5", "10", "15", "20", "25", "30", "35", "40", "45",
            "50", "55", "60", "65", "70", "75", "80", "85", "90", "95", "100"
        )

        /** Modifier letters matching the disease rating layout */
        private val DISEASE_MODIFIER_OPTIONS = listOf("R", "M", "S")

        /** Characters available on the numeric soft keyboard */
        private val NUMERIC_CHARS = ('0'..'9').toList()

        /** Characters available on the text soft keyboard */
        private val TEXT_CHARS = (('a'..'z') + ('A'..'Z') + ('0'..'9')).toList()

        /** Max number of artificial media files per type (keeps file count low) */
        private const val MEDIA_FILE_COUNT = 10
    }

    /**
     * Creates a sample field with artificial observations.
     * Returns the study ID on success, or -1 on failure.
     */
    fun generateSampleField(): Int {
        try {
            // Step 1: Ensure sample traits are loaded BEFORE creating observations
            val traits = ensureTraitsLoaded()
            if (traits.isEmpty()) {
                Log.w(TAG, "No traits available for sample data generation")
                return -1
            }

            // Step 2: Create (or locate existing) sample field from field_sample.csv
            val studyId = getOrCreateSampleField()
            if (studyId < 0) {
                Log.w(TAG, "Failed to create or locate sample field")
                return -1
            }

            // Step 3: Only add observations for fields whose names match sample file names
            val studyName = StudyDao.getAllFieldObjects("study_name")
                .find { it.studyId == studyId }?.name ?: ""

            if (studyName !in SAMPLE_FIELD_NAMES) {
                Log.w(TAG, "Field '$studyName' does not match sample file names; skipping observations")
                return studyId
            }

            // Step 4: Retrieve all observation units for the field
            val units = ObservationUnitDao.getAll(studyId)
            if (units.isEmpty()) {
                Log.w(TAG, "No entries found for study $studyId")
                return studyId
            }

            // Step 5: Generate artificial observations with randomized timestamps
            val now = OffsetDateTime.now()
            val threeMonthsAgo = now.minusMonths(3)
            val epochNow = now.toEpochSecond()
            val epochThreeMonthsAgo = threeMonthsAgo.toEpochSecond()

            for (unit in units) {
                for (trait in traits) {
                    val value = generateValue(trait) ?: continue

                    val randomEpoch = Random.nextLong(epochThreeMonthsAgo, epochNow)
                    val timestamp = OffsetDateTime.ofInstant(
                        Instant.ofEpochSecond(randomEpoch),
                        ZoneId.systemDefault()
                    )

                    ObservationDao.insertObservation(
                        plotId = unit.observation_unit_db_id,
                        traitDbId = trait.id,
                        value = value,
                        person = "sample",
                        location = "",
                        notes = "",
                        studyId = studyId.toString(),
                        observationDbId = null,
                        timestamp = timestamp,
                        lastSyncedTime = null,
                        rep = "1"
                    )
                }
            }

            Log.i(TAG, "Generated observations for ${units.size} entries × ${traits.size} traits")
            return studyId

        } catch (e: Exception) {
            Log.e(TAG, "Error generating sample data", e)
            return -1
        }
    }

    /**
     * Returns existing traits if any are loaded; otherwise loads from trait_sample_json.trt.
     */
    private fun ensureTraitsLoaded(): List<TraitObject> {
        val existing = ObservationVariableDao.getAllTraitObjects()
        if (existing.isNotEmpty()) return existing
        return loadSampleTraits()
    }

    /**
     * Parses trait_sample_json.trt from assets and inserts each trait into the database.
     */
    private fun loadSampleTraits(): List<TraitObject> {
        return try {
            val stream = context.assets.open("trait/trait_sample_json.trt")
            val jsonText = stream.bufferedReader().use { it.readText() }

            val json = Json { ignoreUnknownKeys = true }
            val root = json.parseToJsonElement(jsonText).jsonObject
            val traitsArray = root["traits"]?.jsonArray ?: return emptyList()

            val loaded = mutableListOf<TraitObject>()
            var position = 1

            for (elem in traitsArray) {
                try {
                    val obj = elem.jsonObject
                    val name = obj["name"]?.jsonPrimitive?.content ?: continue
                    val fmt = obj["format"]?.jsonPrimitive?.content ?: continue

                    val trait = TraitObject().apply {
                        this.name = name
                        alias = obj["alias"]?.jsonPrimitive?.content ?: name
                        format = fmt
                        defaultValue = obj["defaultValue"]?.jsonPrimitive?.content ?: ""
                        details = obj["details"]?.jsonPrimitive?.content ?: ""
                        visible = obj["isVisible"]?.jsonPrimitive?.booleanOrNull ?: true
                        realPosition = position++
                        synonyms = obj["synonyms"]?.jsonArray
                            ?.map { it.jsonPrimitive.content }
                            ?: listOf(name)
                        traitDataSource = "trait_sample_json.trt"

                        obj["attributes"]?.jsonObject?.forEach { (key, value) ->
                            val primitive = value as? JsonPrimitive
                            val stringValue = primitive?.content ?: value.toString()
                            val def = TraitAttributes.byKey(key)
                            if (def != null) setAttributeValue(def, stringValue)
                        }
                    }

                    val rowId = ObservationVariableDao.insertTraits(trait)
                    if (rowId != -1L) {
                        trait.id = rowId.toString()
                        loaded.add(trait)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping trait element: ${e.message}")
                }
            }

            Log.i(TAG, "Loaded ${loaded.size} sample traits from assets")
            loaded
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load sample traits from assets", e)
            emptyList()
        }
    }

    /**
     * Creates a new "field_sample" field from field_sample.csv, or returns the ID of an
     * existing field with that name.
     */
    private fun getOrCreateSampleField(): Int {
        // Check if field_sample already exists
        val existing = StudyDao.getAllFieldObjects("study_name")
            .find { it.name == "field_sample" }
        if (existing != null) return existing.studyId

        return try {
            val stream = context.assets.open("field_import/field_sample.csv")
            val reader = CSVReader(InputStreamReader(stream))

            val headers = reader.readNext()?.toList() ?: return -1

            val field = FieldObject().apply {
                name = "field_sample"
                alias = "field_sample"
                uniqueId = headers.getOrElse(0) { "plot_id" }
                primaryId = headers.getOrElse(1) { "row" }
                secondaryId = headers.getOrElse(2) { "column" }
            }

            val studyId = StudyDao.createField(field, getTime(), headers, false)
            if (studyId < 0) {
                reader.close()
                return -1
            }

            var row = reader.readNext()
            while (row != null) {
                StudyDao.createFieldData(studyId, headers, row.toList())
                row = reader.readNext()
            }
            reader.close()

            StudyDao.updateImportDate(studyId)
            studyId
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create sample field", e)
            -1
        }
    }

    /**
     * Generates an artificial observation value appropriate for the given trait format.
     * Returns null for formats where artificial values would not be meaningful (location, gnss, etc.).
     */
    private fun generateValue(trait: TraitObject): String? {
        return when (trait.format) {

            "numeric" -> {
                // Three random digits from the numeric keypad (0–9)
                (0 until 3).map { NUMERIC_CHARS.random() }
                    .joinToString("")
                    .trimStart('0')
                    .ifEmpty { "1" }
            }

            "percent" -> Random.nextInt(0, 101).toString()

            "text" -> {
                // Three random characters from the text keyboard
                (0 until 3).map { TEXT_CHARS.random() }.joinToString("")
            }

            "disease rating", "rust rating" -> {
                // One severity value + one modifier letter from the layout buttons
                val severity = DISEASE_SEVERITY_OPTIONS.random()
                val modifier = DISEASE_MODIFIER_OPTIONS.random()
                severity + modifier
            }

            "categorical" -> {
                val catList = parseCategoryList(trait.categories)
                if (catList.isEmpty()) return null

                val selected = if (trait.allowMulticat) {
                    // Up to three random options for multicat traits
                    val count = Random.nextInt(1, minOf(4, catList.size + 1))
                    catList.shuffled().take(count)
                } else {
                    listOf(catList.random())
                }

                // Encode as BrAPI JSON (same format used by CategoricalTraitLayout)
                val brapiCats = selected.map { label ->
                    BrAPIScaleValidValuesCategories().apply {
                        this.label = label
                        this.value = label
                    }
                }
                CategoryJsonUtil.encode(ArrayList(brapiCats))
            }

            "boolean" -> if (Random.nextBoolean()) "TRUE" else "FALSE"

            "counter" -> Random.nextInt(1, 31).toString()

            "date" -> {
                val daysBack = Random.nextInt(0, 91)
                OffsetDateTime.now().minusDays(daysBack.toLong()).toLocalDate().toString()
            }

            "photo" -> {
                // Artificial filename — no actual image file is created
                "SAMPLE_photo_${String.format("%03d", Random.nextInt(1, MEDIA_FILE_COUNT + 1))}.jpg"
            }

            "audio" -> {
                // Artificial filename — no actual audio file is created
                "SAMPLE_audio_${String.format("%03d", Random.nextInt(1, MEDIA_FILE_COUNT + 1))}.wav"
            }

            "gopro" -> {
                "SAMPLE_video_${String.format("%03d", Random.nextInt(1, MEDIA_FILE_COUNT + 1))}.mp4"
            }

            "angle" -> Random.nextInt(0, 361).toString()

            // Skip formats that require real sensor data or file paths
            else -> null
        }
    }

    /**
     * Parses a category string that may be slash-delimited ("Red/Orange/Yellow")
     * or BrAPI JSON encoded, returning a flat list of label strings.
     */
    private fun parseCategoryList(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return try {
            CategoryJsonUtil.decodeCategories(raw).mapNotNull { it.label }
        } catch (e: Exception) {
            raw.split("/").map { it.trim() }.filter { it.isNotEmpty() }
        }
    }
}
