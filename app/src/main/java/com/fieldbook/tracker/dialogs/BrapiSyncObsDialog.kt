package com.fieldbook.tracker.dialogs

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.app.ProgressDialog
import android.content.Context
import android.os.AsyncTask
import android.os.Handler
import android.text.Html
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.FieldEditorActivity
import com.fieldbook.tracker.brapi.model.Observation
import com.fieldbook.tracker.brapi.service.BrAPIService
import com.fieldbook.tracker.brapi.service.BrAPIServiceFactory
import com.fieldbook.tracker.brapi.service.BrapiPaginationManager
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.database.dao.ObservationVariableDao
import com.fieldbook.tracker.interfaces.FieldSyncController
import com.fieldbook.tracker.objects.FieldObject
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.PreferenceKeys


class BrapiSyncObsDialog(private val context: Context, private val syncController: FieldSyncController, private val fieldObject: FieldObject) : Dialog(context) {

    data class StudyObservations(var fieldBookStudyDbId:Int=0, val traitList: MutableList<TraitObject> = mutableListOf(), val observationList: MutableList<Observation> = mutableListOf()) {
        fun merge(newStudy: StudyObservations) {
            fieldBookStudyDbId = newStudy.fieldBookStudyDbId
            traitList.addAll(newStudy.traitList)
            observationList.addAll(newStudy.observationList)
        }
    }

    private val database = (context as FieldEditorActivity).getDatabase()

    private var saveBtn: Button? = null
    private var noObsLabel: TextView? = null
    private var brAPIService: BrAPIService? = null

    private var studyObservations = StudyObservations()

    private var dialogBrAPISyncObs: AlertDialog? = null

    lateinit var paginationManager: BrapiPaginationManager

    private var fieldNameLbl: TextView? = null

    // Creates a new thread to do importing
    private val importRunnable =
        Runnable { ImportRunnableTask(context, studyObservations, syncController).execute(0) }

    override fun show() {
        showDialog()
        setupUI()
        modifyLayoutElements()
    }

    private fun showDialog() {
        val builder = AlertDialog.Builder(context, R.style.AppAlertDialog)
            .setTitle(R.string.brapi_obs_header_name)
            .setNegativeButton(R.string.dialog_cancel) { d, _ ->
                d.dismiss()
            }
            .setPositiveButton(R.string.dialog_save) { _, _ ->
                saveObservations()
            }

        val view = layoutInflater.inflate(R.layout.dialog_brapi_sync_observations, null)
        builder.setView(view)
        dialogBrAPISyncObs = builder.create()
        dialogBrAPISyncObs?.show()

        saveBtn = dialogBrAPISyncObs?.getButton(BUTTON_POSITIVE)
    }


    private fun setupUI() {
        brAPIService = BrAPIServiceFactory.getBrAPIService(this.context)
        val pageSize = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(PreferenceKeys.BRAPI_PAGE_SIZE, "50")!!.toInt()
        paginationManager = BrapiPaginationManager(0, pageSize)
        saveBtn = dialogBrAPISyncObs?.getButton(BUTTON_POSITIVE)
        noObsLabel = dialogBrAPISyncObs?.findViewById(R.id.noObservationLbl)
        fieldNameLbl = dialogBrAPISyncObs?.findViewById(R.id.studyNameValue)
    }

    private fun modifyLayoutElements() {
        // Set our OK button to be disabled until we are finished loading
        saveBtn?.visibility = View.GONE
        //Hide the error message in case no observations are downloaded.
        noObsLabel?.visibility = View.GONE

        fieldNameLbl?.text = fieldObject.name

        //need to call the load code
        loadObservations()
    }

    /**
     * Function to load the observations from a specific study
     */
    private fun loadObservations() {
        val brapiStudyDbId = fieldObject.studyDbId
        val fieldBookStudyDbId = fieldObject.studyId
        Log.d("BrapiSyncObsDialog", "brapiStudyDbId is $brapiStudyDbId and fieldBookStudyDbId is $fieldBookStudyDbId")
        val brAPIService = BrAPIServiceFactory.getBrAPIService(this.context)

        val existingTraits = database.allTraitObjects
        val existingTraitDbIds = existingTraits.map { it.externalDbId }.toSet()

        //Get the trait information first as we need to know that to associate an observation with a plot:
        brAPIService?.getTraits(brapiStudyDbId, { input ->

            val observationVariableDbIds: MutableList<String> = ArrayList()

            for (obj in input.traits) {

                //only try to sync observations that are of variables which exist in FieldBook
                if (obj.externalDbId in existingTraitDbIds) {
                    println("Trait:" + obj.name)
                    println("ObsIds: " + obj.externalDbId)
                    obj.externalDbId?.let { observationVariableDbIds.add(it) }
                }
            }

            val traitStudy =
                StudyObservations(fieldBookStudyDbId, input.traits, mutableListOf())
            studyObservations.merge(traitStudy)

            brAPIService.getObservations(
                brapiStudyDbId,
                observationVariableDbIds,
                paginationManager,
                { obsInput ->
                    (context as FieldEditorActivity).runOnUiThread {
                        val currentStudy =
                            StudyObservations(fieldBookStudyDbId, mutableListOf(), obsInput)
                        studyObservations.merge(currentStudy)


//                            Print out the values for debug
//                                for (obs in currentStudy.observationList) {
//                                    println("***************************")
//                                    println("StudyId: " + obs.studyId)
//                                    println("ObsId: " + obs.dbId)
//                                    println("UnitDbId: " + obs.unitDbId)
//                                    println("VariableDbId: " + obs.variableDbId)
//                                    println("VariableName: " + obs.variableName)
//                                    println("Value: " + obs.value)
//                                }
                        println("Size of ObsList: ${studyObservations.observationList.size}")
                        //Adding 1 to the page number here so it makes more sense when debugging. Otherwise we get 0/10 as the first and 9/10 as the last message.
                        println("Done pulling observations. Page: ${paginationManager.page + 1}/${paginationManager.totalPages}")

                        //Once we have loaded in all the observations, we can make the save button visible
                        // We need to check page == totalPages - 1 otherwise it will loop indefinitely as the 0-based page will never reach totalPages(1based)
                        if (paginationManager.page >= paginationManager.totalPages - 1) {
                            //If we hit the last page but we have no observations we should not post the save button.
                            if(studyObservations.observationList.size <= 0) {
                                makeNoObsWarningVisible()
                            }
                            else {
                                makeSaveBtnVisible()
                            }
                        }
                        else if(paginationManager.totalPages == 0) {
                            makeNoObsWarningVisible()
                        }
                    }

                    null
                }) {
                println("Stopped:")
                null
            }

            null
        }) { null }
    }

    /**
     * Function that first populates the trait and observation counts, hides the loading spinner and
     * then will pop up the Save button.
     */
    private fun makeSaveBtnVisible() {
        populateCountsAndHideLoadingPanel()

        saveBtn?.visibility = View.VISIBLE
    }

    /**
     * Function that first populates the trait and observation counts, hides the loading spinner and
     * then will pop up the Warning message if no observations are downloaded.
     */
    private fun makeNoObsWarningVisible() {
        populateCountsAndHideLoadingPanel()
        noObsLabel?.visibility = View.VISIBLE
    }

    /**
     * Function used to update the numTraits and numObs counts on the Dialog and then hid the loading spinner.
     */
    private fun populateCountsAndHideLoadingPanel() {
        //populate the description fields
        (dialogBrAPISyncObs?.findViewById<View>(R.id.numberTraitsValue) as TextView).text =
            "${studyObservations.traitList.size}"
        (dialogBrAPISyncObs?.findViewById<View>(R.id.numberObsValue) as TextView).text =
            "${studyObservations.observationList.size}"

        dialogBrAPISyncObs?.findViewById<View>(R.id.loadingPanel)?.visibility = View.GONE
    }

    private fun saveObservations() {
        // Dismiss this dialog
        dismiss()

        // Run saving task in the background so we can showing progress dialog
        val mHandler = Handler()
        mHandler.post(importRunnable)
    }

    // Mimics the class used in the csv field importer to run the saving
    // task in a different thread from the UI thread so the app doesn't freeze up.
    internal class ImportRunnableTask(
        val context: Context, private val studyObservations: StudyObservations,
        private val syncController: FieldSyncController) : AsyncTask<Int, Int, Int>() {

        var dialog: ProgressDialog? = null
        var fail = false
        private var failMessage = ""

        override fun onPreExecute() {
            super.onPreExecute()
            dialog = ProgressDialog(context)
            dialog?.isIndeterminate = true
            dialog?.setCancelable(false)
            dialog?.setMessage(Html.fromHtml(context.resources.getString(R.string.import_dialog_importing)))
            dialog?.show()
        }

        override fun doInBackground(vararg params: Int?): Int? {

            println("numObs: ${studyObservations.observationList.size}")
            println("dbId: ${studyObservations.fieldBookStudyDbId}")
            val dataHelper = DataHelper(context)

            var maxPosition = dataHelper.maxPositionFromTraits + 1
            val traitIdToType = mutableMapOf<String,String>()
            try {
                //Sync the traits first and update date
                for (trait in studyObservations.traitList) {
                    dataHelper.insertTraits(trait.also {
                        it.realPosition = maxPosition++
                    })
                }
                //link up the ids to the type for when we add in the observations
                for (trait in studyObservations.traitList) {
                    val currentId = ObservationVariableDao.getTraitByName(trait.name)!!.id
                    traitIdToType[currentId] = trait.format
                }
                dataHelper.updateSyncDate(studyObservations.fieldBookStudyDbId)
            }
            catch (exc: Exception) {
                fail = true
                failMessage = exc.message ?: "ERROR"
                return null
            }

            try {
                // Calculate rep numbers for new observations based on existing observations
                val hostURL = BrAPIService.getHostUrl(context)
                val existingObservations = dataHelper.getBrapiObservations(studyObservations.fieldBookStudyDbId, hostURL)

                val existingDbIds = existingObservations.map { it.dbId }

                // Track the count of existing observations for each unit-variable pair
                val observationRepBaseMap = mutableMapOf<Pair<String?, String?>, Int>()
                for (obs in existingObservations) {
                    val key = Pair(obs.unitDbId, obs.internalVariableDbId)
                    observationRepBaseMap[key] = observationRepBaseMap.getOrDefault(key, 0) + 1
                }

                // Sort new observations by timestamp so rep # will ascend in order with time
                val observationList = studyObservations.observationList.sortedBy { it.timestamp }

                for (obs in observationList) {

                    if (obs.dbId in existingDbIds) {
                        val existingObs = existingObservations.first { it.dbId == obs.dbId }
                        if (existingObs.timestamp.isEqual(obs.timestamp) || existingObs.timestamp.isBefore(obs.timestamp)) {
                            // Skip saving this observation if it is older than the existing one
                            continue
                        }
                    }

                    val key = Pair(obs.unitDbId, obs.variableDbId)
                    val baseRep = observationRepBaseMap.getOrDefault(key, 0)
                    val nextRep = baseRep + 1
                    obs.rep = nextRep.toString()

                    val obsId = dataHelper.getObservation(obs.studyId, obs.unitDbId, obs.variableDbId, obs.rep)

                    // Save observation to the database and update highest rep # for the pair
                    if (obsId == null) {

                        dataHelper.insertObservation(obs.unitDbId, obs.variableDbId, obs.value,
                            obs.collector, "", "", obs.studyId,
                            null, obs.timestamp, obs.lastSyncedTime, obs.rep)
                    }

                    observationRepBaseMap[key] = nextRep
                }

                return 0

            } catch (exc: Exception) {
                fail = true
                failMessage = exc.message ?: "ERROR"
                Log.e("ImportRunnableTask", "Exception occurred: ${exc.message}", exc)
                return null
            }
        }


        override fun onPostExecute(result: Int?) {
            if (dialog?.isShowing == true) dialog?.dismiss()
            if (result == null || fail) {
                val alertDialogBuilder = AlertDialog.Builder(context, R.style.AppAlertDialog)
                alertDialogBuilder.setTitle(R.string.dialog_save_error_title)
                    .setPositiveButton(R.string.dialog_ok) { _, _ ->
                        // Finish our BrAPI import activity
                        (context as Activity).finish()
                    }
                alertDialogBuilder.setMessage(failMessage)
                val alertDialog = alertDialogBuilder.create()
                alertDialog.show()
            } else {
                syncController.onSyncComplete()
            }
        }
    }
}
