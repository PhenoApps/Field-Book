package com.fieldbook.tracker.fragments

import android.app.ActivityOptions
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.transition.Explode
import android.util.Log
import android.view.View
import android.view.Window
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.core.os.bundleOf
import androidx.fragment.app.*
import androidx.transition.Slide
import com.fieldbook.tracker.R
import com.fieldbook.tracker.brapi.model.BrapiProgram
import kotlinx.coroutines.launch
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

class BrapiProgramsFragment: BaseBrapiFragment() {

    private var mPrograms = HashSet<BrapiProgram>()

    companion object {

        val TAG = BrapiProgramsFragment::class.simpleName

        const val TRIALS_REQUEST = 801
        const val KEY_PROGRAMS_ARRAY = "org.phenoapps.brapi.programs_bundle"
    }

    /**
     * For this import activity, the top bar shows the current table we are filtering for,
     * which navigates to the previous table on click (like a back button).
     * Similarly, the bottom bar moves to the next table but is like a select all query if no fields are chosen.
     */
    private fun setupTopAndBottomButtons() {

        findViewById<Button>(R.id.currentButton)?.setOnClickListener {
            //update UI with the new state
            loadBrAPIData()
        }

        findViewById<Button>(R.id.nextButton)?.setOnClickListener {

            val i = Intent().apply {
                setClassName(this@BrapiProgramsFragment, BrapiTrialsFragment::class.java.name)
                putStringArrayListExtra(KEY_PROGRAMS_ARRAY, ArrayList(mPrograms.map { it.programDbId }))
            }

            startActivityForResult(i, TRIALS_REQUEST)

        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        findViewById<Button>(R.id.currentButton)?.text = "Programs"

        findViewById<Button>(R.id.nextButton)?.text = "Trials"

        setupTopAndBottomButtons()
    }

    //create a list of programs from brapi data source
    private fun loadPrograms() {

        switchProgress()

        mService.getPrograms(mPaginationManager, { programs ->

            switchProgress()

            buildArrayAdapter(programs)

            null

        }) { fail ->

            switchProgress()

            handleFailure(fail)

        }
    }

    //load and display programs
    override fun loadBrAPIData() {

        mScope.launch { //uses Dispatchers.IO for network background processing

            try {

                loadPrograms()

            } catch (cme: ConcurrentModificationException) {

                Log.d(TAG, cme.localizedMessage ?: "Async update error.")

                cme.printStackTrace()

            }
        }
    }

    /**
     * Updates the UI with the data parameter. Type T can be BrapiProgram, BrapiStudyDetails,
     * or ProgramTrialPair. All of which contain information to reconstruct the filter tree
     * from user input.
     */
    private fun <T> buildArrayAdapter(data: List<T>) {

        val listView = findViewById<ListView>(R.id.listView)

        //set up list item click event listener
        listView?.setOnItemClickListener { _, _, position, _ ->

            when (val item = data[position]) {

                is BrapiProgram -> mPrograms.addOrRemove(item)

            }
        }

        val itemDataList: MutableList<Any?> = ArrayList()

        //load data into adapter
        data.forEach {

            when (it) {

                is BrapiProgram -> it.programName?.let { name -> itemDataList.add(name) }

            }
        }

        this.let { ctx ->

            this@BrapiProgramsFragment.runOnUiThread {

                listView?.adapter = ArrayAdapter(ctx, android.R.layout.simple_list_item_multiple_choice, itemDataList)

            }
        }
    }
}