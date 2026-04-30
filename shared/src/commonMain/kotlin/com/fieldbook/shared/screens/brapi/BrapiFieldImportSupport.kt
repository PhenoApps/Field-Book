package com.fieldbook.shared.screens.brapi

import com.fieldbook.shared.brapi.model.v2.core.BrapiStudyDetails
import com.fieldbook.shared.brapi.model.v2.phenotyping.BrapiObservationUnitDetails
import com.fieldbook.shared.brapi.model.v2.phenotyping.BrapiTraitDetails
import com.fieldbook.shared.database.models.FieldObject
import com.fieldbook.shared.database.models.TraitObject
import com.fieldbook.shared.database.repository.StudyRepository
import com.fieldbook.shared.database.repository.TraitRepository
import com.fieldbook.shared.database.utils.internalTimeFormatter
import com.fieldbook.shared.objects.ImportFormat
import com.fieldbook.shared.sqldelight.FieldbookDatabase
import com.fieldbook.shared.sqldelight.createDatabase
import kotlinx.datetime.Clock
import kotlinx.datetime.format

data class BrapiFieldImportResult(
    val fieldId: Int,
    val importedTraitCount: Int,
)

object BrapiFieldImportSupport {
    private const val OBSERVATION_UNIT_DB_ID = "observationUnitDbId"
    private const val OBSERVATION_UNIT_NAME = "observationUnitName"
    private val OBSERVATION_UNIT_COLUMNS = listOf(
        OBSERVATION_UNIT_DB_ID,
        OBSERVATION_UNIT_NAME,
        "germplasmDbId",
        "germplasmName",
    )

    private val db: FieldbookDatabase
        get() = createDatabase()

    fun importStudy(
        study: BrapiStudyDetails,
        observationUnits: List<BrapiObservationUnitDetails>,
        traits: List<BrapiTraitDetails>,
        studyRepository: StudyRepository,
        traitRepository: TraitRepository,
        sourceUrl: String,
    ): BrapiFieldImportResult {
        if (observationUnits.isEmpty()) {
            error("This BrAPI study has no observation units to import.")
        }

        if (observationUnits.map { it.observationUnitDbId }.toSet().size != observationUnits.size) {
            error("This BrAPI study contains duplicate observation unit IDs.")
        }

        if (studyRepository.checkBrapiStudyUnique("Plot", study.studyDbId) != -1) {
            error("This BrAPI study has already been imported.")
        }

        val field = FieldObject().apply {
            study_db_id = study.studyDbId
            exp_name = study.studyName ?: study.studyDbId
            exp_alias = study.studyName ?: study.studyDbId
            exp_source = sourceUrl.hostForDisplay()
            import_format = ImportFormat.BRAPI.format
            count = observationUnits.size.toString()
            observation_level = "Plot"
            exp_species = study.commonCropName
            unique_id = OBSERVATION_UNIT_DB_ID
            primary_id = OBSERVATION_UNIT_NAME
        }

        var fieldId = -1
        db.transaction {
            fieldId = studyRepository.createField(
                e = field,
                timestamp = Clock.System.now().format(internalTimeFormatter),
                fromBrapi = true,
            )

            observationUnits.forEach { unit ->
                studyRepository.createFieldData(
                    studyId = fieldId.toLong(),
                    columns = OBSERVATION_UNIT_COLUMNS,
                    data = listOf(
                        unit.observationUnitDbId,
                        unit.observationUnitName.orEmpty(),
                        unit.germplasmDbId.orEmpty(),
                        unit.germplasmName.orEmpty(),
                    ),
                )
            }

            val startingPosition = traitRepository.getMaxPositionFromTraits() + 1
            traits.forEachIndexed { index, trait ->
                traitRepository.insertTrait(trait.toTraitObject(sourceUrl, startingPosition + index))
            }
        }

        return BrapiFieldImportResult(
            fieldId = fieldId,
            importedTraitCount = traits.size,
        )
    }

    private fun BrapiTraitDetails.toTraitObject(sourceUrl: String, position: Int): TraitObject {
        return TraitObject(
            name = observationVariableName,
            format = format,
            defaultValue = defaultValue,
            minimum = minimum,
            maximum = maximum,
            categories = categories,
            visible = "true",
            realPosition = position,
            externalDbId = observationVariableDbId,
            traitDataSource = sourceUrl.hostForDisplay(),
            commonCropName = commonCropName,
            language = language,
            dataType = dataType,
            ontologyDbId = ontologyDbId,
            ontologyName = ontologyName,
            details = details,
        )
    }

    private fun String.hostForDisplay(): String {
        return trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore("/")
    }
}
