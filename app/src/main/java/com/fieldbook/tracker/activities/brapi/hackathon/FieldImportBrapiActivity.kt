package com.fieldbook.tracker.activities.brapi.hackathon

import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.activities.brapi.hackathon.cropontology.tables.BrapiStudy
import com.fieldbook.tracker.adapters.SimpleListAdapter
import com.fieldbook.tracker.brapi.service.BrAPIServiceV2.GenericSearchCallFunction
import com.fieldbook.tracker.brapi.service.BrAPIServiceV2.GenericSearchCallWithDbIdFunction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.brapi.v2.model.core.BrAPIStudy
import org.brapi.v2.model.core.request.BrAPIStudySearchRequest
import org.brapi.v2.model.core.response.BrAPIStudyListResponse
import org.brapi.v2.model.germ.BrAPIGermplasm
import org.brapi.v2.model.germ.request.BrAPIGermplasmSearchRequest
import org.brapi.v2.model.germ.response.BrAPIGermplasmListResponse

class FieldImportBrapiActivity: AbstractImportBrapiActivity() {

    override val brapiObjectListAdapter: RecyclerView.Adapter<*>
        get() = SimpleListAdapter(this)


    override fun fetchBrapiData() {

        launch(Dispatchers.IO) {

            val studies = brapiService.searchStudies(lastPage, 50, listOf()) { null }

            println(studies.size)

//            val fakeModels = listOf(
//                BrapiStudy(
//                    study = BrAPIStudy(
//
//                    )
//                    studyDbId = "1",
//                    name = "Study ABC",
//                    description = "A study of mangoes and how to make them spicy",
//                    institution = "IRRI",
//                    scientist = "James, Karen, Jmar, Maria",
//                    language = "English",
//                    crop = "Rice"
//                ),
//                BrapiStudy(
//                    studyDbId = "2",
//                    name = "Study DEF",
//                    description = "Kansas wheat study of disease resistance",
//                    institution = "Kansas State University",
//                    scientist = "Jesse Poland",
//                    language = "English",
//                    crop = "Wheat"),
//                BrapiStudy(
//                    studyDbId = "3",
//                    name = "Study GHI",
//                    description = "Saudi Arabia wheat study of crop improvement",
//                    institution = "SAE",
//                    scientist = "Jesse Poland",
//                    language = "Arabic",
//                    crop = "Wheat")
//            )

//            val brapiModels = studies.entries.map {
//                val study = it.value
//                BrapiStudy(
//                    studyDbId = it.value.studyDbId,
//                    name = it.value.studyName,
//                    description = it.value.studyDescription,
//                    institution = "Unknown Brapi",
//                    scientist = "",
//                    language = "",
//                    crop = it.value.commonCropName
//                )
//            }

            (studies.entries).forEach {
                val study = it.value
                println(study.locationName)
                println(study.seasons)
                //study.program
                ftsDatabase.studyDao().insert(BrapiStudy(
                    studyName = study.studyName,
                    studyDbId = study.studyDbId,
                    active = study.isActive.toString(),
                    culturalPractices = study.culturalPractices,
                    documentationUrl = study.documentationURL,
                    license = study.license,
                    locationDbId = study.locationDbId,
                    locationName = study.locationName,
                    obsUnitDescription = study.observationUnitsDescription,
                    seasons = study.seasons.joinToString(",") { it },
                    studyCode = study.studyCode,
                    studyPUI = study.studyPUI,
                    studyType = study.studyType,
                    trialDbId = study.trialDbId,
                    trialName = study.trialName,
                    description = study.studyDescription,
                    commonCropName = study.commonCropName
                ))
            }

            runOnUiThread {

                //resultCountTv.text = (checkableItemRecyclerView.adapter as SimpleListAdapter).currentList.size.toString()

                startSearch(lastQuery)

                startSearchViews()

            }
        }
    }
}