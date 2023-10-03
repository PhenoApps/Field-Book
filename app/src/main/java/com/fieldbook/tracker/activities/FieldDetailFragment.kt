package com.fieldbook.tracker.activities

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.DataHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

fun newFieldDetailFragment(
    fieldName: String,
    importDate: String,
    exportDate: String,
    editDate: String,
    count: String
): FieldDetailFragment {
    val fragment = FieldDetailFragment()
    val args = Bundle()
    args.putString("FIELD_NAME", fieldName)
    args.putString("IMPORT_DATE", importDate)
    args.putString("EXPORT_DATE", exportDate)
    args.putString("EDIT_DATE", editDate)
    args.putString("COUNT", count)
    fragment.arguments = args
    return fragment
}

@AndroidEntryPoint
class FieldDetailFragment : Fragment() {

    @Inject
    lateinit var database: DataHelper
    private var toolbar: Toolbar? = null

    private lateinit var importDateTextView: TextView
    private lateinit var editDateTextView: TextView
    private lateinit var exportDateTextView: TextView
    private lateinit var countTextView: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {

        val view = inflater.inflate(R.layout.fragment_field_detail, container, false)

        toolbar = view.findViewById(R.id.toolbar)

        importDateTextView = view.findViewById(R.id.importDateTextView)
        editDateTextView = view.findViewById(R.id.editDateTextView)
        exportDateTextView = view.findViewById(R.id.exportDateTextView)
        countTextView = view.findViewById(R.id.countTextView)

        val args = requireArguments()
        toolbar?.title = args.getString("FIELD_NAME")
        importDateTextView.text = "Import Date: ${args.getString("IMPORT_DATE")}"
        editDateTextView.text = "Edit Date: ${args.getString("EDIT_DATE")}"
        exportDateTextView.text = "Export Date: ${args.getString("EXPORT_DATE")}"
        countTextView.text = "Count: ${args.getString("COUNT")}"

        toolbar?.setNavigationIcon(R.drawable.arrow_left)
        toolbar?.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        return view
    }
}
