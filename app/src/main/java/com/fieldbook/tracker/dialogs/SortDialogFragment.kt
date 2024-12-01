package com.fieldbook.tracker.dialogs

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.adapters.SortAdapter
import com.fieldbook.tracker.interfaces.FieldSortController
import com.fieldbook.tracker.objects.FieldObject
import com.fieldbook.tracker.preferences.GeneralKeys

/**
Extensible sort dialog fragment

Look at FieldSortDialog and FieldAdapter for example: extends and overrides setup ui to update field object
//EXAMPLE: extend this class and implement this on click in on create, extend Sorter interface and call save method
//        //submits the new sort list to the field sort controller (where db queries should be handled)
//        okButton?.setOnClickListener {
//
//            (act as? FieldSortController)?.submitSortList(field, sortList.toTypedArray())
//
//            dismiss()
//        }
 */
open class SortDialogFragment : DialogFragment(), SortAdapter.Sorter {

    protected var act: Activity? = null
    protected var field: FieldObject? = null
    protected var initialItems: Array<String>? = null
    protected var selectableItems: Array<String>? = null
    protected var dialogTitle: String? = null

    protected  var attributeRv: RecyclerView? = null
    protected var cancelButton: Button? = null
    protected  var deleteAllButton: ImageButton? = null
    protected  var addButton: ImageButton? = null
    protected  var sortOrderButton: ImageButton? = null
    protected var okButton: Button? = null
    protected  var sortList = arrayListOf<String>()

    fun newInstance(
        act: Activity,
        field: FieldObject,
        initialItems: Array<String>,
        selectableItems: Array<String>,
        dialogTitle: String,
    ): SortDialogFragment {
        this.act = act
        this.field = field
        this.initialItems = initialItems
        this.selectableItems = selectableItems
        this.dialogTitle = dialogTitle
        return this
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(context, R.style.AppAlertDialog)
            .setTitle(this.dialogTitle)
            .setNegativeButton(R.string.dialog_cancel) { _, _ -> }
            .setPositiveButton(R.string.dialog_ok) { _, _ -> }

        val view = layoutInflater.inflate(R.layout.dialog_field_sort, null)
        builder.setView(view)

        val dialog = builder.create()

        attributeRv = view.findViewById(R.id.dialog_field_sort_rv)
        addButton = view.findViewById(R.id.dialog_field_sort_add_btn)
        deleteAllButton = view.findViewById(R.id.dialog_field_sort_delete_all_btn)
        sortOrderButton = view.findViewById(R.id.dialog_field_sort_toggle_order_btn)

        dialog.show()

        okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        cancelButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

        setupUi()

        return dialog
    }

    private val itemTouchHelper by lazy {

        val itemTouchCallback =
            object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {

                override fun isLongPressDragEnabled(): Boolean {
                    return false
                }

                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {

                    val adapter = recyclerView.adapter as SortAdapter
                    val from = viewHolder.bindingAdapterPosition
                    val to = target.bindingAdapterPosition

                    try {

                        //swap local list
                        sortList[to] = sortList[from].also { sortList[from] = sortList[to] }

                        //swap adapter list

                    } catch (e: java.lang.IndexOutOfBoundsException) {

                        return false

                    }

                    return true
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {

                }

                override fun onSelectedChanged(
                    viewHolder: RecyclerView.ViewHolder?, actionState: Int
                ) {
                    super.onSelectedChanged(viewHolder, actionState)

                    if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                        viewHolder?.itemView?.scaleY = 1.618f //golden ratio
                        viewHolder?.itemView?.alpha = 0.5f
                    }
                }

                override fun clearView(
                    recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder
                ) {
                    super.clearView(recyclerView, viewHolder)

                    viewHolder.itemView.scaleY = 1.0f
                    viewHolder.itemView.alpha = 1.0f
                }

            }

        ItemTouchHelper(itemTouchCallback)
    }


    /**
     * Initialize views and all business logic
     */
    open fun setupUi() {

        //used for drag and drop
        itemTouchHelper.attachToRecyclerView(attributeRv)

        attributeRv?.itemAnimator = null

        attributeRv?.adapter = SortAdapter(this).also { adapter ->

            if (selectableItems?.isNotEmpty() == true) {

                sortList.addAll(initialItems?.toList() ?: emptyList())

                adapter.submitList(sortList)

                adapter.notifyDataSetChanged()

            } else {

                Toast.makeText(
                    context, R.string.dialog_field_sort_no_attributes_found, Toast.LENGTH_SHORT
                ).show()

                dismiss()
            }
        }

        //starts a dialog to add another attribute that hasn't been added already
        addButton?.setOnClickListener {

            val unused = selectableItems?.filter { it !in sortList }

            if (unused != null) {
                if (unused.isNotEmpty()) {

                    askDialogForAttribute(unused)

                } else {

                    Toast.makeText(
                        context,
                        R.string.dialog_field_sort_no_more_attributes_found,
                        Toast.LENGTH_SHORT
                    ).show()

                }
            }
        }

        //simply dismisses dialog
        cancelButton?.setOnClickListener {

            dismiss()

        }

        deleteAllButton?.setOnClickListener {

            sortList.clear()

            submitList()
        }

        sortOrderButton?.setOnClickListener {

            val studyId = getStudyId()

            studyId?.let { it1 -> toggleSortOrder(it1) }

            studyId?.let { it1 -> getSortIcon(it1) }
                ?.let { it2 -> sortOrderButton?.setImageResource(it2) }
        }

        submitList()

        getStudyId()?.let { getSortIcon(it) }?.let { sortOrderButton?.setImageResource(it) }

    }

    private fun getStudyId() = context?.let {
        PreferenceManager.getDefaultSharedPreferences(it)
            .getInt(GeneralKeys.SELECTED_FIELD_ID, 0).toString()
    }

    private fun getSortIcon(studyId: String) =
        if (isSortOrderAsc(studyId) == true) R.drawable.sort_ascending else R.drawable.sort_descending

    private fun toggleSortOrder(studyId: String) = when (isSortOrderAsc(studyId)) {
        true -> persistSortOrder(studyId, false)
        else -> persistSortOrder(studyId, true)
    }

    private fun isSortOrderAsc(studyId: String) = getSortOrder(studyId)

    private fun persistSortOrder(studyId: String, isAsc: Boolean) =
        context?.let {
            PreferenceManager.getDefaultSharedPreferences(it).edit()
                .putBoolean("${GeneralKeys.SORT_ORDER}.$studyId", isAsc).apply()
        }

    private fun getSortOrder(studyId: String) =
        context?.let {
            PreferenceManager.getDefaultSharedPreferences(it)
                .getBoolean("${GeneralKeys.SORT_ORDER}.$studyId", true)
        }

    private fun submitList() {

        (attributeRv?.adapter as? SortAdapter)?.also {

            it.submitList(sortList)

            it.notifyDataSetChanged()
        }
    }

    private fun addSortItem(item: String) {

        sortList.add(item)

        submitList()

        attributeRv?.scrollToPosition(sortList.size - 1)

    }

    private fun askDialogForAttribute(unused: List<String>) {

        val dialog = AlertDialog.Builder(context, R.style.AppAlertDialog)

        val adapter = context?.let {
            ArrayAdapter(
                it,
                android.R.layout.simple_list_item_1,
                unused
            )
        }

        dialog.setAdapter(adapter) { d, which ->

            //on click listener

            addSortItem(unused[which])

            d.dismiss()
        }

        dialog.setNegativeButton(android.R.string.cancel) { d, _ ->
            d.dismiss()
        }

        dialog.show()
    }

    override fun onDeleteItem(attribute: String) {

        val index = sortList.indexOf(attribute)

        sortList.remove(attribute)

        submitList()

        (attributeRv?.adapter as? SortAdapter)?.also {

            if (index in 0 until it.itemCount) {

                attributeRv?.scrollToPosition(index)

            }
        }
    }

    override fun onDrag(item: SortAdapter.ViewHolder) {

        itemTouchHelper.startDrag(item)
    }
}