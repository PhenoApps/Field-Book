package com.fieldbook.tracker.dialogs

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.activities.TraitEditorActivity
import com.fieldbook.tracker.adapters.TraitAdapter
import com.fieldbook.tracker.adapters.TraitFormatAdapter
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.offbeat.traits.formats.Formats
import com.fieldbook.tracker.offbeat.traits.formats.ParameterRecyclerView
import com.fieldbook.tracker.offbeat.traits.formats.TraitFormatParametersAdapter
import com.fieldbook.tracker.offbeat.traits.formats.ValidationResult
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.DialogUtils
import com.fieldbook.tracker.utilities.SoundHelperImpl
import com.fieldbook.tracker.utilities.VibrateUtil
import dagger.hilt.android.AndroidEntryPoint
import org.phenoapps.utils.SoftKeyboardUtil
import javax.inject.Inject

//TODO this class needs refactoring @chaneylc

@AndroidEntryPoint
class NewTraitDialog(private val activity: Activity) :
    DialogFragment(),
    TraitFormatParametersAdapter.TraitFormatAdapterController {

    @Inject
    lateinit var soundHelperImpl: SoundHelperImpl

    @Inject
    lateinit var vibrator: VibrateUtil

    @Inject
    lateinit var prefs: SharedPreferences

    @Inject
    lateinit var database: DataHelper

    // UI elements of new trait dialog
    private lateinit var traitFormatsRv: RecyclerView
    private lateinit var traitParameterRv: ParameterRecyclerView

    private var negativeBtn: Button? = null
    private var positiveBtn: Button? = null

    //holds the trait objects sent from trait editor activity
    private var initialTraitObject: TraitObject? = null

    //when editing this tracks the original object, to see if values changed when discarding
    private var originalInitialTraitObject: TraitObject? = null

    //private var createVisible: Boolean
    private var brapiDialogShown = false
    private val traitAdapter: TraitAdapter = (activity as TraitEditorActivity).adapter

//    init {
//        oldTrait = null
//        setBrAPIDialogShown((activity as TraitEditorActivity).brAPIDialogShown)
//        createVisible = true
//        optionalHint = getResString(R.string.traits_create_optional)
//        categoriesHint = getResString(R.string.traits_create_categories_text)
//
//    }

    override fun onStart() {
        super.onStart()

        positiveBtn = (dialog as AlertDialog?)?.getButton(AlertDialog.BUTTON_POSITIVE)
        negativeBtn = (dialog as AlertDialog?)?.getButton(AlertDialog.BUTTON_NEGATIVE)

        /**
         * EditText's inside RV's inside a dialog fragment need certain window flags to be cleared
         * for the software keyboard to show.
         */
        dialog?.window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        dialog?.window?.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        val params = dialog?.window?.attributes
        params?.width = LinearLayout.LayoutParams.MATCH_PARENT
        params?.height = LinearLayout.LayoutParams.WRAP_CONTENT
        dialog?.window?.attributes = params
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        context?.let { ctx ->
            show(ctx)
        }
    }

    private fun showFormatLayouts() {

        dialog?.setTitle(context?.getString(R.string.trait_creator_title_layout))

        traitFormatsRv.visibility = View.VISIBLE
        traitParameterRv.visibility = View.GONE

        negativeBtn?.setText(R.string.dialog_cancel)
        negativeBtn?.setOnClickListener {
            dismiss()
        }

        positiveBtn?.setText(R.string.next)
        positiveBtn?.setOnClickListener {
            val adapter = traitFormatsRv.adapter as? TraitFormatAdapter

            if (adapter?.selectedFormat != null) {

                context?.let { ctx ->

                    initialTraitObject?.format = adapter.selectedFormat?.getDatabaseName(ctx)

                    showFormatParameters(adapter.selectedFormat ?: Formats.TEXT)

                }
            }
        }

        if (traitFormatsRv.adapter == null) {

            context?.let { ctx ->

                setupTraitFormatsRv(getSelectedFormat(ctx))

            }
        }
    }

    private fun showFormatParameters(format: Formats) {

        traitFormatsRv.visibility = View.GONE
        traitParameterRv.visibility = View.VISIBLE

        //if editing a variable and observations exist, don't allow the format to change
        var observationsExist = false
        if (initialTraitObject != null) {
            initialTraitObject?.id?.let { traitDbId ->
                observationsExist = database.getAllObservationsOfVariable(traitDbId).isNotEmpty()
            }
        }

        if (initialTraitObject == null || !observationsExist) {

            negativeBtn?.setText(R.string.dialog_back)
            negativeBtn?.setOnClickListener {
                //close keyboard programmatically
                SoftKeyboardUtil.closeKeyboard(context, traitFormatsRv, 1L)

                if (observationsExist) {
                    onCancel()
                }

                showFormatLayouts()
            }

        } else {

            negativeBtn?.setText(R.string.dialog_cancel)
            negativeBtn?.setOnClickListener {
                onCancel()
            }

        }

        positiveBtn?.setText(R.string.dialog_save)
        positiveBtn?.setOnClickListener {
            onSave()
        }

        setupParameterRecyclerView()

        loadFormatParameterAdapterItems(Formats.values().indexOf(format))

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

        traitFormatsRv = view.findViewById(R.id.dialog_new_trait_formats_rv)
        traitParameterRv = view.findViewById(R.id.dialog_new_trait_parameter_rv)

        return builder.create()
    }

    private fun show(ctx: Context) {
        if (initialTraitObject == null) showFormatLayouts() else showFormatParameters(
            Formats.values().first {
                initialTraitObject?.format == it.getDatabaseName(ctx)
            }
        )
    }

    private fun getSelectedFormat(ctx: Context) =
        Formats.values().find { it.getDatabaseName(ctx) == initialTraitObject?.format }
            ?: (traitFormatsRv.adapter as? TraitFormatAdapter)?.selectedFormat
            ?: Formats.TEXT

    private fun setupParameterRecyclerView() {

        context?.let { ctx ->

            val format = getSelectedFormat(ctx)

            val adapter = TraitFormatParametersAdapter(this, format)

            traitParameterRv.adapter = adapter.also {

                it.initialTraitObject = initialTraitObject
            }
        }
    }

    private fun setupTraitFormatsRv(format: Formats) {

        context?.let { ctx ->

            val formatsAdapter = TraitFormatAdapter(ctx)

            formatsAdapter.selectedFormat = format

            traitFormatsRv.adapter = formatsAdapter

            formatsAdapter.submitList(Formats.values().toList())

        }
    }

    private fun loadFormatParameterAdapterItems(position: Int) {

        val format = Formats.values()[position]
        val definition = format.getTraitFormatDefinition()
        val parameters = definition.parameters

        (traitParameterRv.adapter as TraitFormatParametersAdapter).submitList(parameters)
    }

    private fun onSave() {

        traitParameterRv.adapter?.itemCount?.let { count ->
            traitParameterRv.scrollToPosition(0)
            traitParameterRv.scrollToPosition(count)
        }

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

                if (validateFormat(traitObject).result != true) {

                    pass = false

                } else {

                    val t = updateInitialTraitObjectFromUi(traitObject)

                    updateDatabaseTrait(t)

                    onSaveFinish()
                }
            }
        }

        if (!pass) {

            vibrator.vibrate()

            soundHelperImpl.playError()
        }
    }

    private fun onSaveFinish() {

        val ed = this.prefs.edit()
        ed.putBoolean(GeneralKeys.CREATE_TRAIT_FINISHED, true)
        ed.putBoolean(GeneralKeys.TRAITS_EXPORTED, false)
        ed.apply()

        // Display our BrAPI dialog if it has not been show already
        // Get our dialog state from our adapter to see if a trait has been selected
        // brapiDialogShown = mAdapter.infoDialogShown;
//        setBrAPIDialogShown(traitAdapter.infoDialogShown)
//        if (!brapiDialogShown) {
//            setBrAPIDialogShown(
//                (activity as TraitAdapterController)
//                    .displayBrapiInfo(activity, null, true)
//            )
//        }

        //TODO replace with onDismiss listener like in DatePickerDialog
        (activity as TraitEditorActivity).queryAndLoadTraits()

        //TODO do we really need reloadData here
        CollectActivity.reloadData = true

        soundHelperImpl.playCelebrate()

        dismiss()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)

        initialTraitObject = null
        originalInitialTraitObject = null
    }

    private fun onCancel() {

        if (initialTraitObject == null) {

            //dismiss()

        }

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

        DialogUtils.styleDialogs(alert)
    }

    /**
     * Takes a trait object (initialTraitObject) and loads parameters from UI into it.
     */
    private fun updateInitialTraitObjectFromUi(traitObject: TraitObject): TraitObject {

        return traitParameterRv.merge(traitObject)
    }

    /**
     * Returns a new trait object based on the UI parameters
     */
    private fun createTraitObjectFromUi(): TraitObject {

        val index = (traitFormatsRv.adapter as TraitFormatAdapter).selectedFormat?.ordinal
            ?: Formats.TEXT.ordinal
        val format = Formats.values()[index]
        var t = TraitObject()
        t.format = format.getDatabaseName(activity)

        t = traitParameterRv.merge(t)

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
            traitObject.format,
            traitObject.defaultValue,
            traitObject.minimum,
            traitObject.maximum,
            traitObject.details,
            traitObject.categories
        )
    }

    private fun validateFormat(initialTraitObject: TraitObject? = null): ValidationResult {

        context?.let { ctx ->
            return traitParameterRv.validateFormat(getSelectedFormat(ctx))
        }

        return ValidationResult()
    }

    private fun validateParameters(): ValidationResult {

        return traitParameterRv.validateParameters(
            database = database,
            initialTraitObject = initialTraitObject
        )
    }

    fun setTraitObject(traitObject: TraitObject) {

        initialTraitObject = traitObject
        originalInitialTraitObject = traitObject

    }
//
//    fun prepareFields(index: Int) {
////        val traitFormat = traitFormats.getTraitFormatByIndex(index)
////        detailsEt.hint = traitFormat.detailsBox()!!.parameterHint
////        defaultValueEt.hint = traitFormat.defaultBox()!!.parameterHint
////        minimumEt.hint = traitFormat.minimumBox()!!.parameterHint
////        maximumEt.hint = traitFormat.maximumBox()!!.parameterHint
////        defaultValueToggleBtn.visibility = viewVisibility(traitFormat.isBooleanVisible)
//    }
//
//    // when this value changes in this class,
//    // the value in TraitEditorActivity must change
//    private fun setBrAPIDialogShown(b: Boolean) {
//        brapiDialogShown = b
//        (activity as TraitEditorActivity).brAPIDialogShown = b
//    }
//
//    companion object {
//        // Non negative numbers only
//        private fun isNumeric(str: String, positive: Boolean): Boolean {
//            return if (str.isEmpty()) false else try {
//                val d = str.toDouble()
//                !positive || d >= 0
//            } catch (nfe: NumberFormatException) {
//                false
//            }
//        }
//
//        private fun isNumericOrEmpty(str: String, positive: Boolean): Boolean {
//            return str.isEmpty() || isNumeric(str, positive)
//        }
//    }
}