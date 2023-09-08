package com.fieldbook.tracker.views

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.Group
import androidx.viewpager.widget.ViewPager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.adapters.RepeatedValuesPagerAdapter
import com.fieldbook.tracker.database.models.ObservationModel
import com.fieldbook.tracker.utilities.Utils
import kotlin.math.abs

/**
 * Feature view for taking multiple measurements for each unit/variable.
 * Is enabled in the beta feature settings.
 *
 * This is a linked list of values that the user can scroll through.
 *
 * The collect activity has a toolbar indicator that shows the total number of repeated values.
 */
class RepeatedValuesView(context: Context, attributeSet: AttributeSet) :
    ConstraintLayout(context, attributeSet) {

    data class ObservationModelViewHolder(var model: ObservationModel, val color: Int)

    class SimplePageTransformer : ViewPager.PageTransformer {

        override fun transformPage(view: View, position: Float) {
            view.apply {
                alpha = when {
                    position < -1 -> { // [-Infinity,-1)
                        // This page is way off-screen to the left.
                        0.1f
                    }
                    position <= 1 -> { // [-1,1]
                        1f
                    }
                    else -> { // (1,+Infinity]
                        // This page is way off-screen to the right.
                        0.1f
                    }
                }
            }
        }
    }

    var displayColor: Int = Color.RED

    private var mValues = arrayListOf<ObservationModelViewHolder>()
    private val leftButton: Button
    private val rightButton: Button
    private val addButton: ImageButton
    private val pager: ViewPager
    private val nonEmptyGroup: Group

    //initialize all the global view variables
    init {

        inflate(context, R.layout.view_repeated_values, this)

        leftButton = findViewById(R.id.repeated_values_view_left_btn)
        rightButton = findViewById(R.id.repeated_values_view_right_btn)
        addButton = findViewById(R.id.repeated_values_view_add_btn)
        pager = findViewById(R.id.repeated_values_view_pager)
        nonEmptyGroup = findViewById(R.id.view_repeated_values_group)

        pager.pageMargin = 8f.dipToPixels(context).toInt()

        pager.adapter = RepeatedValuesPagerAdapter(context)

        pager.setPageTransformer(true, SimplePageTransformer())

        //update buttons when user scrolls
        pager.setOnScrollChangeListener { _, _, _, _, _ ->
            updateButtonVisibility()
        }

        rightButton.setOnClickListener {

            val act = context as CollectActivity

            if (!act.isTraitBlocked) {

                if (pager.currentItem < (pager.adapter?.count ?: 1) - 1) {

                    pager.currentItem++

                }

                updateButtonVisibility()

                Handler(Looper.getMainLooper()).postDelayed({

                    act.traitLayoutRefresh()

                }, 100)
            }
        }

        leftButton.setOnClickListener {

            val act = context as CollectActivity

            if (!act.isTraitBlocked) {

                if (pager.currentItem > 0) {

                    pager.currentItem--

                }

                updateButtonVisibility()

                Handler(Looper.getMainLooper()).postDelayed({

                    act.traitLayoutRefresh()

                }, 100)
            }
        }

        addButton.setOnClickListener {

            val act = context as CollectActivity

            if (!act.isTraitBlocked) {

                val current = getSelectedModel()

                //only add new measurements if the current one has been observed
                if (current != null && current.value.isNotEmpty()) {

                    val model = insertNewRep((mValues.maxOf { it.model.rep.toInt() } + 1).toString())

                    mValues.add(ObservationModelViewHolder(model, Color.BLACK))

                    submitList()

                    pager.currentItem = mValues.size - 1

                    updateButtonVisibility()

                    act.traitLayoutRefreshNew()

                } else {

                    Utils.makeToast(context, context.getString(R.string.view_repeated_values_add_button_fail))
                }
                
            } else { //edge case for first value to be added and the trait supports blocking (date format)

                Utils.makeToast(context, context.getString(R.string.view_repeated_values_add_button_fail))

            }
        }
    }

    //inserts a dummy row which later is deleted if the value is blank
    private fun addFirstItem() {

        prepareModeNonEmpty()

        mValues.add(ObservationModelViewHolder(insertNewRep("1"), Color.BLACK))

        submitList()

        updateButtonVisibility()

        (context as CollectActivity).traitLayoutRefresh()

    }

    //updates the local view model's value
    private fun updateItem(value: String) {

        val old = pager.currentItem

        getSelectedModel()?.let { model ->

            getSelectedModel()?.value = value

            submitList()

            pager.currentItem = old

            updateButtonVisibility()
        }
    }

    /**
     * Called during trait navigation in collect activity.
     * Used to detect if the current item is blank, it should be deleted in the database.
     */
    fun refresh(onNew: Boolean) {

        if (!onNew) {
            val value = getSelectedModel()?.value ?: ""

            if (value.isEmpty()) {

                deleteCurrentRep()

            }
        }
    }

    //called when no observations exist for this unit/variable combo
    fun prepareModeEmpty() {

        addFirstItem()

    }

    //called when values do exist
    fun prepareModeNonEmpty() {

        nonEmptyGroup.visibility = View.VISIBLE
        addButton.visibility = View.VISIBLE
        pager.visibility = View.VISIBLE
        leftButton.visibility = View.VISIBLE
        rightButton.visibility = View.VISIBLE

        updateButtonVisibility()
    }

    //returns the currently selected observation's rep value
    fun getRep(): String {
        return getSelectedModel()?.rep ?: "1"
    }

    //mimics edit text variable to set/get the currently selected text
    var text: String
        get() {
            return if (mValues.isNotEmpty()) {
                getSelectedModel()?.value ?: ""
            } else ""
        }
        set(value) {
            if (mValues.isNotEmpty()) {
                updateItem(value)
            } else {
                addFirstItem()
                updateItem(value)
            }
        }

    //clears the linked list
    fun clear() {
        mValues.clear()
    }

    //basic setup for linked list view's adapter
    fun initialize(values: List<ObservationModel>, initialRep: Int) {

        mValues.clear()

        mValues.addAll(values.map { ObservationModelViewHolder(it, displayColor) })

        submitList()

        pager.currentItem = if (initialRep == -1) values.size - 1 else initialRep - 1

        updateButtonVisibility()
    }

    //called when user manually deletes a value
    fun userDeleteCurrentRep() {

        val repToDelete = getRep()

        //delete the selected rep from the repeated values list
        mValues = arrayListOf(*mValues.filter { it.model.rep != repToDelete }.toTypedArray())

        //if all values are deleted, keep at least one for data entry
        if (mValues.isEmpty()) {

            prepareModeEmpty()

        } else {

            mValues.minByOrNull { abs(it.model.rep.toInt() - repToDelete.toInt()) }?.let { entry ->
                pager.currentItem = mValues.indexOf(entry)
            }

            Handler(Looper.getMainLooper()).postDelayed({

                submitList()

                //select closest rep
                updateButtonVisibility()

                (context as CollectActivity).traitLayoutRefresh()

            }, 100)
        }
    }

    //called on refresh if the value is undesirable
    private fun deleteCurrentRep() {

        val repToDelete = getRep()

        mValues.firstOrNull { it.model.rep == repToDelete }?.let { deleteItem ->

            (context as? CollectActivity)?.deleteRep(deleteItem.model.observation_variable_name, deleteItem.model.rep)

        }
    }

    fun getEditText() = (pager.adapter as? RepeatedValuesPagerAdapter)?.get(pager.currentItem)

    fun setTextColor(color: Int) {
        getEditText()?.setTextColor(color)
    }

    private fun getSelectedModel(): ObservationModel? {
        return if (mValues.isNotEmpty()) mValues[pager.currentItem].model
        else null
    }

    private fun insertNewRep(rep: String): ObservationModel {

        with (context as CollectActivity) {

            insertRep(traitName, traitFormat, "", rep)

            return ObservationModel(mapOf(
                "observation_variable_db_id" to currentTrait.id,
                "observation_variable_name" to traitName,
                "observation_variable_field_book_format" to traitFormat,
                "value" to "",
                "rep" to rep
            ))
        }
    }

    private fun submitList() {

        (pager.adapter as RepeatedValuesPagerAdapter).submitItems(mValues)

        updateButtonVisibility()
    }

    private fun Float.dipToPixels(context: Context): Float {
        val metrics = context.resources.displayMetrics
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this, metrics)
    }

    private fun updateButtonVisibility() {

        val flag = pager.currentItem != (pager.adapter?.count ?: 1) - 1

        //swap the right arrow / new button visibility
        if (flag) {
            addButton.visibility = View.INVISIBLE
            rightButton.visibility = View.VISIBLE
        } else {
            addButton.visibility = View.VISIBLE
            rightButton.visibility = View.INVISIBLE
        }

        leftButton.visibility = if (pager.currentItem == 0) View.INVISIBLE else View.VISIBLE

    }
}