package com.fieldbook.tracker.dialogs

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Rect
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.adapters.SortAdapter
import com.fieldbook.tracker.preferences.GeneralKeys

/**
Extensible sort dialog

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
open class SortDialog(
    act: Activity,
    private val initialItems: Array<String>,
    private val selectableItems: Array<String>
) : Dialog(act), SortAdapter.Sorter {

    protected var attributeRv: RecyclerView? = null
    protected var cancelButton: Button? = null
    protected var deleteAllButton: ImageButton? = null
    protected var addButton: ImageButton? = null
    protected var sortOrderButton: ImageButton? = null
    protected var okButton: Button? = null
    protected var sortList = arrayListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.dialog_field_sort)

        setCanceledOnTouchOutside(true)

        //when shown, update the window size to %80 width and %50 height
        setOnShowListener {

            window?.let { w ->

                val rect = Rect()
                w.decorView.getWindowVisibleDisplayFrame(rect)

                w.setLayout(
                    (rect.width() * .8).toInt(), (rect.height() * .5).toInt()
                )
            }

        }

        setupUi()

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

        attributeRv = findViewById(R.id.dialog_field_sort_rv)
        addButton = findViewById(R.id.dialog_field_sort_add_btn)
        okButton = findViewById(R.id.dialog_field_sort_ok_btn)
        cancelButton = findViewById(R.id.dialog_field_sort_cancel_btn)
        deleteAllButton = findViewById(R.id.dialog_field_sort_delete_all_btn)
        sortOrderButton = findViewById(R.id.dialog_field_sort_toggle_order_btn)

        //used for drag and drog
        itemTouchHelper.attachToRecyclerView(attributeRv)

        attributeRv?.itemAnimator = null

        attributeRv?.adapter = SortAdapter(this).also { adapter ->

            if (selectableItems.isNotEmpty()) {

                sortList.addAll(initialItems)

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

            val unused = selectableItems.filter { it !in sortList }

            if (unused.isNotEmpty()) {

                askDialogForAttribute(unused)

            } else {

                Toast.makeText(
                    context, R.string.dialog_field_sort_no_more_attributes_found, Toast.LENGTH_SHORT
                ).show()

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

            toggleSortOrder(studyId)

            sortOrderButton?.setImageResource(getSortIcon(studyId))
        }

        submitList()

        sortOrderButton?.setImageResource(getSortIcon(getStudyId()))

    }

    private fun getStudyId() = PreferenceManager.getDefaultSharedPreferences(context)
        .getInt(GeneralKeys.SELECTED_FIELD_ID, 0).toString()

    private fun getSortIcon(studyId: String) = if (isSortOrderAsc(studyId)) R.drawable.sort_ascending else R.drawable.sort_descending

    private fun toggleSortOrder(studyId: String) = when (isSortOrderAsc(studyId)) {
        true -> persistSortOrder(studyId, false)
        else -> persistSortOrder(studyId, true)
    }

    private fun isSortOrderAsc(studyId: String) = getSortOrder(studyId)

    private fun persistSortOrder(studyId: String, isAsc: Boolean) =
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean("${GeneralKeys.SORT_ORDER}.$studyId", isAsc).apply()

    private fun getSortOrder(studyId: String) =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean("${GeneralKeys.SORT_ORDER}.$studyId", true)

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

        val dialog = AlertDialog.Builder(context)

        dialog.setTitle(R.string.dialog_field_sort_title)

        val adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, unused)

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