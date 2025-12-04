package com.fieldbook.tracker.views

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
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
import com.fieldbook.tracker.activities.CollectActivity
import androidx.core.content.edit
import com.fieldbook.tracker.utilities.Utils


class TraitBoxView : ConstraintLayout {

    enum class MoveDirection {
        LEFT,
        RIGHT
    }

    private val studyId: Int

    private var controller: CollectTraitController
    private var prefixTraits: Array<String>

    private var traitTypeTv: TextView
    private var traitDetails: TextView
    private var traitLeft: ActionImageView
    private var traitRight: ActionImageView

    private var traitsStatusBarRv: RecyclerView? = null
    private var traitBoxItemModels: List<TraitsStatusAdapter.TraitBoxItemModel>? = null
    private var visibleTraitsList: Array<TraitObject> = arrayOf()

    var currentTrait: TraitObject? = null

    init {

        val v = inflate(context, R.layout.view_trait_box, this)

        this.controller = context as CollectTraitController

        this.studyId = controller.getPreferences().getInt(GeneralKeys.SELECTED_FIELD_ID, 0)

        traitTypeTv = findViewById(R.id.traitTypeTv)
        traitDetails = v.findViewById(R.id.traitDetails)
        traitLeft = v.findViewById(R.id.traitLeft)
        traitRight = v.findViewById(R.id.traitRight)

        traitsStatusBarRv = v.findViewById(R.id.traitsStatusBarRv)

        prefixTraits = controller.getDatabase().getAllObservationUnitAttributeNames(studyId)

    }

    constructor(ctx: Context) : super(ctx)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    )

    fun connectRangeBox() {

        traitLeft = findViewById(R.id.traitLeft)
        traitRight = findViewById(R.id.traitRight)
        traitDetails = findViewById(R.id.traitDetails)

        traitLeft.setImageResource(R.drawable.trait_chevron_left)
        traitLeft.setOnActionListener(object : ActionImageView.OnActionListener {
            override fun onActionDown() {
                traitLeft.setImageResource(R.drawable.trait_chevron_left_pressed)
            }

            override fun onActionUp() {
                traitLeft.setImageResource(R.drawable.trait_chevron_left)
                // Go to previous trait
                moveTrait(MoveDirection.LEFT)
            }
        })

        traitRight.setImageResource(R.drawable.trait_chevron_right)
        traitRight.setOnActionListener(object : ActionImageView.OnActionListener {
            override fun onActionDown() {
                traitRight.setImageResource(R.drawable.trait_chevron_right_pressed)
            }

            override fun onActionUp() {
                traitRight.setImageResource(R.drawable.trait_chevron_right)
                // Go to next trait
                moveTrait(MoveDirection.RIGHT)
            }
        })

        if (controller.getPreferences().getBoolean(PreferenceKeys.TRAITS_PROGRESS_BAR, true)) {
            traitsStatusBarRv?.adapter = TraitsStatusAdapter(this)
            traitsStatusBarRv?.layoutManager = getCenteredLayoutManager()
            traitsStatusBarRv?.visibility = VISIBLE
        } else {
            traitsStatusBarRv?.visibility = GONE
        }

        updateTraitBoxArrows()
    }

    fun handleTraitTypeWrapping() {
        val isWordWrapEnabled = controller.getPreferences().getBoolean(GeneralKeys.TRAIT_TYPE_WORD_WRAP, false)
        applyWordWrapState(traitTypeTv, isWordWrapEnabled)
        traitTypeTv.setOnLongClickListener { view ->
            val newState = traitTypeTv.maxLines == 1
            applyWordWrapState(traitTypeTv, newState)

            val message = controller.getContext().getString(R.string.trait_box_word_wrap_toast,
                context.getString(if (newState) R.string.enabled else R.string.disabled))

            Utils.makeToast(controller.getContext(), message)

            controller.getPreferences().edit { putBoolean(GeneralKeys.TRAIT_TYPE_WORD_WRAP, newState) }

            true
        }
    }

    private fun applyWordWrapState(textView: TextView, isWordWrapEnabled: Boolean) {
        if (isWordWrapEnabled) {
            textView.maxLines = 10
            textView.ellipsize = null
        } else {
            textView.maxLines = 1
            textView.ellipsize = android.text.TextUtils.TruncateAt.END
        }
    }

    fun initTraitDetails() {
        val traitDetails: ActionTextView = findViewById(R.id.traitDetails)
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
    ) {
        this.visibleTraitsList = visibleTraits

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

        loadLayout()

        traitTypeTv.setOnClickListener {
            // Display dialog or menu for trait selection
            showTraitPickerDialog(visibleTraits)
        }

        updateTraitsStatusBar()
        updateTraitBoxArrows()
    }

    private fun updateTraitBoxArrows() { // hide arrows if only one trait is active
        val shouldShowArrows = visibleTraitsList.size > 1

        traitLeft.visibility = if (shouldShowArrows) VISIBLE else GONE
        traitRight.visibility = if (shouldShowArrows) VISIBLE else GONE
    }

    fun getRecyclerView(): RecyclerView? {
        return traitsStatusBarRv
    }

    private var previousSelection = 0

    private fun getSelectedItemPosition(): Int {
        return visibleTraitsList.indexOf(currentTrait)
    }

    fun loadLayout(skipSelection: Boolean = false) {

        val traitPosition = getSelectedItemPosition()

        if (!skipSelection) {
            setSelection(traitPosition)
        }

        if (controller.getPreferences().getBoolean(PreferenceKeys.TRAITS_PROGRESS_BAR, true)) {
            traitsStatusBarRv?.adapter?.notifyItemChanged(previousSelection)
            traitsStatusBarRv?.adapter?.notifyItemChanged(traitPosition)
        }

        previousSelection = traitPosition

        traitTypeTv.text = currentTrait?.alias
        traitDetails.text = currentTrait?.details ?: ""

        handleTraitTypeWrapping()

        //Get current layout object and make it visible
        val layoutCollections: LayoutCollections = controller.getTraitLayouts()

        val currentTraitLayout: BaseTraitLayout = layoutCollections.getTraitLayout(currentTrait?.format)

        controller.inflateTrait(currentTraitLayout)

        //Call specific load layout code for the current trait layout
        currentTraitLayout.loadLayout()
    }

    private fun showTraitPickerDialog(visibleTraits: Array<TraitObject>) {
        val builder = AlertDialog.Builder(context, R.style.AppAlertDialog)

        builder.setTitle(R.string.select_trait)
            .setCancelable(true)
            .setSingleChoiceItems(visibleTraits.map { it.alias }.toTypedArray(), getSelectedItemPosition()) { dialog, index ->
                // Update selected trait
                currentTrait = visibleTraits[index]
                loadLayout()
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

        if (!controller.getPreferences().getBoolean(PreferenceKeys.TRAITS_PROGRESS_BAR, true)) {
            traitsStatusBarRv?.visibility = GONE
            return
        }

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

        if (!controller.getPreferences().getBoolean(PreferenceKeys.TRAITS_PROGRESS_BAR, true)) {
            traitsStatusBarRv?.visibility = GONE
            return
        }

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
        prefixTraits = controller.getDatabase().getAllObservationUnitAttributeNames(studyId)
    }

    fun setSelection(pos: Int) {
        // if pos is -1, default back to first element
        // pos = -1 if the last used trait was disabled
        currentTrait = visibleTraitsList[if (pos == -1) 0 else pos]

        loadLayout(skipSelection = true)

        if (controller.getPreferences().getBoolean(PreferenceKeys.TRAITS_PROGRESS_BAR, true)) {
            (traitsStatusBarRv?.adapter as TraitsStatusAdapter).setCurrentSelection(pos)
        }
    }

    fun getCurrentFormat(): String {
        return currentTrait!!.format
    }

    /**
     * Deletes all observation variables with traitDbId from the db.
     * @param trait the observation variable
     * @param plotID the unique plot identifier to remove the observations from
     */
    fun remove(trait: TraitObject, plotID: String, rep: String) {
        val studyId =
            controller.getPreferences().getInt(GeneralKeys.SELECTED_FIELD_ID, 0).toString()
        controller.getDatabase().deleteTrait(studyId, plotID, trait.id, rep)
    }

    fun moveTrait(direction: MoveDirection) {

        controller.navigateIfDataIsValid(controller.getCurrentObservation()?.value) {
            var pos = 0

            val rangeBox = controller.getRangeBox()
            if (direction == MoveDirection.LEFT) {
                pos = getSelectedItemPosition() - 1
                if (pos < 0) {
                    pos = visibleTraitsList.count() - 1
                    if (controller.isCyclingTraitsAdvances()) {
                        rangeBox.clickLeft()
                    }
                    if (controller.getPreferences()
                            .getBoolean(PreferenceKeys.CYCLE_TRAITS_SOUND, false)
                    ) {
                        controller.getSoundHelper().playCycle()
                    }
                }
            } else if (direction == MoveDirection.RIGHT) {
                pos = getSelectedItemPosition() + 1
                if (pos > visibleTraitsList.count() - 1) {
                    pos = 0
                    if (controller.isCyclingTraitsAdvances()) {
                        rangeBox.clickRight()
                    }
                    if (controller.getPreferences()
                            .getBoolean(PreferenceKeys.CYCLE_TRAITS_SOUND, false)
                    ) {
                        controller.getSoundHelper().playCycle()
                    }
                }
            }

            setSelection(pos)
            loadLayout()
            controller.refreshLock()
            controller.getCollectInputView().resetInitialIndex()
        }
    }

    fun returnFirst() {
        if (controller.getPreferences().getBoolean(PreferenceKeys.CYCLE_TRAITS_SOUND, false)) {
            controller.getSoundHelper().playCycle()
        }
        setSelection(0)
        loadLayout()
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