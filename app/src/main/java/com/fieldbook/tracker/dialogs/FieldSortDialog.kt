package com.fieldbook.tracker.dialogs

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Rect
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Toast
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.adapters.FieldAdapter.FieldSortController
import com.fieldbook.tracker.adapters.FieldSortAdapter
import com.fieldbook.tracker.database.dao.ObservationUnitPropertyDao
import com.fieldbook.tracker.database.dao.StudyDao
import com.fieldbook.tracker.objects.FieldObject


/**
 Opens from the FieldEditorActivity FieldAdapter "sort" option.
 Allows user to make and order a list of field attributes to sort by.
 */
class FieldSortDialog(private val act: Activity, private val field: FieldObject) : Dialog(act, R.style.Dialog),
    FieldSortAdapter.FieldSorter {

    private var attributeRv: RecyclerView? = null
    private var addButton: Button? = null
    private var cancelButton: Button? = null
    private var okButton: Button? = null
    private var revertButton: Button? = null

    private var attributes = listOf<String>()
    private var sortList = arrayListOf<String>()

    private var preSortList = arrayListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.dialog_field_sort)

        setCanceledOnTouchOutside(true)

        //when shown, update the window size to %80 width and %50 height
        setOnShowListener {

            window?.let { w ->

                val rect = Rect()
                w.decorView.getWindowVisibleDisplayFrame(rect)

                w.setLayout((rect.width() * .8).toInt(),
                    (rect.height() * .5).toInt())
            }

        }

        setupUi()

    }

    private val itemTouchHelper by lazy {

        val itemTouchCallback = object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {

            override fun isLongPressDragEnabled(): Boolean {
                return false
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {

                val recyclerviewAdapter = recyclerView.adapter as FieldSortAdapter
                val fromPosition = viewHolder.bindingAdapterPosition
                val toPosition = target.bindingAdapterPosition

                try {

                    //swap local list
                    sortList[toPosition] = sortList[fromPosition].also { sortList[fromPosition] = sortList[toPosition]  }

                    //swap adapter list
                    recyclerviewAdapter.moveItem(fromPosition, toPosition)

                } catch (e: java.lang.IndexOutOfBoundsException) {

                    return false

                }

                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {

            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)

                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.scaleY = 1.618f //golden ratio
                    viewHolder?.itemView?.alpha = 0.5f
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
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
    private fun setupUi() {

        setTitle(R.string.dialog_field_sort_title)

        attributeRv = findViewById(R.id.dialog_field_sort_rv)
        addButton = findViewById(R.id.dialog_field_sort_add_btn)
        okButton = findViewById(R.id.dialog_field_sort_ok_btn)
        cancelButton = findViewById(R.id.dialog_field_sort_cancel_btn)
        revertButton = findViewById(R.id.dialog_field_sort_revert_btn)

        attributes = ObservationUnitPropertyDao.getRangeColumnNames().mapNotNull { it }

        //used for drag and drog
        itemTouchHelper.attachToRecyclerView(attributeRv)

        attributeRv?.adapter = FieldSortAdapter(this).also { adapter ->

            //initialize ui with one element to sort by, users can add more if they wish
            if (attributes.isNotEmpty()) {

                val f = StudyDao.getFieldObject(field.exp_id)

                val order = f?.exp_sort ?: String()

                if (order.isNotEmpty()) {

                    val sortOrder = order.split(",")

                    sortList.addAll(sortOrder)

                    preSortList.addAll(sortOrder)
                }

                adapter.submitList(sortList)

                adapter.notifyDataSetChanged()

            } else {

                Toast.makeText(context, R.string.dialog_field_sort_no_attributes_found, Toast.LENGTH_SHORT).show()

                dismiss()
            }
        }

        //starts a dialog to add another attribute that hasn't been added already
        addButton?.setOnClickListener {

            val unused = attributes.filter { it !in sortList }

            if (unused.isNotEmpty()) {

                askDialogForAttribute(unused)

            } else {

                Toast.makeText(context, R.string.dialog_field_sort_no_more_attributes_found, Toast.LENGTH_SHORT).show()

            }
        }

        //submits the new sort list to the field sort controller (where db queries should be handled)
        okButton?.setOnClickListener {

            (act as? FieldSortController)?.submitSortList(field, sortList.toTypedArray())

            dismiss()
        }

        //simply dismisses dialog
        cancelButton?.setOnClickListener {

            dismiss()

        }

        //revert list to un-edited version
        revertButton?.setOnClickListener {

            (attributeRv?.adapter as? FieldSortAdapter)?.let { adapter ->

                sortList.clear()

                sortList.addAll(preSortList)

                adapter.submitList(sortList)

                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun addSortItem(item: String) {

        sortList.add(item)

        (attributeRv?.adapter as? FieldSortAdapter)?.also {

            it.submitList(sortList)

            it.notifyDataSetChanged()

            attributeRv?.scrollToPosition(sortList.size - 1)
        }
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

        (attributeRv?.adapter as? FieldSortAdapter)?.notifyItemRemoved(index)
    }

    override fun onDrag(item: FieldSortAdapter.ViewHolder) {

        itemTouchHelper.startDrag(item)
    }
}