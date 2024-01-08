package com.fieldbook.tracker.dialogs

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.activities.TraitEditorActivity
import com.fieldbook.tracker.adapters.TraitFormatAdapter
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.offbeat.traits.formats.Formats
import com.fieldbook.tracker.offbeat.traits.formats.TraitFormatParametersAdapter
import com.fieldbook.tracker.offbeat.traits.formats.ValidationResult
import com.fieldbook.tracker.offbeat.traits.formats.ui.ParameterScrollView
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.SoundHelperImpl
import com.fieldbook.tracker.utilities.VibrateUtil
import dagger.hilt.android.AndroidEntryPoint
import org.phenoapps.utils.SoftKeyboardUtil
import javax.inject.Inject

@AndroidEntryPoint
class CanonConnectDialog(
    private val activity: Activity,
    private val onConfigured: () -> Unit
) :
    DialogFragment() {

    // UI elements of new trait dialog
    private lateinit var ipEditText: EditText
    private lateinit var portEditText: EditText

    override fun onStart() {
        super.onStart()
        /**
         * EditText's inside a dialog fragment need certain window flags to be cleared
         * for the software keyboard to show.
         */
        dialog?.window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        dialog?.window?.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)

        //following stretches dialog a bit for more pixel real estate
        val params = dialog?.window?.attributes
        params?.width = LinearLayout.LayoutParams.MATCH_PARENT
        params?.height = LinearLayout.LayoutParams.WRAP_CONTENT
        dialog?.window?.attributes = params
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val view = layoutInflater.inflate(R.layout.dialog_canon_connection, null)

        ipEditText = view.findViewById(R.id.dialog_canon_ip_et)
        portEditText = view.findViewById(R.id.dialog_canon_port_et)

        context?.let { ctx ->
            val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
            ipEditText.setText(prefs.getString(GeneralKeys.CANON_IP, "").toString())
            portEditText.setText(prefs.getString(GeneralKeys.CANON_PORT, "").toString())
        }

        val builder = AlertDialog.Builder(
            activity,
            R.style.AppAlertDialog
        )

        builder.setTitle("Canon Connect")
            .setCancelable(true)
            .setView(view)

        builder.setPositiveButton(R.string.next) { _, _ ->

            val ip = ipEditText.text.toString()
            val port = portEditText.text.toString()

            context?.let { ctx ->

                PreferenceManager.getDefaultSharedPreferences(ctx).edit()
                    .putString(GeneralKeys.CANON_IP, ip)
                    .putString(GeneralKeys.CANON_PORT, port)
                    .apply()
            }

            onConfigured()
        }

        builder.setNegativeButton(R.string.dialog_cancel) { _, _ -> }
        builder.setNeutralButton(R.string.dialog_back) { _, _ -> }

        return builder.create()
    }
}