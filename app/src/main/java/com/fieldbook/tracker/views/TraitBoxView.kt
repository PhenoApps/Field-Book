package com.fieldbook.tracker.views

import android.app.AlertDialog
import android.app.Service
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View.OnTouchListener
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat.startActivity
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.TraitEditorActivity
import com.fieldbook.tracker.interfaces.CollectTraitController
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.traits.BaseTraitLayout
import com.fieldbook.tracker.traits.LayoutCollections


class TraitBoxView : ConstraintLayout {

    private var controller: CollectTraitController
    private var prefixTraits: Array<String>

    private var traitTypeTv: TextView
    private var traitDetails: TextView
    private var traitLeft: ImageView
    private var traitRight: ImageView

    private var traitsProgressBar: ProgressBar

    var currentTrait: TraitObject? = null

    private var visibleTraitsList: Array<String>? = null
    private var rangeSuppress: Boolean? = null

    /**
     * New traits is a map of observations where the key is the trait name
     * and the value is the observation value. This is updated whenever
     * a new plot id is navigated to.
     */
    private var newTraits: HashMap<String, String> = hashMapOf()

    init {

        val v = inflate(context, R.layout.view_trait_box, this)

        this.controller = context as CollectTraitController

        traitTypeTv = findViewById(R.id.traitTypeTv)
        traitDetails = v.findViewById(R.id.traitDetails)
        traitLeft = v.findViewById(R.id.traitLeft)
        traitRight = v.findViewById(R.id.traitRight)

        prefixTraits = controller.getDatabase().rangeColumnNames
        newTraits = HashMap()

        traitsProgressBar = findViewById(R.id.traitsProgressBar)
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
            traitLeft = rangeBoxView.getRangeLeft()
            traitRight = rangeBoxView.getRangeRight()
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
        visibleTraits: Array<String>?,
        rangeSuppress: Boolean
    ) {
        this.visibleTraitsList = visibleTraits
        this.rangeSuppress = rangeSuppress

        // navigate to the last used trait using preferences
        // if using for the first time, use the first element
        traitTypeTv.text = controller.getPreferences().getString(GeneralKeys.LAST_USED_TRAIT,
            visibleTraits?.get(0)
        )

        loadLayout(rangeSuppress)

        traitTypeTv.setOnClickListener {
            // Display dialog or menu for trait selection
            showTraitPickerDialog(visibleTraits)
        }
    }

    private fun loadLayout(rangeSuppress: Boolean){
        val traitPosition = getSelectedItemPosition()

        setSelection(traitPosition)

        // This updates the in memory hashmap from database
        currentTrait = controller.getDatabase().getDetail(
            traitTypeTv.text
                .toString()
        )

        // Update last used trait so it is preserved when entry moves
        controller.getPreferences().edit().putString(GeneralKeys.LAST_USED_TRAIT,traitTypeTv.text.toString()).apply()
        traitTypeTv.text = currentTrait?.name


        val imm =
            context.getSystemService(Service.INPUT_METHOD_SERVICE) as InputMethodManager
        if (currentTrait?.format != "text") {

            try {
                imm.hideSoftInputFromWindow(controller.getInputView().windowToken, 0)
            } catch (ignore: Exception) {
            }
        }
        traitDetails.text = currentTrait?.details
        if (!rangeSuppress or (currentTrait?.format != "numeric")) {
            if (controller.getInputView().visibility == VISIBLE) {
                controller.getInputView().visibility = GONE
                controller.getInputView().isEnabled = false
            }
        }

        //Clear all layouts
        //controller.getTraitLayouts().hideLayouts()

        //Get current layout object and make it visible
        val layoutCollections: LayoutCollections =
            controller.getTraitLayouts()

        val currentTraitLayout: BaseTraitLayout = layoutCollections.getTraitLayout(currentTrait?.format)

        controller.inflateTrait(currentTraitLayout)

        //currentTraitLayout.visibility = VISIBLE

        //Call specific load layout code for the current trait layout
        if (currentTraitLayout != null) {
            currentTraitLayout.loadLayout()
        } else {
            controller.getInputView().visibility = VISIBLE
            controller.getInputView().isEnabled = true
        }

        updateTraitProgressBar()
    }

    private fun showTraitPickerDialog(visibleTraits: Array<String>?) {
        val builder = AlertDialog.Builder(context, R.style.AppAlertDialog)

        builder.setTitle(R.string.select_trait)
            .setCancelable(true)
            .setSingleChoiceItems(visibleTraits, getSelectedItemPosition()) { dialog, index ->
                // Update selected trait
                traitTypeTv.text = visibleTraits?.get(index) ?: ""
                rangeSuppress?.let { loadLayout(it) }
                dialog.dismiss()
            }
            .setPositiveButton(
                android.R.string.ok
            ) {
                    d: DialogInterface, _: Int -> d.dismiss()
            }
            .setNeutralButton(
                R.string.edit_traits
            ) {
                    _: DialogInterface, _: Int ->
                    val intent = Intent(context, TraitEditorActivity::class.java)
                    startActivity(context, intent, null)
            }
        val dialog = builder.create()
        dialog.show()

    }

    private fun updateTraitProgressBar() {
        var traits = controller.getDatabase().allTraitObjects

        // a new trait object is made while assigning to currentTrait
        // so instead of finding the index of currentTrait object
        // we find the index of the trait name
        val visibleTraits = ArrayList<String>()
        for (traitObject in traits) {
            if (traitObject.visible) {
                visibleTraits.add(traitObject.name)
            }
        }

        traitsProgressBar.max = visibleTraits.size
        traitsProgressBar.progress = visibleTraits.indexOf(currentTrait?.name) + 1
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
        return findViewById<ImageView>(R.id.traitLeft)
    }

    fun getTraitRight(): ImageView {
        return findViewById<ImageView>(R.id.traitRight)
    }

    fun existsNewTraits(): Boolean {
        return newTraits != null
    }

    fun setPrefixTraits() {
        prefixTraits = controller.getDatabase().rangeColumnNames
    }

    fun setSelection(pos: Int) {
        // if pos is -1, default back to first element
        // pos = -1 if the last used trait was disabled
        traitTypeTv.text =  visibleTraitsList?.get(if (pos == -1) 0 else pos)
    }

    private fun getSelectedItemPosition(): Int {
        return try {
            // if the list does not contain the trait, default back to first element
            if (visibleTraitsList!!.contains(traitTypeTv.text)) visibleTraitsList?.indexOf(traitTypeTv.text.toString()) ?: 0 else 0
        } catch (f: Exception) {
            0
        }
    }

    fun getCurrentFormat(): String {
        return currentTrait!!.format
    }

    fun existsTrait(): Boolean {
        return newTraits.containsKey(currentTrait!!.name)
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
        val traitDbId = controller.getDatabase().getTraitByName(traitName).id
        controller.getDatabase().deleteTrait(studyId, plotID, traitDbId, rep)
    }

    fun remove(trait: TraitObject, plotID: String, rep: String) {
        remove(trait.name, plotID, rep)
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
        // if visibleTraitsList is null
        // don't move the trait
        // as we won't get the length of the list
        if (visibleTraitsList == null) return

        var pos = 0
        if (!controller.validateData()) {
            return
        }

        val rangeBox = controller.getRangeBox()
        if (direction == "left") {
            pos = getSelectedItemPosition() - 1
            if (pos < 0) {
                pos = visibleTraitsList!!.count() - 1
                if (controller.isCyclingTraitsAdvances()) {
                    rangeBox.clickLeft()
                }
                if (controller.getPreferences().getBoolean(GeneralKeys.CYCLE_TRAITS_SOUND, false)) {
                    controller.getSoundHelper().playCycle()
                }
            }
        } else if (direction == "right") {
            pos = getSelectedItemPosition() + 1
            if (pos > visibleTraitsList!!.count() - 1) {
                pos = 0
                if (controller.isCyclingTraitsAdvances()) {
                    rangeBox.clickRight()
                }
                if (controller.getPreferences().getBoolean(GeneralKeys.CYCLE_TRAITS_SOUND, false)) {
                    controller.getSoundHelper().playCycle()
                }
            }
        }
//        traitType.setSelection(pos)
        setSelection(pos)
        rangeSuppress?.let { loadLayout(it) }
        controller.refreshLock()
        controller.getCollectInputView().resetInitialIndex()
    }

    fun returnFirst() {
        if (controller.getPreferences().getBoolean(GeneralKeys.CYCLE_TRAITS_SOUND, false)) {
            controller.getSoundHelper().playCycle()
        }
        setSelection(0)
        rangeSuppress?.let { loadLayout(it) }
        controller.refreshLock()
        controller.getCollectInputView().resetInitialIndex()
    }

    fun update(parent: String?, value: String) {
        if (newTraits.containsKey(parent!!)) {
            newTraits.remove(parent)
        }
        newTraits[parent] = value
    }
}