package com.fieldbook.tracker.views

import android.content.Context
import android.database.Cursor
import android.os.Handler
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.dialogs.AttributeChooserDialog
import com.fieldbook.tracker.dialogs.QuickGotoDialog
import com.fieldbook.tracker.interfaces.CollectRangeController
import com.fieldbook.tracker.objects.RangeObject
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.preferences.PreferenceKeys
import com.fieldbook.tracker.utilities.Utils
import java.util.*
import androidx.core.content.edit
import com.fieldbook.tracker.adapters.AttributeAdapter

class RangeBoxView : ConstraintLayout {

    companion object {
        const val TAG = "RangeBoxView"
        //truncate to three characters plus the colon
        const val TRUNCATE_LENGTH = 3 + 1
    }

    private var controller: CollectRangeController

    private var rangeID: IntArray
    var paging = 0

    var cRange: RangeObject
    private var lastRange: String

    private var primaryNameTv: TextView
    private var secondaryNameTv: TextView

    var primaryIdTv: TextView
    var secondaryIdTv: TextView

    private var rangeLeft: ImageView
    private var rangeRight: ImageView

    private var plotsProgressBar: ThumbOnlySeekBar

    private var repeatHandler: Handler? = null

    /**
     * Variables to track Quick Goto searching
     */
    private var rangeEdited = false
    private var plotEdited = false

    private var delay = 100
    private var count = 1

    private var exportDataCursor: Cursor? = null

    private val studyId: Int

    init {

        val v = inflate(context, R.layout.view_range_box, this)

        this.rangeLeft = v.findViewById(R.id.rangeLeft)
        this.rangeRight = v.findViewById(R.id.rangeRight)
        this.primaryIdTv = v.findViewById(R.id.primaryIdTv)
        this.secondaryIdTv = v.findViewById(R.id.secondaryIdTv)
        this.primaryNameTv = v.findViewById(R.id.primaryNameTv)
        this.secondaryNameTv = v.findViewById(R.id.secondaryNameTv)
        this.plotsProgressBar = v.findViewById(R.id.plotsProgressBar)

        this.controller = context as CollectRangeController

        studyId = controller.getPreferences().getInt(GeneralKeys.SELECTED_FIELD_ID, 0)

        rangeID = this.controller.getDatabase().getAllRangeID(studyId)
        cRange = RangeObject()
        cRange.secondaryId = ""
        cRange.uniqueId = ""
        cRange.primaryId = ""
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
        return cRange.uniqueId
    }

    fun isEmpty(): Boolean {
        return cRange.uniqueId.isEmpty()
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

        // Allow dragging the progress bar thumb to seek to any entry
        plotsProgressBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser && rangeID.isNotEmpty() && progress in rangeID.indices) {
                    paging = progress + 1
                    updateCurrentRange(rangeID[progress])
                    saveLastPlotAndTrait()
                    display()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                controller.initWidgets(true)
            }
        })

        setName()

        val attributeChooserDialog = AttributeChooserDialog(
            showTraits = false,
            showOther = false,
            showSystemAttributes = false
        )

        primaryNameTv.setOnClickListener {
            attributeChooserDialog.setOnAttributeSelectedListener(object :
                AttributeChooserDialog.OnAttributeSelectedListener {
                override fun onAttributeSelected(model: AttributeAdapter.AttributeModel) {
                    //update preference primary name
                    controller.getPreferences().edit {
                        putString(
                            GeneralKeys.PRIMARY_NAME,
                            model.label
                        )
                    }
                    setName()
                    refresh()
                }
            })
            attributeChooserDialog.show(
                (controller.getContext() as CollectActivity).supportFragmentManager,
                "attributeChooserDialog"
            )
        }

        secondaryNameTv.setOnClickListener {
            attributeChooserDialog.setOnAttributeSelectedListener(object :
                AttributeChooserDialog.OnAttributeSelectedListener {
                override fun onAttributeSelected(model: AttributeAdapter.AttributeModel) {
                    //update preference primary name
                    controller.getPreferences().edit {
                        putString(
                            GeneralKeys.SECONDARY_NAME,
                            model.label
                        )
                    }
                    setName()
                    refresh()
                }
            })
            attributeChooserDialog.show(
                (controller.getContext() as CollectActivity).supportFragmentManager,
                "attributeChooserDialog"
            )
        }

        primaryIdTv.setOnClickListener {
            showQuickGoToDialog()
        }

        secondaryIdTv.setOnClickListener {
            showQuickGoToDialog(primaryClicked = false)
        }
    }

    /**
     * Builds and shows an alert dialog with two edit text fields for the primary/secondary ids
     */
    private fun showQuickGoToDialog(primaryClicked: Boolean = true) {

        val dialog = QuickGotoDialog(controller, primaryClicked) { primaryId, secondaryId ->

            quickGoToNavigateFromDialog(primaryId, secondaryId)
        }

        dialog.show((context as CollectActivity).supportFragmentManager, "quickGotoDialog")

    }

    private fun quickGoToNavigateFromDialog(primaryId: String, secondaryId: String) {

        try {

            when {

                primaryId.isNotBlank() && secondaryId.isNotBlank() -> {
                    controller.moveToSearch(
                        "quickgoto", rangeID,
                        primaryId,
                        secondaryId, null, -1
                    )
                }

                primaryId.isNotBlank() && secondaryId.isBlank() -> {
                    controller.moveToSearch(
                        "range", rangeID,
                        primaryId,
                        null, primaryId, -1
                    )
                }

                primaryId.isBlank() && secondaryId.isNotBlank() -> {
                    controller.moveToSearch(
                        "plot", rangeID,
                        null,
                        secondaryId, secondaryId, -1
                    )
                }

                else -> return

            }

        } catch (e: Exception) {

            Log.e(TAG, "Error in quickGoToNavigateFromDialog: $e")

        }
    }

    private fun truncate(s: String, maxLen: Int): String {
        return if (s.length > maxLen) s.substring(0, maxLen - 1) + ":" else s
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

        controller.navigateIfDataIsValid(controller.getCurrentObservation()?.value) {
            if (rangeID.isNotEmpty()) {
                val step = if (left) -1 else 1
                paging = movePaging(paging, step, false)

                // Refresh onscreen controls
                updateCurrentRange(rangeID[paging - 1])
                saveLastPlotAndTrait()
                if (cRange.uniqueId.isEmpty()) return@navigateIfDataIsValid
                if (controller.getPreferences().getBoolean(PreferenceKeys.PRIMARY_SOUND, false)) {
                    if (cRange.primaryId != lastRange && lastRange != "") {
                        lastRange = cRange.primaryId
                        controller.getSoundHelper().playPlonk()
                    }
                }
                display()
                controller.initWidgets(true)

                Log.d("Field Book", "refresh widgets range box repeat key press")
            }
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

            try {

                cRange = controller.getDatabase().getRange(primaryId, secondaryId, uniqueId, id)

                if (controller.getPreferences().getBoolean(PreferenceKeys.RANGE_PROGRESS_BAR, true)) {
                    // RangeID is a sorted list of obs unit ids for the current field.
                    // Set bar maximum to number of obs units in the field
                    // Set bar progress to position of current obs unit within the sorted list
                    plotsProgressBar.max = rangeID.size
                    plotsProgressBar.progress = rangeID.indexOf(id)
                    plotsProgressBar.visibility = VISIBLE
                } else {
                    plotsProgressBar.visibility = GONE
                }

            } catch (e: Exception) {

                Log.e("Field Book", "Error getting range: $e")

                controller.askUserSendCrashReport(e)

            }

        } else {

            Toast.makeText(
                context,
                R.string.act_collect_study_names_empty, Toast.LENGTH_SHORT
            ).show()

            controller.callFinish()
        }
    }

    private fun updateProgressBarVisibility() {
        if (controller.getPreferences().getBoolean(PreferenceKeys.RANGE_PROGRESS_BAR, true)) {
            plotsProgressBar.visibility = VISIBLE
        } else {
            plotsProgressBar.visibility = GONE
        }
    }

    fun reload() {
        setName()
        paging = 1
        setAllRangeID()
        updateProgressBarVisibility()
        if (rangeID.isNotEmpty()) {
            updateCurrentRange(rangeID[0])
            lastRange = cRange.primaryId
            display()
        } else { //if no fields, print a message and finish with result canceled
            Utils.makeToast(context, context.getString(R.string.act_collect_no_plots))
            controller.cancelAndFinish()
        }
    }

    // Refresh onscreen controls
    fun refresh() {
        updateCurrentRange(rangeID[paging - 1])
        display()
        if (controller.getPreferences().getBoolean(PreferenceKeys.PRIMARY_SOUND, false)) {
            if (cRange.primaryId != lastRange && lastRange != "") {
                lastRange = cRange.primaryId
                controller.getSoundHelper().playPlonk()
            }
        }
    }

    // Updates the data shown in the dropdown
    fun display() {
        primaryIdTv.text = cRange.primaryId
        secondaryIdTv.text = cRange.secondaryId
    }

    fun rightClick() {
        rangeRight.performClick()
    }

    fun saveLastPlotAndTrait() {
        controller.getPreferences().edit {
            putString(GeneralKeys.LAST_PLOT, cRange.uniqueId)
            putString(
                GeneralKeys.LAST_USED_TRAIT,
                (controller.getTraitBox().currentTrait?.id ?: 0).toString()
            )
        }
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

    fun setName() {
        val primaryName = controller.getPreferences().getString(
            GeneralKeys.PRIMARY_NAME,
            context.getString(R.string.search_results_dialog_range)
        ) + ":"
        val secondaryName = controller.getPreferences().getString(
            GeneralKeys.SECONDARY_NAME,
            context.getString(R.string.search_results_dialog_plot)
        ) + ":"
        this.primaryNameTv.text = truncate(primaryName, TRUNCATE_LENGTH)
        this.secondaryNameTv.text = truncate(secondaryName, TRUNCATE_LENGTH)
    }

    fun setAllRangeID() {
        rangeID = controller.getDatabase().getAllRangeID(studyId)
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
        lastRange = cRange.primaryId
    }

    ///// paging /////
    fun moveEntryLeft() {
        controller.navigateIfDataIsValid(controller.getCurrentObservation()?.value) {

            if (controller.getPreferences().getBoolean(PreferenceKeys.ENTRY_NAVIGATION_SOUND, false)
            ) {
                controller.getSoundHelper().playAdvance()
            }
            val entryArrow =
                controller.getPreferences()
                    .getString(PreferenceKeys.DISABLE_ENTRY_ARROW_NO_DATA, "0")
            if ((entryArrow == "1" || entryArrow == "3") && !controller.getTraitBox()
                    .existsTrait()
            ) {
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
    }

    fun moveEntryRight() {
        controller.navigateIfDataIsValid(controller.getCurrentObservation()?.value) {
            val traitBox = controller.getTraitBox()
            if (controller.getPreferences().getBoolean(PreferenceKeys.ENTRY_NAVIGATION_SOUND, false)
            ) {
                controller.getSoundHelper().playAdvance()
            }
            val entryArrow =
                controller.getPreferences()
                    .getString(PreferenceKeys.DISABLE_ENTRY_ARROW_NO_DATA, "0")
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
            controller.getPreferences().getString(PreferenceKeys.HIDE_ENTRIES_WITH_DATA_TOOLBAR, "0")?.toIntOrNull() ?: 0
        } else {
            controller.getPreferences().getString(GeneralKeys.HIDE_ENTRIES_WITH_DATA, "0")?.toIntOrNull() ?: 0
        }

        return when (skipMode) {
            1 -> {
                val traits = ArrayList<TraitObject>()
                controller.getTraitBox().currentTrait.let { traits.add(it!!) }
                moveToNextUncollectedObs(pos, step, traits)
            }
            2 -> {
                val visibleTraits = ArrayList(controller.getDatabase().getVisibleTraits().filterNotNull())
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

        val uniqueName = controller.getPreferences().getString(GeneralKeys.UNIQUE_NAME, "") ?: ""
        val exportDataCursor = controller.getDatabase().getExportTableDataShort(studyId, uniqueName, traits)
        val traitNames = traits.map { it.name }

        exportDataCursor?.use { cursor ->
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
                for (i in 0 until cursor.columnCount) {
                    val traitName = cursor.getColumnName(i)
                    if (traitName in traitNames) {
                        val value = cursor.getString(i)
                        if (value == null) {
                            controller.getPreferences().edit {
                                putString(
                                    GeneralKeys.LAST_USED_TRAIT,
                                    traits.find { it.name == traitName }?.id ?: ""
                                )
                            }
                            if (pos == currentPos) {
                                // We are back where we started, notify that current entry is only one without data
                                Utils.makeToast(context, context.getString(R.string.collect_sole_entry_without_data))
                            }
                            return pos
                        }
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