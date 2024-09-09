package com.fieldbook.tracker.activities.brapi.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.fieldbook.tracker.activities.ThemedActivity
import com.fieldbook.tracker.brapi.service.BrAPIServiceFactory
import com.fieldbook.tracker.brapi.service.BrAPIServiceV1
import com.fieldbook.tracker.brapi.service.BrAPIServiceV2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.brapi.client.v2.model.queryParams.phenotype.VariableQueryParams
import org.brapi.v2.model.pheno.BrAPIObservationVariable

/**
 * receive study infomation including trial
 * from trial get program
 * get obs. levels
 * get observation units, and traits
 */
class BrapiStudyImportActivity : ThemedActivity(), CoroutineScope by MainScope() {

    companion object {

        const val EXTRA_PROGRAM_DB_ID = "programDbId"

        fun getIntent(context: Context): Intent {
            return Intent(context, BrapiStudyImportActivity::class.java)
        }
    }

    /**
     * Lazy-instantiate the brapi service, but cancel the activity if its v1
     */
    protected val brapiService by lazy {
        BrAPIServiceFactory.getBrAPIService(this).also { service ->
            if (service is BrAPIServiceV1) {
                launch(Dispatchers.Main) {
                    Toast.makeText(
                        this@BrapiStudyImportActivity,
                        "BrAPI V1 is not compatible.",
                        Toast.LENGTH_SHORT
                    ).show()
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //parseIntentExtras()

        fetchTraits()
    }

    private fun parseIntentExtras() {
        val programDbId = intent.getStringExtra(EXTRA_PROGRAM_DB_ID)
        if (programDbId != null) {
            // fetch study info
            fetchStudyInfo(programDbId)
        } else {
            Toast.makeText(this, "No programDbId provided", Toast.LENGTH_SHORT).show()
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    private fun fetchStudyInfo(programDbId: String) {

        brapiService.getObservationLevels(programDbId, { levels ->

            //log the levels
            levels.forEach { level ->
                println("Level: ${level.observationLevelName}")
            }

        }) { apiError ->
            Toast.makeText(this, "Failed to fetch observation levels", Toast.LENGTH_SHORT).show()
            setResult(Activity.RESULT_CANCELED)
            finish()

        }
    }

    private fun fetchTraits() {

        launch {

            withContext(Dispatchers.IO) {

                (brapiService as BrAPIServiceV2).observationVariableService.fetchAll(
                    VariableQueryParams()
                ).collect { models ->

                   // models.forEach { trait ->

                        Log.d("TRAIT", (models as BrAPIObservationVariable).observationVariableName)
                    //}
                }
            }
        }
    }
}