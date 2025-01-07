package com.fieldbook.tracker.views

import android.app.Service
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.dialogs.AttributeChooserDialog
import com.fieldbook.tracker.interfaces.CollectRangeController
import com.fieldbook.tracker.objects.RangeObject
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.Utils
import java.util.*

class RangeBoxView : ConstraintLayout {

    private var controller: CollectRangeController

    private var rangeID: IntArray
    var paging = 0

    var cRange: RangeObject
    private var lastRange: String

    private var rangeName: TextView
    private var plotName: TextView

    //edit text used for quick goto feature range = primary id
    private var rangeEt: EditText

    //edit text used for quick goto feature plot = secondary id
    private var plotEt: EditText
    private var tvRange: TextView
    private var tvPlot: TextView
    private var rangeLeft: ImageView
    private var rangeRight: ImageView

    private var plotsProgressBar: ProgressBar

    private var repeatHandler: Handler? = null

    /**
     * Variables to track Quick Goto searching
     */
    private var rangeEdited = false
    private var plotEdited = false

    private var delay = 100
    private var count = 1

    init {

        val v = inflate(context, R.layout.view_range_box, this)

        this.rangeLeft = v.findViewById(R.id.rangeLeft)
        this.rangeRight = v.findViewById(R.id.rangeRight)
        this.tvRange = v.findViewById(R.id.tvRange)
        this.tvPlot = v.findViewById(R.id.tvPlot)
        this.plotEt = v.findViewById(R.id.plot) //secondary
        this.rangeEt = v.findViewById(R.id.range) //primary
        this.rangeName = v.findViewById(R.id.rangeName)
        this.plotName = v.findViewById(R.id.plotName)
        this.plotsProgressBar = v.findViewById(R.id.plotsProgressBar)

        this.controller = context as CollectRangeController

        rangeID = this.controller.getDatabase().allRangeID
        cRange = RangeObject()
        cRange.plot = ""
        cRange.plot_id = ""
        cRange.range = ""
        lastRange = ""
    }

    constructor(ctx: Context) : super(ctx)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    )

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int, defStyleRes: Int) : super(
        context,
        attrs,
        defStyle,
        defStyleRes
    )

    private fun getPrimaryName(): String {
        return controller.getPreferences().getString(GeneralKeys.PRIMARY_NAME, "") ?: ""
    }

    private fun getSecondaryName(): String {
        return controller.getPreferences().getString(GeneralKeys.SECONDARY_NAME, "") ?: ""
    }

    private fun getUniqueName(): String {
        return controller.getPreferences().getString(GeneralKeys.UNIQUE_NAME, "") ?: ""
    }

    fun toggleNavigation(toggle: Boolean) {
        rangeLeft.isEnabled = toggle
        rangeRight.isEnabled = toggle
    }

    fun getRangeID(): IntArray {
        return rangeID
    }

    fun getRangeIDByIndex(j: Int): Int {
        return rangeID[j]
    }

    fun getRangeLeft(): ImageView {
        return rangeLeft
    }

    fun getRangeRight(): ImageView {
        return rangeRight
    }

    fun getPlotID(): String? {
        return cRange.plot_id
    }

    fun isEmpty(): Boolean {
        return cRange.plot_id.isEmpty()
    }

    fun connectTraitBox(traitBoxView: TraitBoxView) {

        rangeLeft = findViewById(R.id.rangeLeft)
        rangeRight = findViewById(R.id.rangeRight)

        rangeLeft.setOnTouchListener(createOnLeftTouchListener())
        rangeRight.setOnTouchListener(createOnRightTouchListener())

        // Go to previous range
        rangeLeft.setOnClickListener { moveEntryLeft() }

        // Go to next range
        rangeRight.setOnClickListener { moveEntryRight() }

        rangeEt.setOnEditorActionListener(createOnEditorListener(rangeEt, "range"))
        plotEt.setOnEditorActionListener(createOnEditorListener(plotEt, "plot"))
        rangeEt.setOnTouchListener { _, _ ->
            rangeEt.isCursorVisible = true
            false
        }

        plotEt.setOnTouchListener { _, _ ->
            plotEt.isCursorVisible = true
            false
        }
        setName(10)

        val attributeChooserDialog = AttributeChooserDialog(showTraits = false, showOther = false)

        rangeName.setOnClickListener {
            attributeChooserDialog.setOnAttributeSelectedListener(object :
                AttributeChooserDialog.OnAttributeSelectedListener {
                override fun onAttributeSelected(label: String) {
                    //update preference primary name
                    controller.getPreferences().edit().putString(GeneralKeys.PRIMARY_NAME, label).apply()
                    rangeName.setText(label)
                    refresh()
                }
            })
            attributeChooserDialog.show(
                (controller.getContext() as CollectActivity).supportFragmentManager,
                "attributeChooserDialog"
            )
        }

        plotName.setOnClickListener {
            attributeChooserDialog.setOnAttributeSelectedListener(object :
                AttributeChooserDialog.OnAttributeSelectedListener {
                override fun onAttributeSelected(label: String) {
                    //update preference primary name
                    controller.getPreferences().edit().putString(GeneralKeys.SECONDARY_NAME, label).apply()
                    plotName.setText(label)
                    refresh()
                }
            })
            attributeChooserDialog.show(
                (controller.getContext() as CollectActivity).supportFragmentManager,
                "attributeChooserDialog"
            )
        }
    }

    private fun repeatUpdate() {

        controller.getTraitBox().setNewTraits(getPlotID())

    }

    private fun truncate(s: String, maxLen: Int): String {
        return if (s.length > maxLen) s.substring(0, maxLen - 1) + ":" else s
    }

    /**
     * This listener is used in the QuickGoto feature.
     * This listens to the primary/secondary edit text's in the rangebox.
     * When the soft keyboard enter key action is pressed (IME_ACTION_DONE)
     * this will use the moveToSearch function.
     * First it will search for both primary/secondary ids if they have both been changed.
     * If one has not been changed or a plot is not found for both terms then it defaults to
     * a search with whatever was changed last.
     * @param edit the edit text to assign this listener to
     * @param searchType the type used in moveToSearch, either plot or range
     */
    private fun createOnEditorListener(
        edit: EditText,
        searchType: String
    ): TextView.OnEditorActionListener {
        return object : TextView.OnEditorActionListener {
            override fun onEditorAction(view: TextView, actionId: Int, event: KeyEvent?): Boolean {
                // do not do bit check on event, crashes keyboard
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    try {

                        //if both quick goto et's have been changed, attempt a search with them
                        if (rangeEdited && plotEdited) {

                            //if the search fails back-down to the original search
                            if (!controller.moveToSearch(
                                    "quickgoto", rangeID,
                                    rangeEt.text.toString(),
                                    plotEt.text.toString(), null, -1
                                )
                            ) {
                                controller.moveToSearch(
                                    searchType,
                                    rangeID,
                                    null,
                                    null,
                                    view.text.toString(),
                                    -1
                                )
                            }
                        } else { //original search if only one has changed
                            controller.moveToSearch(
                                searchType,
                                rangeID,
                                null,
                                null,
                                view.text.toString(),
                                -1
                            )
                        }

                        //reset the changed flags
                        rangeEdited = false
                        plotEdited = false
                        val imm: InputMethodManager =
                            context.getSystemService(Service.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.hideSoftInputFromWindow(edit.windowToken, 0)
                    } catch (ignore: Exception) {
                    }
                    return true
                }
                return false
            }
        }
    }

    private fun createRunnable(directionStr: String): Runnable {
        return object : Runnable {
            override fun run() {
                repeatKeyPress(directionStr)
                if (count % 5 == 0) {
                    if (delay > 20) {
                        delay -= 10
                    }
                }
                count++
                repeatHandler?.postDelayed(this, delay.toLong())
            }
        }
    }

    private fun createOnLeftTouchListener(): OnTouchListener {
        val actionLeft = createRunnable("left")

        return createOnTouchListener(
            rangeLeft, actionLeft,
            R.drawable.chevron_left_pressed,
            R.drawable.chevron_left
        )
    }

    private fun createOnRightTouchListener(): OnTouchListener {
        val actionRight = createRunnable("right")

        return createOnTouchListener(
            rangeRight, actionRight,
            R.drawable.chevron_right_pressed,
            R.drawable.chevron_right
        )
    }

    private fun createOnTouchListener(
        control: ImageView,
        action: Runnable, imageID: Int, imageID2: Int
    ): OnTouchListener {
        return OnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    control.setImageResource(imageID)
                    control.performClick()
                    if (repeatHandler != null) {
                        return@OnTouchListener true
                    }
                    repeatHandler = Handler()
                    repeatHandler?.postDelayed(action, 750)
                    delay = 100
                    count = 1
                }
                MotionEvent.ACTION_MOVE -> {}
                MotionEvent.ACTION_UP -> {
                    control.setImageResource(imageID2)
                    if (repeatHandler == null) {
                        return@OnTouchListener true
                    }
                    repeatHandler?.removeCallbacks(action)
                    repeatHandler = null
                    repeatUpdate()
                }
                MotionEvent.ACTION_CANCEL -> {
                    control.setImageResource(imageID2)
                    repeatHandler?.removeCallbacks(action)
                    repeatHandler = null
                    v.tag = null // mark btn as not pressed
                }
            }

            // return true to prevent calling btn onClick handler
            true
        }
    }

    // Simulate range right key press
    fun repeatKeyPress(directionStr: String) {
        val left = directionStr.equals("left", ignoreCase = true)
        if (!controller.validateData(controller.getCurrentObservation()?.value)) {
            return
        }
        if (rangeID.isNotEmpty()) {
            val step = if (left) -1 else 1
            paging = movePaging(paging, step, false)

            // Refresh onscreen controls
            updateCurrentRange(rangeID[paging - 1])
            saveLastPlot()
            if (cRange.plot_id.isEmpty()) return
            if (controller.getPreferences().getBoolean(GeneralKeys.PRIMARY_SOUND, false)) {
                if (cRange.range != lastRange && lastRange != "") {
                    lastRange = cRange.range
                    controller.getSoundHelper().playPlonk()
                }
            }
            display()
            controller.getTraitBox().setNewTraits(getPlotID())
            controller.initWidgets(true)

            Log.d("Field Book", "refresh widgets range box repeate key press")
        }
    }

    /**
     * Checks whether the preference study names are empty.
     * If they are show a message, otherwise update the current range.
     * @param id the range position to update to
     */
    private fun updateCurrentRange(id: Int) {
        val primaryId = getPrimaryName()
        val secondaryId = getSecondaryName()
        val uniqueId = getUniqueName()
        if (primaryId.isNotEmpty() && secondaryId.isNotEmpty() && uniqueId.isNotEmpty()) {
            cRange = controller.getDatabase().getRange(primaryId, secondaryId, uniqueId, id)

            // RangeID is a sorted list of obs unit ids for the current field.
            // Set bar maximum to number of obs units in the field
            // Set bar progress to position of current obs unit within the sorted list
            plotsProgressBar.max = rangeID.size
            plotsProgressBar.progress = rangeID.indexOf(id)

        } else {
            //TODO switch to Utils
            Toast.makeText(
                context,
                R.string.act_collect_study_names_empty, Toast.LENGTH_SHORT
            ).show()
            controller.callFinish()
        }
    }

    fun reload() {
        switchVisibility(controller.getPreferences().getBoolean(GeneralKeys.QUICK_GOTO, false))
        setName(8)
        paging = 1
        setAllRangeID()
        if (rangeID.isNotEmpty()) {
            updateCurrentRange(rangeID[0])
            lastRange = cRange.range
            display()
            controller.getTraitBox().setNewTraits(cRange.plot_id)
        } else { //if no fields, print a message and finish with result canceled
            Utils.makeToast(context, context.getString(R.string.act_collect_no_plots))
            controller.cancelAndFinish()
        }
    }

    // Refresh onscreen controls
    fun refresh() {
        updateCurrentRange(rangeID[paging - 1])
        display()
        if (controller.getPreferences().getBoolean(GeneralKeys.PRIMARY_SOUND, false)) {
            if (cRange.range != lastRange && lastRange != "") {
                lastRange = cRange.range
                controller.getSoundHelper().playPlonk()
            }
        }
    }

    // Updates the data shown in the dropdown
    fun display() {
        rangeEt.setText(cRange.range)
        plotEt.setText(cRange.plot)
        rangeEt.isCursorVisible = false
        plotEt.isCursorVisible = false
        tvRange.text = cRange.range
        tvPlot.text = cRange.plot
    }

    fun rightClick() {
        rangeRight.performClick()
    }

    fun saveLastPlot() {
        val ed: SharedPreferences.Editor = controller.getPreferences().edit()
        ed.putString(GeneralKeys.LAST_PLOT, cRange.plot_id)
        ed.apply()
    }

    private fun createTextWatcher(type: String): TextWatcher {
        return object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (type == "range") rangeEdited = true else plotEdited = true
            }
        }
    }

    private fun switchVisibility(textview: Boolean) {
        if (textview) {
            tvRange.visibility = GONE
            tvPlot.visibility = GONE
            rangeEt.visibility = VISIBLE
            plotEt.visibility = VISIBLE

            //when the et's are visible create text watchers to listen for changes
            rangeEt.addTextChangedListener(createTextWatcher("range"))
            plotEt.addTextChangedListener(createTextWatcher("plot"))
        } else {
            tvRange.visibility = VISIBLE
            tvPlot.visibility = VISIBLE
            rangeEt.visibility = GONE
            plotEt.visibility = GONE
        }
    }

    fun setName(maxLen: Int) {
        val primaryName = controller.getPreferences().getString(
            GeneralKeys.PRIMARY_NAME,
            context.getString(R.string.search_results_dialog_range)
        ) + ":"
        val secondaryName = controller.getPreferences().getString(
            GeneralKeys.SECONDARY_NAME,
            context.getString(R.string.search_results_dialog_plot)
        ) + ":"
        rangeName.text = truncate(primaryName, maxLen)
        plotName.text = truncate(secondaryName, maxLen)
    }

    fun setAllRangeID() {
        rangeID = controller.getDatabase().allRangeID
    }

    fun setRange(id: Int) {
        updateCurrentRange(id)
    }

    fun setRangeByIndex(j: Int) {
        if (j < rangeID.size) {
            updateCurrentRange(rangeID[j])
        }
    }

    fun setLastRange() {
        lastRange = cRange.range
    }

    ///// paging /////

    ///// paging /////
    fun moveEntryLeft() {
        if (!controller.validateData(controller.getCurrentObservation()?.value)) {
            return
        }
        if (controller.getPreferences().getBoolean(GeneralKeys.ENTRY_NAVIGATION_SOUND, false)
        ) {
            controller.getSoundHelper().playAdvance()
        }
        val entryArrow =
            controller.getPreferences().getString(GeneralKeys.DISABLE_ENTRY_ARROW_NO_DATA, "0")
        if ((entryArrow == "1" || entryArrow == "3") && !controller.getTraitBox().existsTrait()) {
            controller.getSoundHelper().playError()
        } else {
            if (rangeID.isNotEmpty()) {
                //index.setEnabled(true);
                paging = decrementPaging(paging)
                controller.refreshMain()
            }
        }
        controller.resetGeoNavMessages()
        controller.getCollectInputView().resetInitialIndex()
    }

    fun moveEntryRight() {
        val traitBox = controller.getTraitBox()
        if (!controller.validateData(controller.getCurrentObservation()?.value)) {
            return
        }
        if (controller.getPreferences().getBoolean(GeneralKeys.ENTRY_NAVIGATION_SOUND, false)
        ) {
            controller.getSoundHelper().playAdvance()
        }
        val entryArrow =
            controller.getPreferences().getString(GeneralKeys.DISABLE_ENTRY_ARROW_NO_DATA, "0")
        if ((entryArrow == "2" || entryArrow == "3") && !traitBox.existsTrait()) {
            controller.getSoundHelper().playError()
        } else {
            if (rangeID.isNotEmpty()) {
                //index.setEnabled(true);
                // In addition to advancing the entry, return to the first trait in the trait order if the preference is enabled
                if (controller.isReturnFirstTrait()) {
                    traitBox.returnFirst()
                }
                paging = incrementPaging(paging)
                controller.refreshMain()
            }
        }
        controller.resetGeoNavMessages()
        controller.getCollectInputView().resetInitialIndex()
    }

    private fun decrementPaging(pos: Int): Int {
        return movePaging(pos, -1, fromToolbar = false)
    }

    private fun incrementPaging(pos: Int): Int {
        return movePaging(pos, 1, fromToolbar = false)
    }

    fun movePaging(pos: Int, step: Int, fromToolbar: Boolean): Int {
        //three skipMode options: 0. disabled 1. skip active trait 2. skip but check all traits

        val skipMode = if (fromToolbar) {
            controller.getPreferences().getString(GeneralKeys.HIDE_ENTRIES_WITH_DATA_TOOLBAR, "0")?.toIntOrNull() ?: 0
        } else {
            controller.getPreferences().getString(GeneralKeys.HIDE_ENTRIES_WITH_DATA, "0")?.toIntOrNull() ?: 0
        }

        return when (skipMode) {
            1 -> {
                val currentTraitString = controller.getTraitBox().currentTrait?.name
                val currentTraitObj = controller.getDatabase().getDetail(currentTraitString)
                moveToNextUncollectedObs(pos, step, arrayListOf(currentTraitObj))
            }
            2 -> {
                val visibleTraits = ArrayList(controller.getDatabase().visibleTraitObjects.filterNotNull())
                moveToNextUncollectedObs(pos, step, visibleTraits)
            }
            else -> moveSimply(pos, step)
        }
    }


    private fun moveSimply(pos: Int, step: Int): Int {
        var p = pos
        p += step
        return if (p > rangeID.size) {
            1
        } else if (p < 1) {
            rangeID.size
        } else {
            p
        }
    }

    private fun moveToNextUncollectedObs(currentPos: Int, direction: Int, traits: ArrayList<TraitObject>): Int {
        val studyId = controller.getPreferences().getInt(GeneralKeys.SELECTED_FIELD_ID, 0)
        val study = controller.getDatabase().getFieldObject(studyId)
        val cursor = controller.getDatabase().getExportTableDataShort(studyId, study.unique_id, traits)

        cursor?.use {
            // Convert one-based range position to zero-based cursor position
            val zeroBasedPos = currentPos - 1
            cursor.moveToPosition(zeroBasedPos)

            // Track the number of rows seen
            var rowsSeen = 0
            val totalRows = cursor.count

            while (rowsSeen < totalRows) {
                // Move in the specified direction
                val moved = if (direction > 0) cursor.moveToNext() else cursor.moveToPrevious()

                if (!moved) {
                    if (direction > 0) {
                        cursor.moveToFirst()
                    } else {
                        cursor.moveToLast()
                    }
                }

                val pos = cursor.position + 1 // Convert zero-based cursor position back to one-based range position
                rowsSeen++

                // Check for uncollected trait observations
                for (trait in traits) {
                    val value = cursor.getString(cursor.getColumnIndexOrThrow(trait.name))
                    if (value == null) {
                        controller.getPreferences().edit().putString(GeneralKeys.LAST_USED_TRAIT, trait.name).apply()
                        if (pos == currentPos) {
                            // we are back where we started, notify that current entry is only one without data
                            Utils.makeToast(context, context.getString(R.string.collect_sole_entry_without_data))
                        }
                        return pos
                    }
                }
            }
        }

        // Display toast message if no uncollected obs is found after checking all rows
        if (traits.size == 1) {
            Utils.makeToast(context, context.getString(R.string.collect_all_entries_complete_for_current_trait))
        } else {
            Utils.makeToast(context, context.getString(R.string.collect_all_entries_complete_for_all_traits))
        }

        return currentPos
    }

    @Throws(Exception::class)
    fun nextEmptyPlot(): Int {
        var pos = paging
        if (pos == rangeID.size) {
            throw Exception()
        }
        while (pos <= rangeID.size) {
            pos += 1
            if (pos > rangeID.size) {
                throw Exception()
            }
            if (!controller.existsTrait(rangeID[pos - 1])) {
                paging = pos
                return rangeID[pos - 1]
            }
        }
        throw Exception() // not come here
    }

    fun clickLeft() {
        rangeLeft.performClick()
    }

    fun clickRight() {
        rangeRight.performClick()
    }
}