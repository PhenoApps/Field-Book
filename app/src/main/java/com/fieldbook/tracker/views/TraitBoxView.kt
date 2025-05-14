package com.fieldbook.tracker.views

import android.app.AlertDialog
import android.app.Service
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat.startActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.TraitEditorActivity
import com.fieldbook.tracker.adapters.TraitsStatusAdapter
import com.fieldbook.tracker.interfaces.CollectTraitController
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.preferences.PreferenceKeys
import com.fieldbook.tracker.traits.BaseTraitLayout
import com.fieldbook.tracker.traits.LayoutCollections
import androidx.core.content.edit
import com.fieldbook.tracker.activities.CollectActivity


class TraitBoxView : ConstraintLayout {

    private var controller: CollectTraitController
    private var prefixTraits: Array<String>

    private var traitTypeTv: TextView
    private var traitDetails: TextView
    private var traitLeft: ImageView
    private var traitRight: ImageView

    private var traitsStatusBarRv: RecyclerView? = null
    private var traitBoxItemModels: List<TraitsStatusAdapter.TraitBoxItemModel>? = null
    private var visibleTraitsList: Array<TraitObject> = arrayOf()

    var currentTrait: TraitObject? = null
    var rangeSuppress: Boolean? = null

    init {

        val v = inflate(context, R.layout.view_trait_box, this)

        this.controller = context as CollectTraitController

        traitTypeTv = findViewById(R.id.traitTypeTv)
        traitDetails = v.findViewById(R.id.traitDetails)
        traitLeft = v.findViewById(R.id.traitLeft)
        traitRight = v.findViewById(R.id.traitRight)

        traitsStatusBarRv = v.findViewById(R.id.traitsStatusBarRv)

        prefixTraits = controller.getDatabase().rangeColumnNames

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

        traitLeft = findViewById(R.id.traitLeft)
        traitRight = findViewById(R.id.traitRight)
        traitDetails = findViewById(R.id.traitDetails)

        traitLeft.setImageResource(R.drawable.trait_chevron_left)
        traitLeft.setOnTouchListener(
            createTraitOnTouchListener(
                traitLeft, R.drawable.trait_chevron_left,
                R.drawable.trait_chevron_left_pressed
            )
        )

        // Go to previous trait
        traitLeft.setOnClickListener { moveTrait("left") }

        traitRight.setImageResource(R.drawable.trait_chevron_right)
        traitRight.setOnTouchListener(
            createTraitOnTouchListener(
                traitRight, R.drawable.trait_chevron_right,
                R.drawable.trait_chevron_right_pressed
            )
        )

        // Go to next trait
        traitRight.setOnClickListener { moveTrait("right") }

        traitsStatusBarRv?.adapter = TraitsStatusAdapter(this)
        traitsStatusBarRv?.layoutManager = getCenteredLayoutManager()
    }

    fun initTraitDetails() {
        val traitDetails: TextView = findViewById(R.id.traitDetails)
        traitDetails.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> traitDetails.maxLines = 10
                MotionEvent.ACTION_UP -> traitDetails.maxLines = 1
            }
            view?.performClick()
            true
        }
    }

    fun initialize(
        visibleTraits: Array<TraitObject>,
        rangeSuppress: Boolean
    ) {
        this.visibleTraitsList = visibleTraits
        this.rangeSuppress = rangeSuppress

        // navigate to the last used trait using preferences
        // if using for the first time, use the first element
        val traitId = controller.getPreferences().getString(
            GeneralKeys.LAST_USED_TRAIT,
            "-1"
        ) ?: "-1"

        if (traitId != "-1") {
            visibleTraits.find { it.id == traitId }?.let { trait ->
                currentTrait = trait
            }
        }

        loadLayout(rangeSuppress)

        traitTypeTv.setOnClickListener {
            // Display dialog or menu for trait selection
            showTraitPickerDialog(visibleTraits)
        }

        updateTraitsStatusBar()

    }

    fun getRecyclerView(): RecyclerView? {
        return traitsStatusBarRv
    }

    private var previousSelection = 0

    private fun getSelectedItemPosition(): Int {
        return visibleTraitsList.indexOf(currentTrait)
    }

    fun loadLayout(rangeSuppress: Boolean, skipSelection: Boolean = false) {

        val traitPosition = getSelectedItemPosition()

        if (!skipSelection) {
            setSelection(traitPosition)
        }

        traitsStatusBarRv?.adapter?.notifyItemChanged(previousSelection)
        traitsStatusBarRv?.adapter?.notifyItemChanged(traitPosition)

        previousSelection = traitPosition

        if (currentTrait != null) {
            // Update last used trait so it is preserved when entry moves
            controller.getPreferences().edit {
                putString(
                    GeneralKeys.LAST_USED_TRAIT,
                    currentTrait!!.id
                )
            }
        }

        traitTypeTv.text = currentTrait?.name

        val imm =
            context.getSystemService(Service.INPUT_METHOD_SERVICE) as InputMethodManager

        if (currentTrait?.format != "text") {

            try {
                imm.hideSoftInputFromWindow(controller.getInputView().windowToken, 0)
            } catch (_: Exception) { }
        }

        traitDetails.text = currentTrait?.details ?: ""

        //TODO: move these to individual trait layouts, check how 'rangeSuppresse' is used
        if (!rangeSuppress or (currentTrait?.format != "numeric")) {
            if (controller.getInputView().visibility == VISIBLE) {
                controller.getInputView().visibility = GONE
                controller.getInputView().isEnabled = false
            }
        }

        //Get current layout object and make it visible
        val layoutCollections: LayoutCollections = controller.getTraitLayouts()

        val currentTraitLayout: BaseTraitLayout = layoutCollections.getTraitLayout(currentTrait?.format)

        controller.inflateTrait(currentTraitLayout)

        //Call specific load layout code for the current trait layout
        if (currentTraitLayout != null) {
            currentTraitLayout.loadLayout()
        } else {
            controller.getInputView().visibility = VISIBLE
            controller.getInputView().isEnabled = true
        }
    }

    private fun showTraitPickerDialog(visibleTraits: Array<TraitObject>) {
        val builder = AlertDialog.Builder(context, R.style.AppAlertDialog)

        builder.setTitle(R.string.select_trait)
            .setCancelable(true)
            .setSingleChoiceItems(visibleTraits.map { it.name }.toTypedArray(), getSelectedItemPosition()) { dialog, index ->
                // Update selected trait
                currentTrait = visibleTraits[index]
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

    private fun updateTraitsStatusBar() {

        // images saved are not stored in newTraits hashMap
        // get the data for current plot_id again
        val studyId = (context as CollectActivity).studyId
        val plotId = (context as CollectActivity).observationUnit

        traitBoxItemModels = visibleTraitsList.map { trait ->

            val exists = controller.getDatabase().getAllObservations(
                studyId,
                plotId,
                trait.id
            ).isNotEmpty()

            TraitsStatusAdapter.TraitBoxItemModel(
                trait.name,
                exists
            )
        }

        (traitsStatusBarRv?.adapter as TraitsStatusAdapter).submitList(traitBoxItemModels)

        // the recyclerView height was 0 initially, so calculate the icon size again
        recalculateTraitStatusBarSizes()
    }

    fun existsTrait(): Boolean {
        val studyId = (context as CollectActivity).studyId
        val plotId = (context as CollectActivity).observationUnit
        val traitId = currentTrait?.id ?: "-1"

        return if (traitId == "-1" || studyId == null || plotId == null) {
            false
        } else controller.getDatabase().getAllObservations(studyId, plotId, traitId).isNotEmpty()
    }

    fun recalculateTraitStatusBarSizes() {
        traitsStatusBarRv?.post {
            for (pos in 0 until (traitsStatusBarRv?.adapter?.itemCount ?: 0)) {
                val viewHolder = traitsStatusBarRv?.findViewHolderForAdapterPosition(pos) as? TraitsStatusAdapter.ViewHolder
                viewHolder?.let {
                    (traitsStatusBarRv?.adapter as? TraitsStatusAdapter)?.calculateAndSetItemSize(it)
                }
            }
        }
    }

    fun getTraitLeft(): ImageView {
        return findViewById<ImageView>(R.id.traitLeft)
    }

    fun getTraitRight(): ImageView {
        return findViewById<ImageView>(R.id.traitRight)
    }

    fun setPrefixTraits() {
        prefixTraits = controller.getDatabase().rangeColumnNames
    }

    fun setSelection(pos: Int) {
        // if pos is -1, default back to first element
        // pos = -1 if the last used trait was disabled
        currentTrait = visibleTraitsList[if (pos == -1) 0 else pos]

        loadLayout(false, skipSelection = true)

        (traitsStatusBarRv?.adapter as TraitsStatusAdapter).setCurrentSelection(pos)
    }

    fun getCurrentFormat(): String {
        return currentTrait!!.format
    }

    /**
     * Deletes all observation variables named traitName from the db.
     * Also removes the trait from "newTraits"
     * @param traitName the observation variable name
     * @param plotID the unique plot identifier to remove the observations from
     */
    fun remove(trait: TraitObject, plotID: String, rep: String) {
        val studyId =
            controller.getPreferences().getInt(GeneralKeys.SELECTED_FIELD_ID, 0).toString()
        controller.getDatabase().deleteTrait(studyId, plotID, trait.id, rep)
    }

    private fun createTraitOnTouchListener(
        arrow: ImageView,
        imageIdUp: Int, imageIdDown: Int
    ): OnTouchListener {
        return OnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    arrow.setImageResource(imageIdDown)
                }
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
        if (!controller.validateData(controller.getCurrentObservation()?.value)) {
            return
        }

        val rangeBox = controller.getRangeBox()
        if (direction == "left") {
            pos = getSelectedItemPosition() - 1
            if (pos < 0) {
                pos = visibleTraitsList.count() - 1
                if (controller.isCyclingTraitsAdvances()) {
                    rangeBox.clickLeft()
                }
                if (controller.getPreferences().getBoolean(PreferenceKeys.CYCLE_TRAITS_SOUND, false)) {
                    controller.getSoundHelper().playCycle()
                }
            }
        } else if (direction == "right") {
            pos = getSelectedItemPosition() + 1
            if (pos > visibleTraitsList.count() - 1) {
                pos = 0
                if (controller.isCyclingTraitsAdvances()) {
                    rangeBox.clickRight()
                }
                if (controller.getPreferences().getBoolean(PreferenceKeys.CYCLE_TRAITS_SOUND, false)) {
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
        if (controller.getPreferences().getBoolean(PreferenceKeys.CYCLE_TRAITS_SOUND, false)) {
            controller.getSoundHelper().playCycle()
        }
        setSelection(0)
        rangeSuppress?.let { loadLayout(it) }
        controller.refreshLock()
        controller.getCollectInputView().resetInitialIndex()
    }

    private fun getCenteredLayoutManager(): LinearLayoutManager {
        return object : LinearLayoutManager(context, HORIZONTAL, false) {
            override fun canScrollHorizontally(): Boolean { // disable scrolling
                return false
            }

            // center all the items
            override fun onLayoutChildren(recycler: RecyclerView.Recycler?, state: RecyclerView.State?) {
                super.onLayoutChildren(recycler, state)

                if (childCount > 0 && width > 0) {
                    val totalWidthNeeded = getChildAt(0)?.let { getDecoratedMeasurementHorizontal(it) * childCount }

                    if (totalWidthNeeded != null && totalWidthNeeded < width) {
                        val leftPadding = (width - totalWidthNeeded) / 2 // equal padding on left and right
                        offsetChildrenHorizontal(leftPadding) // offset from left
                    }
                }
            }

            // get the full horizontal measurement
            private fun getDecoratedMeasurementHorizontal(child: View): Int {
                val params = child.layoutParams as RecyclerView.LayoutParams
                return getDecoratedMeasuredWidth(child) + params.leftMargin + params.rightMargin
            }
        }
    }
}