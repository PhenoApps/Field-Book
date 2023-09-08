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
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import com.fieldbook.tracker.R
import com.fieldbook.tracker.interfaces.CollectRangeController
import com.fieldbook.tracker.objects.RangeObject
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

    private var repeatHandler: Handler? = null

    /**
     * Variables to track Quick Goto searching
     */
    private var rangeEdited = false
    private var plotEdited = false

    /**
     * unique plot names used in range queries
     * query and save them once during initialization
     */
    private var firstName: String
    private var secondName: String
    private var uniqueName: String

    private var delay = 100
    private var count = 1

    init {

        val v = inflate(context, R.layout.view_range_box, this)

        this.rangeLeft = v.findViewById(R.id.rangeLeft)
        this.rangeRight = v.findViewById(R.id.rangeRight)
        this.tvRange = v.findViewById(R.id.tvRange)
        this.tvPlot = v.findViewById(R.id.tvPlot)
        this.plotEt = v.findViewById(R.id.plot)
        this.rangeEt = v.findViewById(R.id.range)
        this.rangeName = v.findViewById(R.id.rangeName)
        this.plotName = v.findViewById(R.id.plotName)

        this.controller = context as CollectRangeController

        rangeID = this.controller.getDatabase().allRangeID
        cRange = RangeObject()
        cRange.plot = ""
        cRange.plot_id = ""
        cRange.range = ""
        lastRange = ""
        firstName = controller.getPreferences().getString(GeneralKeys.PRIMARY_NAME, "") ?: ""
        secondName = controller.getPreferences().getString(GeneralKeys.SECONDARY_NAME, "") ?: ""
        uniqueName = controller.getPreferences().getString(GeneralKeys.UNIQUE_NAME, "") ?: ""
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

    fun getRangeID(): IntArray {
        return rangeID
    }

    fun getRangeIDByIndex(j: Int): Int {
        return rangeID[j]
    }

    fun getRangeLeft(): ImageView? {
        return rangeLeft
    }

    fun getRangeRight(): ImageView? {
        return rangeRight
    }

    fun getPlotID(): String? {
        return cRange.plot_id
    }

    fun isEmpty(): Boolean {
        return cRange.plot_id.isEmpty()
    }

    fun connectTraitBox(traitBoxView: TraitBoxView) {

        //determine range button function based on user-preferences
        //issues217 introduces the ability to swap trait and plot arrows
        val flipFlopArrows =
            controller.getPreferences().getBoolean(GeneralKeys.FLIP_FLOP_ARROWS, false)
        if (flipFlopArrows) {
            rangeLeft = traitBoxView.getTraitLeft()
            rangeRight = traitBoxView.getTraitRight()
        } else {
            rangeLeft = findViewById(R.id.rangeLeft)
            rangeRight = findViewById(R.id.rangeRight)
        }

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
        rangeName.setOnTouchListener { _, _ ->
            Utils.makeToast(
                context,
                controller.getPreferences().getString(
                    GeneralKeys.PRIMARY_NAME,
                    context.getString(R.string.search_results_dialog_range)
                )
            )
            false
        }

        //TODO https://stackoverflow.com/questions/47107105/android-button-has-setontouchlistener-called-on-it-but-does-not-override-perform
        plotName.setOnTouchListener { v: View, _: MotionEvent? ->
            Utils.makeToast(
                context,
                controller.getPreferences().getString(
                    GeneralKeys.SECONDARY_NAME,
                    context.getString(R.string.search_results_dialog_range)
                )
            )
            v.performClick()
        }
    }

    private fun repeatUpdate() {

        controller.getTraitBox().setNewTraits(getPlotID())

    }

    private fun truncate(s: String, maxLen: Int): String? {
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

        //change click-arrow based on preferences
        val flipFlopArrows =
            controller.getPreferences().getBoolean(GeneralKeys.FLIP_FLOP_ARROWS, false)
        return if (flipFlopArrows) {
            createOnTouchListener(
                rangeLeft, actionLeft,
                R.drawable.trait_chevron_left_pressed,
                R.drawable.trait_chevron_left
            )
        } else {
            createOnTouchListener(
                rangeLeft, actionLeft,
                R.drawable.chevron_left_pressed,
                R.drawable.chevron_left
            )
        }
    }

    private fun createOnRightTouchListener(): OnTouchListener {
        val actionRight = createRunnable("right")

        //change click-arrow based on preferences
        val flipFlopArrows =
            controller.getPreferences().getBoolean(GeneralKeys.FLIP_FLOP_ARROWS, false)
        return if (flipFlopArrows) {
            createOnTouchListener(
                rangeRight, actionRight,
                R.drawable.trait_chevron_right_pressed,
                R.drawable.trait_chevron_right
            )
        } else {
            createOnTouchListener(
                rangeRight, actionRight,
                R.drawable.chevron_right_pressed,
                R.drawable.chevron_right
            )
        }
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
        if (!controller.validateData()) {
            return
        }
        if (rangeID.isNotEmpty()) {
            val step = if (left) -1 else 1
            paging = movePaging(paging, step, true, false)

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
        if (firstName.isNotEmpty() && secondName.isNotEmpty() && uniqueName.isNotEmpty()) {
            cRange = controller.getDatabase().getRange(firstName, secondName, uniqueName, id)
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

        firstName = controller.getPreferences().getString(GeneralKeys.PRIMARY_NAME, "") ?: ""
        secondName = controller.getPreferences().getString(GeneralKeys.SECONDARY_NAME, "") ?: ""
        uniqueName = controller.getPreferences().getString(GeneralKeys.UNIQUE_NAME, "") ?: ""

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
        if (!controller.validateData()) {
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
        if (!controller.validateData()) {
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
                // In addtion to advancing the entry, return to the first trait in the trait order if the preference is enabled
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
        return movePaging(pos, -1, cyclic = false, fromToolbar = false)
    }

    private fun incrementPaging(pos: Int): Int {
        return movePaging(pos, 1, cyclic = false, fromToolbar = false)
    }

//    private fun chooseNextTrait(pos: Int, step: Int) {
//        val nextTrait = controller.getNonExistingTraits(rangeID[pos - 1])
//        if (nextTrait.isNotEmpty()) {
//            if (step < 0) {
//                controller.getTraitBox().setSelection(Collections.max(nextTrait))
//            } else controller.getTraitBox().setSelection(Collections.min(nextTrait))
//        }
//    }

    private fun getTraitIndex(traits: Array<String>): Int {
        val currentTraitName: String? = controller.getTraitBox().currentTrait?.trait
        var traitIndex = 0
        for (i in traits.indices) {
            if (currentTraitName == traits[i]) {
                traitIndex = i
                break
            }
        }
        return traitIndex
    }

    private fun checkSkipTraits(
        traits: Array<String>,
        step: Int,
        p: Int,
        cyclic: Boolean,
        skipMode: Boolean
    ): Int {

        //edge case where we are on the last position
        //check for missing traits dependent on step for last position
        //if all traits are observed or the only unobserved is to the left, move to pos 1
        var pos = p
        if (step == 1 && pos == rangeID.size) {
            if (!skipMode) {
                val currentTrait = getTraitIndex(traits)
                val nextTrait = controller.existsAllTraits(currentTrait, rangeID[pos - 1])
                if (nextTrait != -1) { //check if this trait is "next" if not then move to 1
                    if (nextTrait > currentTrait) {
                        controller.getTraitBox().setSelection(nextTrait)
                        return rangeID.size
                    } else { //when moving to one, select the non existing trait
                        val nextTraitOnFirst = controller.getNonExistingTraits(rangeID[0])
                        if (nextTraitOnFirst.isNotEmpty()) {
                            controller.getTraitBox().setSelection(Collections.min(nextTraitOnFirst))
                            return 1
                        }
                    } //if all traits exist for 1 then just follow the main loop
                }
            }
        }
        val prevPos = pos
        //first loop is used to detect if all observations are completed
        var firstLoop = true
        //this keeps track of the previous loops position
        //while prevPos keeps track of what position this function was called with.
        var localPrev: Int
        while (true) {

            //get the index of the currently selected trait
            val traitIndex = getTraitIndex(traits)
            localPrev = pos
            pos = moveSimply(pos, step)

            //if we wrap around the entire range then observations are completed
            //notify the user and just go to the first range id.
            if (!firstLoop && prevPos == localPrev) {
                return 1
            }
            firstLoop = false

            // absorb the differece
            // between single click and repeated clicks
            if (cyclic) {
                when (pos) {
                    prevPos -> {
                        return pos
                    }
                    1 -> {
                        pos = rangeID.size
                    }
                    rangeID.size -> {
                        pos = 1
                    }
                }
            } else {
                if (pos == 1 || pos == prevPos) {
                    if (!skipMode) {
                        val nextTrait = controller.getNonExistingTraits(rangeID[pos - 1])
                        if (nextTrait.isNotEmpty()) {
                            if (step < 0) {
                                controller.getTraitBox().setSelection(Collections.max(nextTrait))
                            } else controller.getTraitBox().setSelection(Collections.min(nextTrait))
                            return pos
                        }
                    }
                }
            }
            if (skipMode) {
                if (!controller.existsTrait(rangeID[pos - 1])) {
                    return pos
                }
            } else {

                //check all traits for the currently selected range id
                //this returns the missing trait index or -1 if they all are observed
                val nextTrait = controller.existsAllTraits(traitIndex, rangeID[localPrev - 1])
                //if we press right, but a trait to the left is missing, go to next plot
                //similarly if we press left, but a trait to the right is missing, go to previous
                //check if pressing left/right will skip an unobserved trait
                //if it does, force it to the next plot and set the traitBox to the first unobserved
                //boolean skipped = Math.abs(prevPos - localPrev) > 1;
                if (nextTrait < traitIndex && step > 0) {

                    //check which trait is missing in the next position
                    val nextPlotTrait = controller.getNonExistingTraits(rangeID[pos - 1])

                    //if no trait is missing, loop
                    if (nextPlotTrait.isNotEmpty()) { //otherwise set the selection and return position

                        //we are moving to the right, so set the left most trait
                        controller.getTraitBox().setSelection(
                            Collections.min(nextPlotTrait)
                        )
                        return pos
                    }
                } else if ((nextTrait == -1 || nextTrait > traitIndex) && step < 0) {

                    //check which trait is missing in the next position
                    val nextPlotTrait = controller.getNonExistingTraits(rangeID[pos - 1])

                    //if no trait is missing, loop
                    if (nextPlotTrait.isNotEmpty()) { //otherwise set the selection and return position

                        //moving to the left so set the right most trait
                        controller.getTraitBox().setSelection(
                            Collections.max(nextPlotTrait)
                        )
                        return pos
                    }
                    //otherwise, set the selection to the missing trait and return the current pos
                } else if (nextTrait > -1) {
                    controller.getTraitBox().setSelection(nextTrait)
                    return localPrev
                }
            }
        }
    }

    fun movePaging(pos: Int, step: Int, cyclic: Boolean, fromToolbar: Boolean): Int {
        // If ignore existing data is enabled, then skip accordingly
        val traits = controller.getDatabase().visibleTrait
        //three options: 1. disabled 2. skip active trait 3. skip but check all traits
        var skipMode =
            controller.getPreferences().getString(GeneralKeys.HIDE_ENTRIES_WITH_DATA, "1")
        if (fromToolbar) {
            skipMode = controller.getPreferences()
                .getString(GeneralKeys.HIDE_ENTRIES_WITH_DATA_TOOLBAR, "1")
        }
        return when (skipMode) {
            "2" -> {
                checkSkipTraits(traits, step, pos, cyclic, true)
            }
            "3" -> {
                checkSkipTraits(traits, step, pos, cyclic, false)
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

//    fun resetPaging() {
//        paging = 1
//    }

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