package com.fieldbook.tracker.dialogs

import android.app.Activity
import android.app.Dialog
import android.app.ProgressDialog
import android.content.Context
import android.content.ContextWrapper
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.text.Html
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.fieldbook.tracker.R
import com.fieldbook.tracker.brapi.BrapiControllerResponse
import com.fieldbook.tracker.brapi.model.Observation
import com.fieldbook.tracker.brapi.service.BrAPIService
import com.fieldbook.tracker.brapi.service.BrAPIServiceFactory
import com.fieldbook.tracker.brapi.service.BrapiPaginationManager
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.objects.FieldObject
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.DialogUtils

data class StudyObservations(var fieldBookStudyDbId:Int=0, val traitList: MutableList<TraitObject> = mutableListOf(), val observationList: MutableList<Observation> = mutableListOf()) {
    fun merge(newStudy: StudyObservations) {
        fieldBookStudyDbId = newStudy.fieldBookStudyDbId
        traitList.addAll(newStudy.traitList)
        observationList.addAll(newStudy.observationList)
    }
}

class BrapiSyncObsDialog(context: Context) : Dialog(context) ,android.view.View.OnClickListener {
    private var saveBtn: Button? = null
    private var brAPIService: BrAPIService? = null

    private var studyObservations = StudyObservations()

    lateinit var paginationManager: BrapiPaginationManager

    lateinit var selectedField: FieldObject
    lateinit var fieldNameLbl: TextView

    // Creates a new thread to do importing
    private val importRunnable =
        Runnable { ImportRunnableTask(context,studyObservations).execute(0) }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setCanceledOnTouchOutside(false)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_brapi_sync_observations)
        brAPIService = BrAPIServiceFactory.getBrAPIService(this.context)
        val pageSize = context.getSharedPreferences("Settings", 0)
            .getString(GeneralKeys.BRAPI_PAGE_SIZE, "1000")!!.toInt()
        paginationManager = BrapiPaginationManager(0, pageSize)
        saveBtn = findViewById(R.id.brapi_save_btn)
        saveBtn!!.setOnClickListener(this)
        fieldNameLbl = findViewById(R.id.studyNameValue)
        val cancelBtn = findViewById<Button>(R.id.brapi_cancel_btn)
        cancelBtn.setOnClickListener(this)
    }

    fun setFieldObject(fieldObject: FieldObject) {
        this.selectedField = fieldObject
    }

    override fun onStart() {
        // Set our OK button to be disabled until we are finished loading
        saveBtn!!.visibility = View.GONE

        fieldNameLbl.text = selectedField.exp_name

        //need to call the load code
        loadObservations(selectedField)
    }

    override fun onClick(v: View) {
        println("Clicked: ${v.id}")
        when (v.id) {
            R.id.brapi_save_btn -> saveObservations()
            R.id.brapi_cancel_btn -> dismiss()
            else -> {}
        }
        dismiss()
    }

    /**
     * Function to load the observations from a specific study
     */
    private fun loadObservations(study: FieldObject) {
        val brapiStudyDbId = study.exp_alias
        val fieldBookStudyDbId = study.exp_id
        val brAPIService = BrAPIServiceFactory.getBrAPIService(this.context)


        //Get the trait information first as we need to know that to associate an observation with a plot:
        brAPIService!!.getTraits(brapiStudyDbId,
            { input ->
                val observationVariableDbIds: MutableList<String> = ArrayList()

                for (obj in input.traits) {
                    println("Trait:" + obj.trait)
                    println("ObsIds: " + obj.externalDbId)
                    observationVariableDbIds.add(obj.externalDbId)
                }
                val traitStudy =
                    StudyObservations(fieldBookStudyDbId, input.traits, mutableListOf())
                studyObservations.merge(traitStudy)

                brAPIService!!.getObservations(brapiStudyDbId,
                    observationVariableDbIds,
                    paginationManager,
                    { obsInput ->
                        ((context as ContextWrapper).baseContext as Activity).runOnUiThread {
                            val currentStudy =
                                StudyObservations(fieldBookStudyDbId, mutableListOf(), obsInput)
                            studyObservations.merge(currentStudy)


                            //Print out the values for debug
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
                            println("Done pulling observations. Page: ${paginationManager.page}/${paginationManager.totalPages}")

                            //Once we have loaded in all the observations, we can make the save button visible
                            if (paginationManager.page == paginationManager.totalPages) {
                                makeSaveBtnVisible()
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
     * Function to unhide the save button
     */
    fun makeSaveBtnVisible() {
        //populate the description fields
        (findViewById<View>(R.id.numberTraitsValue) as TextView).text =
            "${studyObservations.traitList.size}"
        (findViewById<View>(R.id.numberObsValue) as TextView).text =
            "${studyObservations.observationList.size}"

        findViewById<View>(R.id.loadingPanel).visibility = View.GONE
        saveBtn!!.visibility = View.VISIBLE
    }

    fun saveObservations() {
        // Dismiss this dialog
        dismiss()

        // Run saving task in the background so we can showing progress dialog
        val mHandler = Handler()
        mHandler.post(importRunnable)
    }
}
// Mimics the class used in the csv field importer to run the saving
// task in a different thread from the UI thread so the app doesn't freeze up.
internal class ImportRunnableTask(val context: Context, val studyObservations: StudyObservations) : AsyncTask<Int, Int, Int>() {

    var dialog: ProgressDialog? = null
    var fail = false
    var failMessage = ""

    override fun onPreExecute() {
        super.onPreExecute()
        dialog = ProgressDialog(context)
        dialog!!.isIndeterminate = true
        dialog!!.setCancelable(false)
        dialog!!.setMessage(Html.fromHtml(context.resources.getString(R.string.import_dialog_importing)))
        dialog!!.show()
    }

    override fun doInBackground(vararg params: Int?): Int? {

        println("numObs: ${studyObservations.observationList.size}")
        println("dbId: ${studyObservations.fieldBookStudyDbId}")
        val dataHelper = DataHelper(context)

        try {
            //Sync the traits first
            for (trait in studyObservations.traitList) {
                dataHelper.insertTraits(trait)
            }
        }
        catch (exc: Exception) {
            fail = true
            failMessage = exc?.message?:"ERROR"
            return null
        }

        try {
            //Sorting here to only save the most recently taken observation if it is not already in the DB
            val observationList =
                studyObservations.observationList.sortedByDescending { it.timestamp }

            //Then sync the observations
            for (obs in observationList) {
                //Leaving this here for debugging purposes
//            println("****************************")
//            println("Saving: varName: " + obs.variableName)
//            println("Saving: value: " + obs.value)
//            println("Saving: studyId: " + obs.studyId)
//            println("Saving: unitDBId: " + obs.unitDbId)
//            println("Saving: varDbId: " + obs.variableDbId)
                dataHelper.setTraitObservations(studyObservations.fieldBookStudyDbId, obs)
            }
            return 0
        }
        catch (exc: Exception) {
            fail = true
            failMessage = exc?.message?:"ERROR"
            return null
        }

    }


    override fun onPostExecute(result: Int) {
        if (dialog!!.isShowing) dialog!!.dismiss()
        if(fail) {
            val alertDialogBuilder = AlertDialog.Builder(context)
            alertDialogBuilder.setTitle(R.string.dialog_save_error_title)
                .setPositiveButton(R.string.dialog_ok) { dialogInterface, i ->
                    // Finish our BrAPI import activity
                    (context as Activity).finish()
                }
            alertDialogBuilder.setMessage(failMessage)
            val alertDialog = alertDialogBuilder.create()
            alertDialog.show()
            DialogUtils.styleDialogs(alertDialog)
        }
    }
}
