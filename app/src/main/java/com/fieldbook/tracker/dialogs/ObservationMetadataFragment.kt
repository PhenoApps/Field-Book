package com.fieldbook.tracker.dialogs

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.adapters.ObservationMetadataAdapter
import com.fieldbook.tracker.database.models.ObservationModel
import com.fieldbook.tracker.preferences.GeneralKeys

/**
 * Creates a observation metadata dialog.
 */
class ObservationMetadataFragment : DialogFragment() {

    private var currentObservationObject: ObservationModel? = null

    private var recyclerView: RecyclerView? = null

    fun newInstance(currentObservationObject: ObservationModel): ObservationMetadataFragment {
        this.currentObservationObject = currentObservationObject
        return this
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext(), R.style.AppAlertDialog)
        builder.setTitle(R.string.observation_metadata_title)

        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.dialog_observation_metadata, null) as ViewGroup

        recyclerView = view.findViewById(R.id.dialog_observation_metadata_rv)

        recyclerView?.adapter = ObservationMetadataAdapter()

        loadData()

        builder.setView(view)

        return builder.create()
    }

    private fun loadData() {
        (activity as? CollectActivity)?.let { collector ->

            val fieldName = collector.getPreferences().getString(GeneralKeys.FIELD_FILE, "")

            // get all non-null attributes for current observation
            val observationModelFields: MutableMap<String, Any>? =
                fieldName?.let {
                    currentObservationObject?.getNonNullAttributes(
                        requireContext(), collector.currentTrait, it
                    )
                }

            val pairList =
                arrayListOf<ObservationMetadataAdapter.ObservationMetadataListModel>()
            observationModelFields?.forEach { (key, value) ->
                pairList.add(
                    ObservationMetadataAdapter.ObservationMetadataListModel(
                        key, value.toString()
                    )
                )
            }

            (recyclerView?.adapter as? ObservationMetadataAdapter)?.submitList(pairList)
        }
    }
}