package com.fieldbook.tracker.activities

import android.Manifest
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.dialogs.BrapiSyncObsDialog
import com.fieldbook.tracker.interfaces.FieldAdapterController
import com.fieldbook.tracker.interfaces.FieldSortController
import com.fieldbook.tracker.objects.FieldObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.ExportUtil
import dagger.hilt.android.AndroidEntryPoint
import pub.devrel.easypermissions.EasyPermissions
import javax.inject.Inject


fun newFieldDetailFragment(
    fieldName: String,
    importDate: String,
    exportDate: String,
    editDate: String,
    count: String,
    observationLevel: String
): FieldDetailFragment {
    val fragment = FieldDetailFragment()
    val args = Bundle()
    args.putString("FIELD_NAME", fieldName)
    args.putString("IMPORT_DATE", importDate)
    args.putString("EXPORT_DATE", exportDate)
    args.putString("EDIT_DATE", editDate)
    args.putString("COUNT", count)
    args.putString("OBSERVATION_LEVEL", observationLevel)
    fragment.arguments = args
    return fragment
}

@AndroidEntryPoint
class FieldDetailFragment : Fragment() {

    @Inject
    lateinit var database: DataHelper
    private var toolbar: Toolbar? = null
    private val PERMISSIONS_REQUEST_TRAIT_DATA = 9950

    private lateinit var exportUtil: ExportUtil
    private lateinit var importDateTextView: TextView
    private lateinit var editDateTextView: TextView
    private lateinit var exportDateTextView: TextView
    private lateinit var countTextView: TextView
    private lateinit var observationLevelTextView: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {

        val view = inflater.inflate(R.layout.fragment_field_detail, container, false)
        val args = requireArguments()
        toolbar = view.findViewById(R.id.toolbar)
        setupToolbar()

        importDateTextView = view.findViewById(R.id.importDateTextView)
        editDateTextView = view.findViewById(R.id.editDateTextView)
        exportDateTextView = view.findViewById(R.id.exportDateTextView)
        countTextView = view.findViewById(R.id.countTextView)
        observationLevelTextView = view.findViewById(R.id.observationLevelTextView)
        exportUtil = ExportUtil(requireActivity(), database)

        val collectButton: Button = view.findViewById(R.id.collectButton)
        val exportButton: Button = view.findViewById(R.id.exportButton)

        importDateTextView.text = "Import Date: ${args.getString("IMPORT_DATE")}"
        editDateTextView.text = "Edit Date: ${args.getString("EDIT_DATE")}"
        exportDateTextView.text = "Export Date: ${args.getString("EXPORT_DATE")}"
        countTextView.text = "Count: ${args.getString("COUNT")}"
        observationLevelTextView.text = "Entry Type: ${args.getString("OBSERVATION_LEVEL")}"

        collectButton.setOnClickListener {
            if (checkTraitsExist() >= 0) collectDataFilePermission()
        }

        exportButton.setOnClickListener {
            if (checkTraitsExist() >= 0) exportUtil.exportDataBasedOnPreference()
        }

        return view
    }

    private fun setupToolbar() {

        with(activity as? FieldEditorActivity) {

            this?.let { editorActivity ->

                val field = editorActivity.fieldObject

                toolbar?.inflateMenu(R.menu.menu_field_details)

                toolbar?.setTitle(field.getExp_name())

                toolbar?.setNavigationIcon(R.drawable.arrow_left)

                toolbar?.setNavigationOnClickListener {

                    parentFragmentManager.popBackStack()
                }

                toolbar?.setOnMenuItemClickListener { item ->

                    when (item.itemId) {
                        android.R.id.home -> {
                            parentFragmentManager.popBackStack()
                        }
                        R.id.sort -> {
                            (activity as? FieldSortController)?.showSortDialog(field)
                        }
                        R.id.syncObs -> {
                            val alert = BrapiSyncObsDialog(requireContext())
                            alert.setFieldObject(field)
                            alert.show()
                        }
                        R.id.delete -> {
                            createDeleteItemAlertDialog(field)?.show()
                        }
                    }

                    true
                }
            }
        }
    }

    private fun makeConfirmDeleteListener(field: FieldObject): DialogInterface.OnClickListener? {
        return DialogInterface.OnClickListener { dialog, which ->

            // Do it when clicking Yes or No
            dialog.dismiss()

            val ep = activity?.getSharedPreferences(GeneralKeys.SHARED_PREF_FILE_NAME, AppCompatActivity.MODE_PRIVATE)
            (activity as? FieldAdapterController)?.getDatabase()?.deleteField(field.getExp_id())
            if (field.getExp_id() == ep!!.getInt(GeneralKeys.SELECTED_FIELD_ID, -1)) {
                val ed = ep.edit()
                ed.putString(GeneralKeys.FIELD_FILE, null)
                ed.putString(GeneralKeys.FIELD_OBS_LEVEL, null)
                ed.putInt(GeneralKeys.SELECTED_FIELD_ID, -1)
                ed.putString(GeneralKeys.UNIQUE_NAME, null)
                ed.putString(GeneralKeys.PRIMARY_NAME, null)
                ed.putString(GeneralKeys.SECONDARY_NAME, null)
                ed.putBoolean(GeneralKeys.IMPORT_FIELD_FINISHED, false)
                ed.putString(GeneralKeys.LAST_PLOT, null)
                ed.apply()
            }
            (activity as? FieldAdapterController)?.queryAndLoadFields()
            CollectActivity.reloadData = true
            parentFragmentManager.popBackStack()
        }
    }

    private fun createDeleteItemAlertDialog(field: FieldObject): AlertDialog? {
        val builder =
            AlertDialog.Builder(
                requireContext(), R.style.AppAlertDialog
            )
        builder.setTitle(requireContext().getString(R.string.fields_delete_study))
        builder.setMessage(requireContext().getString(R.string.fields_delete_study_confirmation))
        builder.setPositiveButton(
            requireContext().getString(R.string.dialog_yes),
            makeConfirmDeleteListener(field)
        )
        builder.setNegativeButton(
            requireContext().getString(R.string.dialog_no)
        ) { dialog, which -> dialog.dismiss() }
        return builder.create()
    }

    fun checkTraitsExist(): Int {
        val traits = database.getVisibleTrait()

        return when {
            traits.isEmpty() -> {
                Toast.makeText(context, R.string.warning_traits_missing, Toast.LENGTH_SHORT).show()
                -1
            }
            else -> 1
        }
    }

    fun collectDataFilePermission() {
        var perms = arrayOf<String?>(
            Manifest.permission.VIBRATE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            perms = arrayOf(
                Manifest.permission.VIBRATE,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.CAMERA
            )
        }
        if (EasyPermissions.hasPermissions(requireActivity(), *perms)) {
            val intent = Intent()
            intent.setClassName(
                requireActivity(),
                "com.fieldbook.tracker.activities.CollectActivity"
            )
            startActivity(intent)
        } else {
            // Do not have permissions, request them now
            EasyPermissions.requestPermissions(
                this, getString(R.string.permission_rationale_trait_features),
                PERMISSIONS_REQUEST_TRAIT_DATA, *perms
            )
        }
    }
}
