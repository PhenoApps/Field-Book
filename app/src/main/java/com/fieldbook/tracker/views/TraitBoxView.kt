package com.fieldbook.tracker.views

import android.app.Service
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import com.fieldbook.tracker.R
import com.fieldbook.tracker.interfaces.CollectTraitController
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.traits.BaseTraitLayout

class TraitBoxView : ConstraintLayout {

    private var controller: CollectTraitController
    private var prefixTraits: Array<String>

    private var traitType: Spinner
    private var traitDetails: TextView
    private var traitLeft: ImageView
    private var traitRight: ImageView

    var currentTrait: TraitObject? = null

    /**
     * New traits is a map of observations where the key is the trait name
     * and the value is the observation value. This is updated whenever
     * a new plot id is navigated to.
     */
    private var newTraits: HashMap<String, String> = hashMapOf()

    init {

        val v = inflate(context, R.layout.view_trait_box, this)

        this.controller = context as CollectTraitController

        traitType = v.findViewById(R.id.traitType)
        traitDetails = v.findViewById(R.id.traitDetails)
        traitLeft = v.findViewById(R.id.traitLeft)
        traitRight = v.findViewById(R.id.traitRight)

        prefixTraits = controller.getDatabase().rangeColumnNames
        newTraits = HashMap()
        traitType = findViewById(R.id.traitType)
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

    fun connectRangeBox(rangeBoxView: RangeBoxView) {

        //determine trait button function based on user-preferences
        //issues217 introduces the ability to swap trait and plot arrows
        val flipFlopArrows: Boolean =
            controller.getPreferences().getBoolean(GeneralKeys.FLIP_FLOP_ARROWS, false)
        if (flipFlopArrows) {
            traitLeft = rangeBoxView.getRangeLeft()!!
            traitRight = rangeBoxView.getRangeRight()!!
        } else {
            traitLeft = findViewById(R.id.traitLeft)
            traitRight = findViewById(R.id.traitRight)
        }
        traitDetails = findViewById(R.id.traitDetails)

        //change click-arrow based on preferences
        if (flipFlopArrows) {
            traitLeft.setImageResource(R.drawable.chevron_left)
            traitLeft.setOnTouchListener(
                createTraitOnTouchListener(
                    traitLeft, R.drawable.chevron_left,
                    R.drawable.chevron_left_pressed
                )
            )
        } else {
            traitLeft.setImageResource(R.drawable.trait_chevron_left)
            traitLeft.setOnTouchListener(
                createTraitOnTouchListener(
                    traitLeft, R.drawable.trait_chevron_left,
                    R.drawable.trait_chevron_left_pressed
                )
            )
        }

        // Go to previous trait
        traitLeft.setOnClickListener { moveTrait("left") }

        //change click-arrow based on preferences
        if (flipFlopArrows) {
            traitRight.setImageResource(R.drawable.chevron_right)
            traitRight.setOnTouchListener(
                createTraitOnTouchListener(
                    traitRight, R.drawable.chevron_right,
                    R.drawable.chevron_right_pressed
                )
            )
        } else {
            traitRight.setImageResource(R.drawable.trait_chevron_right)
            traitRight.setOnTouchListener(
                createTraitOnTouchListener(
                    traitRight, R.drawable.trait_chevron_right,
                    R.drawable.trait_chevron_right_pressed
                )
            )
        }

        // Go to next trait
        traitRight.setOnClickListener { moveTrait("right") }
    }

    fun initTraitDetails() {
        val traitDetails: TextView = findViewById(R.id.traitDetails)
        traitDetails.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> traitDetails.maxLines = 10
                MotionEvent.ACTION_UP -> traitDetails.maxLines = 1
            }
            true
        }
    }

    fun initTraitType(
        adaptor: ArrayAdapter<String?>?,
        rangeSuppress: Boolean
    ) {
        val traitPosition = getSelectedItemPosition()
        traitType.adapter = adaptor
        traitType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                arg0: AdapterView<*>?, arg1: View?,
                arg2: Int, arg3: Long
            ) {

                // This updates the in memory hashmap from database
                currentTrait = controller.getDatabase().getDetail(
                    traitType.selectedItem
                        .toString()
                )

                val imm =
                    context.getSystemService(Service.INPUT_METHOD_SERVICE) as InputMethodManager
                if (currentTrait!!.format != "text") {
                    try {
                        imm.hideSoftInputFromWindow(controller.getInputView().windowToken, 0)
                    } catch (ignore: Exception) {
                    }
                }
                traitDetails.text = currentTrait!!.details
                if (!rangeSuppress or (currentTrait!!.format != "numeric")) {
                    if (controller.getInputView().visibility == VISIBLE) {
                        controller.getInputView().visibility = GONE
                        controller.getInputView().isEnabled = false
                    }
                }

                //Clear all layouts
                controller.getTraitLayouts().hideLayouts()

                //Get current layout object and make it visible
                val currentTraitLayout: BaseTraitLayout =
                    controller.getTraitLayouts().getTraitLayout(currentTrait!!.format)
                currentTraitLayout.visibility = VISIBLE

                //Call specific load layout code for the current trait layout
                if (currentTraitLayout != null) {
                    currentTraitLayout.loadLayout()
                } else {
                    controller.getInputView().visibility = VISIBLE
                    controller.getInputView().isEnabled = true
                }
            }

            override fun onNothingSelected(arg0: AdapterView<*>?) {}
        }

        setSelection(traitPosition)
    }

    fun getNewTraits(): Map<String, String> {
        return newTraits
    }

    /**
     * Called when navigating between plots in collect activity.
     * New Traits hashmap of <trait name to observation value> stores data for the currently
     * selected plot id.
     * @param plotID the new plot id we are transitioning to
    </trait> */
    fun setNewTraits(plotID: String?) {
        newTraits = controller.getDatabase().getUserDetail(plotID)
    }

    fun setNewTraits(newTraits: Map<String, String>) {
        val temp = hashMapOf<String, String>()
        newTraits.forEach { (k, v) -> temp[k] = v }
        this.newTraits = temp
    }

    fun getTraitLeft(): ImageView {
        return findViewById(R.id.traitLeft) as ImageView
    }

    fun getTraitRight(): ImageView {
        return findViewById(R.id.traitRight) as ImageView
    }

    fun existsNewTraits(): Boolean {
        return newTraits != null
    }

    fun setPrefixTraits() {
        prefixTraits = controller.getDatabase().rangeColumnNames
    }

    fun setSelection(pos: Int) {
        traitType.setSelection(pos)
    }

    private fun getSelectedItemPosition(): Int {
        return try {
            traitType.selectedItemPosition
        } catch (f: Exception) {
            0
        }
    }

    fun getCurrentFormat(): String {
        return currentTrait!!.format
    }

    fun existsTrait(): Boolean {
        return newTraits!!.containsKey(currentTrait!!.trait)
    }

    fun createSummaryText(plotID: String?): String {
        val traitList: Array<String> = controller.getDatabase().allTraits
        val data = StringBuilder()

        for (s in prefixTraits) {
            data.append(s).append(": ")
            data.append(controller.getDatabase().getDropDownRange(s, plotID)[0]).append("\n")
        }

        for (s in traitList) {
            if (newTraits.containsKey(s)) {
                data.append(s).append(": ")
                data.append(newTraits[s].toString()).append("\n")
            }
        }
        return data.toString()
    }

    /**
     * Deletes all observation variables named traitName from the db.
     * Also removes the trait from "newTraits"
     * @param traitName the observation variable name
     * @param plotID the unique plot identifier to remove the observations from
     */
    fun remove(traitName: String, plotID: String, rep: String) {
        if (newTraits.containsKey(traitName)) newTraits.remove(traitName)
        val studyId =
            controller.getPreferences().getInt(GeneralKeys.SELECTED_FIELD_ID, 0).toString()
        controller.getDatabase().deleteTrait(studyId, plotID, traitName, rep)
    }

    fun remove(trait: TraitObject, plotID: String, rep: String) {
        remove(trait.trait, plotID, rep)
    }

    private fun createTraitOnTouchListener(
        arrow: ImageView,
        imageIdUp: Int, imageIdDown: Int
    ): OnTouchListener {
        return OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> arrow.setImageResource(imageIdDown)
                MotionEvent.ACTION_MOVE -> {}
                MotionEvent.ACTION_UP -> arrow.setImageResource(imageIdUp)
                MotionEvent.ACTION_CANCEL -> {}
            }

            // return true to prevent calling btn onClick handler
            false
        }
    }

    fun moveTrait(direction: String) {
        var pos = 0
        if (!controller.validateData()) {
            return
        }

        val rangeBox = controller.getRangeBox()
        if (direction == "left") {
            pos = traitType.selectedItemPosition - 1
            if (pos < 0) {
                pos = traitType.count - 1
                if (controller.isCyclingTraitsAdvances()) {
                    rangeBox.clickLeft()
                }
                if (controller.getPreferences().getBoolean(GeneralKeys.CYCLE_TRAITS_SOUND, false)) {
                    controller.playSound("cycle")
                }
            }
        } else if (direction == "right") {
            pos = traitType.selectedItemPosition + 1
            if (pos > traitType.count - 1) {
                pos = 0
                if (controller.isCyclingTraitsAdvances()) {
                    rangeBox.clickRight()
                }
                if (controller.getPreferences().getBoolean(GeneralKeys.CYCLE_TRAITS_SOUND, false)) {
                    controller.playSound("cycle")
                }
            }
        }
        traitType.setSelection(pos)
        controller.refreshLock()
    }

    fun update(parent: String?, value: String) {
        if (newTraits.containsKey(parent!!)) {
            newTraits.remove(parent)
        }
        newTraits[parent] = value
    }
}