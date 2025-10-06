package com.fieldbook.tracker.dialogs

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.adapters.TraitFormatAdapter
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.traits.formats.Formats
import com.fieldbook.tracker.traits.formats.TraitFormatParametersAdapter
import com.fieldbook.tracker.traits.formats.ValidationResult
import com.fieldbook.tracker.traits.formats.parameters.ResourceFileParameter
import com.fieldbook.tracker.traits.formats.ui.ParameterScrollView
import com.fieldbook.tracker.utilities.SoundHelperImpl
import com.fieldbook.tracker.utilities.VibrateUtil
import dagger.hilt.android.AndroidEntryPoint
import org.phenoapps.utils.SoftKeyboardUtil
import javax.inject.Inject
import androidx.core.content.edit
import com.fieldbook.tracker.traits.formats.parameters.DisplayValueParameter

@AndroidEntryPoint
class NewTraitDialog(
    private val activity: Activity,
) :
    DialogFragment(),
    TraitFormatAdapter.FormatSelectionListener,
    TraitFormatParametersAdapter.TraitFormatAdapterController {

    interface TraitDialogDismissListener {
        fun onNewTraitDialogDismiss() = Unit
    }

    interface TraitDialogFormatListener {
        fun onFormatSelected(format: Formats) = Unit
    }

    interface TraitObjectUpdateListener {
        fun onTraitObjectUpdated(traitObject: TraitObject) = Unit
    }

    @Inject
    lateinit var soundHelperImpl: SoundHelperImpl

    @Inject
    lateinit var vibrator: VibrateUtil

    @Inject
    lateinit var prefs: SharedPreferences

    @Inject
    lateinit var database: DataHelper

    //flag to just return selectable format
    var isSelectingFormat: Boolean = false

    //flag for editing existing brapi variable being imported
    var isBrapiTraitImport: Boolean = false

    // UI elements of new trait dialog
    private lateinit var traitFormatsRv: RecyclerView
    private lateinit var parametersSv: ParameterScrollView
    private lateinit var variableEditableErrorTv: TextView

    private var negativeBtn: Button? = null
    private var positiveBtn: Button? = null
    private var neutralBtn: Button? = null

    //holds the trait objects sent from trait editor activity
    private var initialTraitObject: TraitObject? = null

    //when editing this tracks the original object, to see if values changed when discarding
    private var originalInitialTraitObject: TraitObject? = null

    //private var createVisible: Boolean

    fun setTraitObject(traitObject: TraitObject?) {

        initialTraitObject = traitObject?.clone()
        originalInitialTraitObject = traitObject?.clone()

    }

    override fun onStart() {
        super.onStart()

        positiveBtn = (dialog as AlertDialog?)?.getButton(AlertDialog.BUTTON_POSITIVE)
        negativeBtn = (dialog as AlertDialog?)?.getButton(AlertDialog.BUTTON_NEGATIVE)
        neutralBtn = (dialog as AlertDialog?)?.getButton(AlertDialog.BUTTON_NEUTRAL)

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

        context?.let {
            show()
        }
    }

    private fun showFormatLayouts(formats: List<Formats>, showBack: Boolean = false) {

        positiveBtn?.visibility = View.INVISIBLE
        negativeBtn?.visibility = View.INVISIBLE

        dialog?.setTitle(context?.getString(R.string.trait_creator_title_layout))

        traitFormatsRv.visibility = View.VISIBLE
        parametersSv.visibility = View.GONE

        if (showBack) {

            neutralBtn?.setText(R.string.dialog_back)
            neutralBtn?.setOnClickListener {
                isShowingCameraOptions = false
                isShowingSpectralOptions = false
                traitFormatsRv.adapter = null
                showFormatLayouts(Formats.getMainFormats())
            }

        } else {

            neutralBtn?.setText(R.string.dialog_cancel)
            neutralBtn?.setOnClickListener {
                dismiss()
            }
        }

        positiveBtn?.setText(R.string.next)
        positiveBtn?.setOnClickListener {
            showFormatParameters()
            if (context?.let { getSelectedFormat() } == null) {
                Toast.makeText(
                    context,
                    R.string.dialog_new_trait_error_must_select_a_layout,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        if (traitFormatsRv.adapter == null) {

            setupTraitFormatsRv(formats)

        }
    }

    private fun showFormatParameters(format: Formats) {

        positiveBtn?.visibility = View.VISIBLE
        negativeBtn?.visibility = View.VISIBLE

        traitFormatsRv.visibility = View.GONE
        parametersSv.visibility = View.VISIBLE

        //if editing a variable and observations exist, don't allow the format to change
        var observationsExist = false
        if (initialTraitObject != null) {
            initialTraitObject?.id?.let { traitDbId ->
                observationsExist = database.getAllObservationsOfVariable(traitDbId).isNotEmpty()
                variableEditableErrorTv.visibility =
                    if (observationsExist) View.VISIBLE else View.GONE
            }
        }

        if (initialTraitObject == null || !observationsExist) {

            neutralBtn?.setText(R.string.dialog_back)
            neutralBtn?.setOnClickListener {
                //close keyboard programmatically
                SoftKeyboardUtil.closeKeyboard(context, traitFormatsRv, 1L)

                (traitFormatsRv.adapter as? TraitFormatAdapter)?.selectedFormat = null

                if (observationsExist) {
                    onCancel()
                }

                if (format in Formats.getCameraFormats()) {

                    isShowingCameraOptions = true

                    showFormatLayouts(Formats.getCameraFormats(), showBack = true)

                } else {

                    showFormatLayouts(Formats.getMainFormats())

                }
            }
        }

        negativeBtn?.setText(R.string.dialog_cancel)
        negativeBtn?.setOnClickListener {
            onCancel()
        }

        positiveBtn?.setText(R.string.dialog_save)
        positiveBtn?.setOnClickListener {
            onSave(format)
        }

        setupParametersLinearLayout(format)

        context?.let { ctx ->
            dialog?.setTitle(
                ctx.getString(
                    R.string.trait_creator_parameters_title,
                    format.getName(ctx)
                )
            )
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val view = layoutInflater.inflate(R.layout.dialog_trait_creator, null)

        val builder = AlertDialog.Builder(
            activity,
            R.style.AppAlertDialog
        )

        builder.setTitle(R.string.trait_creator_title_layout)
            .setCancelable(true)
            .setView(view)

        builder.setPositiveButton(R.string.next) { _, _ -> }
        builder.setNegativeButton(R.string.dialog_cancel) { _, _ -> }
        builder.setNeutralButton(R.string.dialog_back) { _, _ -> }

        traitFormatsRv = view.findViewById(R.id.dialog_new_trait_formats_rv)
        parametersSv = view.findViewById(R.id.dialog_new_trait_parameters_psv)
        variableEditableErrorTv =
            view.findViewById(R.id.dialog_new_trait_variable_editable_error_tv)

        return builder.create()
    }

    private fun show() {
        if (initialTraitObject == null) showFormatLayouts(Formats.getMainFormats()) else showFormatParameters(
            Formats.entries.first {
                initialTraitObject?.format == it.getDatabaseName()
            }
        )
    }

    private fun getSelectedFormat(): Formats? =
        Formats.entries.find { it.getDatabaseName() == initialTraitObject?.format }
            ?: (traitFormatsRv.adapter as? TraitFormatAdapter)?.selectedFormat

    private fun setupParametersLinearLayout(format: Formats) {

        parametersSv.clear()

        format.getTraitFormatDefinition().parameters.forEach { parameter ->

            val isBrapiTrait = initialTraitObject?.traitDataSource?.let {
                it.isNotEmpty() && it != "local"
            } == true

            // show the DisplayValueParameter only for brapi traits
            if (parameter is DisplayValueParameter && !isBrapiTrait) {
                return@forEach
            }

            if (parameter is ResourceFileParameter) {

                parameter.setActivity(activity)

            }

            parameter.createViewHolder(parametersSv)?.let { holder ->

                holder.bind(parameter, initialTraitObject)

                parametersSv.addViewHolder(holder)
            }
        }
    }


    private fun setupTraitFormatsRv(formats: List<Formats>) {

        context?.let { ctx ->

            val formatsAdapter = TraitFormatAdapter(ctx, this)

            formatsAdapter.selectedFormat = null

            traitFormatsRv.adapter = formatsAdapter

            formatsAdapter.submitList(formats)
        }
    }

    private fun onSave(format: Formats) {

        var pass = true

        if (validateParameters().result != true) pass = false

        if (pass && initialTraitObject == null) {

            if (validateFormat().result != true) {

                pass = false

            } else {

                val pos: Int = database.maxPositionFromTraits + 1

                val t = createTraitObjectFromUi()

                t.realPosition = pos

                database.insertTraits(t)

                onSaveFinish()
            }

        } else if (pass) {

            initialTraitObject?.let { traitObject ->

                if (validateFormat().result != true && !isBrapiTraitImport) {

                    pass = false

                } else {

                    context?.let {

                        val t = updateInitialTraitObjectFromUi(traitObject)

                        t.format = format.getDatabaseName()

                        if (isBrapiTraitImport) {

                            (activity as? TraitObjectUpdateListener)?.onTraitObjectUpdated(t)

                        } else {

                            updateDatabaseTrait(t)

                        }

                        onSaveFinish()
                    }
                }
            }
        }

        if (!pass) {

            vibrator.vibrate()

            soundHelperImpl.playError()
        }
    }

    private fun onSaveFinish() {

        if (!isSelectingFormat && !isBrapiTraitImport) {

            prefs.edit { putBoolean(GeneralKeys.TRAITS_EXPORTED, false) }

            CollectActivity.reloadData = true

            soundHelperImpl.playCelebrate()
        }

        (activity as? TraitDialogDismissListener)?.onNewTraitDialogDismiss()

        dismiss()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)

        initialTraitObject = null
        originalInitialTraitObject = null

        parametersSv.clear()
        traitFormatsRv.adapter = null
    }

    private fun onCancel() {

        if (initialTraitObject == null) dismiss()

        initialTraitObject?.let { traitObject ->

            val t = updateInitialTraitObjectFromUi(traitObject)

            if (t != originalInitialTraitObject) {

                askUserToVerifyDismiss()

            } else dismiss()
        }
    }

    private fun askUserToVerifyDismiss() {

        val builder = AlertDialog.Builder(activity, R.style.AppAlertDialog)

        builder.setTitle(activity.getString(R.string.dialog_close))

        builder.setMessage(activity.getString(R.string.dialog_confirm))

        builder.setPositiveButton(activity.getString(R.string.dialog_yes)) { dialog, _ ->
            dialog.dismiss()
            dismiss()
        }

        builder.setNegativeButton(activity.getString(R.string.dialog_no)) { dialog, _ ->
            dialog.dismiss()
        }

        val alert = builder.create()

        alert.show()
    }

    /**
     * Takes a trait object (initialTraitObject) and loads parameters from UI into it.
     */
    private fun updateInitialTraitObjectFromUi(traitObject: TraitObject): TraitObject {

        return parametersSv.merge(traitObject)
    }

    /**
     * Returns a new trait object based on the UI parameters
     */
    private fun createTraitObjectFromUi(): TraitObject {

        val index = (traitFormatsRv.adapter as TraitFormatAdapter).selectedFormat?.ordinal
            ?: Formats.TEXT.ordinal
        val format = Formats.entries[index]
        var t = TraitObject()
        t.format = format.getDatabaseName()

        t = parametersSv.merge(t)

        t.visible = true
        t.traitDataSource = "local"

        return t
    }

    /**
     * The trait object passed should already have UI loaded values.
     * Simply pass these to the DataHelper editTraits function to call SQL update.
     */
    private fun updateDatabaseTrait(traitObject: TraitObject) {

        database.editTraits(
            traitObject.id,
            traitObject.name,
            traitObject.alias,
            traitObject.format,
            traitObject.defaultValue,
            traitObject.minimum,
            traitObject.maximum,
            traitObject.details,
            traitObject.categories,
            traitObject.closeKeyboardOnOpen,
            traitObject.cropImage,
            traitObject.saveImage,
            traitObject.useDayOfYear,
            traitObject.categoryDisplayValue,
            traitObject.resourceFile,
            traitObject.synonyms,
            traitObject.maxDecimalPlaces,
            traitObject.mathSymbolsEnabled,
            traitObject.allowMulticat,
            traitObject.repeatedMeasures,
            traitObject.autoSwitchPlot,
            traitObject.unit
        )
    }

    private fun validateFormat(): ValidationResult {

        context?.let {
            getSelectedFormat()?.let { selectedFormat ->
                return parametersSv.validateFormat(selectedFormat)
            }
        }

        return ValidationResult()
    }

    private fun validateParameters(): ValidationResult {

        return parametersSv.validateParameters(
            database = database,
            initialTraitObject = initialTraitObject
        )
    }

    private fun showFormatParameters() {

        val adapter = traitFormatsRv.adapter as? TraitFormatAdapter

        if (adapter?.selectedFormat != null) {

            context?.let {

                initialTraitObject?.format = adapter.selectedFormat?.getDatabaseName().toString()

                showFormatParameters(adapter.selectedFormat ?: Formats.TEXT)

            }
        }
    }

    private var isShowingCameraOptions = false
    private var isShowingSpectralOptions = false

    override fun onSelected(format: Formats) {

        if (format == Formats.BASE_PHOTO && !isShowingCameraOptions) {

            isShowingCameraOptions = true

            traitFormatsRv.adapter = null

            showFormatLayouts(Formats.getCameraFormats(), showBack = true)

        } else if (format == Formats.BASE_SPECTRAL && !isShowingSpectralOptions) {

            isShowingSpectralOptions = true

            traitFormatsRv.adapter = null

            showFormatLayouts(Formats.getSpectralFormats(), showBack = true)

        } else {

            if (isSelectingFormat) {
                (activity as? TraitDialogFormatListener)?.onFormatSelected(format)
                (activity as? TraitDialogDismissListener)?.onNewTraitDialogDismiss()
                dismiss()
            } else {
                showFormatParameters(format)
            }
        }
    }
}