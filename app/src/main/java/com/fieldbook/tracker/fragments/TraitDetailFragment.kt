package com.fieldbook.tracker.fragments

import android.app.AlertDialog
import android.app.Dialog
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.isGone
import androidx.core.view.size
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.activities.TraitEditorActivity
import com.fieldbook.tracker.charts.HistogramChartHelper
import com.fieldbook.tracker.charts.HorizontalBarChartHelper
import com.fieldbook.tracker.charts.PieChartHelper
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.databinding.FragmentTraitDetailBinding
import com.fieldbook.tracker.dialogs.FileExploreDialogFragment
import com.fieldbook.tracker.dialogs.composables.DialogTheme
import com.fieldbook.tracker.dialogs.composables.TextInputDialog
import com.fieldbook.tracker.objects.FieldFileObject
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.traits.formats.Formats
import com.fieldbook.tracker.traits.formats.NumericFormat
import com.fieldbook.tracker.traits.formats.TraitFormat
import com.fieldbook.tracker.traits.formats.parameters.AutoSwitchPlotParameter
import com.fieldbook.tracker.traits.formats.parameters.BaseFormatParameter
import com.fieldbook.tracker.traits.formats.parameters.CategoriesParameter
import com.fieldbook.tracker.traits.formats.parameters.CloseKeyboardParameter
import com.fieldbook.tracker.traits.formats.parameters.CropImageParameter
import com.fieldbook.tracker.traits.formats.parameters.DecimalPlacesParameter
import com.fieldbook.tracker.traits.formats.parameters.DefaultToggleParameter
import com.fieldbook.tracker.traits.formats.parameters.InvalidValueParameter
import com.fieldbook.tracker.traits.formats.parameters.MathSymbolsParameter
import com.fieldbook.tracker.traits.formats.parameters.MultipleCategoriesParameter
import com.fieldbook.tracker.traits.formats.parameters.Parameters
import com.fieldbook.tracker.traits.formats.parameters.RepeatedMeasureParameter
import com.fieldbook.tracker.traits.formats.parameters.ResourceFileParameter
import com.fieldbook.tracker.traits.formats.parameters.SaveImageParameter
import com.fieldbook.tracker.traits.formats.parameters.UnitParameter
import com.fieldbook.tracker.utilities.CategoryJsonUtil
import com.fieldbook.tracker.utilities.InsetHandler
import com.fieldbook.tracker.utilities.SoundHelperImpl
import com.fieldbook.tracker.utilities.TraitNameValidator
import com.fieldbook.tracker.utilities.Utils
import com.fieldbook.tracker.utilities.VibrateUtil
import com.fieldbook.tracker.viewmodels.CopyTraitStatus
import com.fieldbook.tracker.viewmodels.TraitDetailUiState
import com.fieldbook.tracker.viewmodels.TraitDetailViewModel
import com.fieldbook.tracker.viewmodels.factory.TraitDetailViewModelFactory
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import dagger.hilt.android.AndroidEntryPoint
import org.phenoapps.utils.BaseDocumentTreeUtil
import java.math.BigDecimal
import java.util.Calendar
import javax.inject.Inject

@AndroidEntryPoint
class TraitDetailFragment : Fragment() {

    companion object {
        private const val TAG = "TraitDetailFragment"
    }

    @Inject
    lateinit var database: DataHelper

    @Inject
    lateinit var preferences: SharedPreferences

    @Inject
    lateinit var soundHelperImpl: SoundHelperImpl

    @Inject
    lateinit var vibrator: VibrateUtil

    private lateinit var binding: FragmentTraitDetailBinding

    private val viewModel: TraitDetailViewModel by viewModels { TraitDetailViewModelFactory(database) }

    private var traitId: String? = null
    private var traitObject: TraitObject? = null

    // excludes certain parameters from being shown in chips
    val excludedParams = setOf(
        Parameters.NAME,
        Parameters.DEFAULT_VALUE,
        Parameters.MAXIMUM,
        Parameters.MINIMUM,
        Parameters.DETAILS,

        // below params are handled separately, exclude these as well
        Parameters.RESOURCE_FILE,
        Parameters.USE_DAY_OF_YEAR,
        Parameters.DISPLAY_VALUE,
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View? {
        binding = FragmentTraitDetailBinding.inflate(layoutInflater)

        traitId = arguments?.getString("traitId")

        traitId?.let { viewModel.loadTraitDetails(database.valueFormatter, it) }

        observeTraitDetailViewModel()

        setupAllCollapsibleSections()

        InsetHandler.setupFragmentWithTopInsetsOnly(binding.root, binding.toolbar)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.root.setOnTouchListener { v, event ->
            // Consume touch event to prevent propagation to TraitEditor RecyclerView
            true
        }

        setupClickListeners()
    }

    fun refresh() {
        traitId?.let { viewModel.loadTraitDetails(database.valueFormatter, it) }
    }

    private fun observeTraitDetailViewModel() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is TraitDetailUiState.Loading -> {
                    binding.traitDetailProgressBar.visibility = View.VISIBLE
                }
                is TraitDetailUiState.Error -> {
                    binding.traitDetailProgressBar.visibility = View.GONE
                    Utils.makeToast(context, getString(state.messageRes))
                }
                is TraitDetailUiState.Success -> {
                    binding.traitDetailProgressBar.visibility = View.GONE
                    traitObject = state.trait
                    updateTraitData(state.trait)
                    if (binding.toolbar.menu?.size == 0) { // setup toolbar menu
                        setupToolbar()
                    }

                    state.observationData?.let { renderObservationData(it) }
                }
            }
        }

        viewModel.copyTraitStatus.observe(viewLifecycleOwner) { status ->
            when (status) {
                is CopyTraitStatus.Error -> {
                    Utils.makeToast(context, getString(status.messageRes))
                }
                is CopyTraitStatus.Success -> {
                    // refresh UI
                    (activity as? TraitEditorActivity)?.queryAndLoadTraits()
                    CollectActivity.reloadData = true
                }
            }
        }
    }

    private fun renderObservationData(data: TraitDetailViewModel.ObservationData) {
        binding.fieldCountChip.text = data.fieldCount.toString()
        binding.observationCountChip.text = data.observationCount.toString()

        val chartTextSize = getChartTextSize()
        PieChartHelper.setupPieChart(requireContext(), binding.completenessChart, data.completeness, chartTextSize)

        traitObject?.let { trait ->
            if (data.processedObservations.isNotEmpty()) {
                setupObservationChart(trait, data.processedObservations, chartTextSize)
            } else {
                showNoChartMessage(getString(R.string.field_trait_chart_no_data))
            }
        }
    }

    private fun setupAllCollapsibleSections() {
        setupCollapsibleSection(
            binding.overviewCollapsibleHeader,
            binding.overviewCollapsibleContent,
            binding.overviewExpandCollapseIcon,
            GeneralKeys.TRAIT_DETAIL_OVERVIEW_COLLAPSED
        )

        setupCollapsibleSection(
            binding.optionsCollapsibleHeader,
            binding.optionsCollapsibleContent,
            binding.optionsExpandCollapseIcon,
            GeneralKeys.TRAIT_DETAIL_OPTIONS_COLLAPSED
        )

        setupCollapsibleSection(
            binding.dataCollapsibleHeader,
            binding.dataCollapsibleContent,
            binding.dataExpandCollapseIcon,
            GeneralKeys.TRAIT_DETAIL_DATA_COLLAPSED
        )
    }

    private fun setupCollapsibleSection(header: LinearLayout, content: LinearLayout, icon: ImageView, prefKey: String) {
        val isCollapsed = preferences.getBoolean(prefKey, false)
        content.visibility = if (isCollapsed) View.GONE else View.VISIBLE
        icon.setImageResource(if (isCollapsed) R.drawable.ic_chevron_down else R.drawable.ic_chevron_up)

        header.setOnClickListener {
            if (content.isGone) {
                content.visibility = View.VISIBLE
                icon.setImageResource(R.drawable.ic_chevron_up)
                preferences.edit { putBoolean(prefKey, false) }
            } else {
                content.visibility = View.GONE
                icon.setImageResource(R.drawable.ic_chevron_down)
                preferences.edit { putBoolean(prefKey, true) }
            }
        }
    }

    private fun updateTraitData(trait: TraitObject) {
        binding.traitDisplayName.text = trait.alias

        binding.sourceChip.text = trait.traitDataSource
        binding.formatChip.text = trait.format

        val formatEnum = Formats.entries.find { it.getDatabaseName() == trait.format }
        binding.formatChip.chipIcon = ContextCompat.getDrawable(requireContext(),
            formatEnum?.getIcon() ?: R.drawable.ic_trait_categorical)

        updateVisibilityChip(trait)

        setupOptionChips(trait)
    }

    private fun setupOptionChips(trait: TraitObject) {
        binding.optionsChipGroup.removeAllViews()

        addResourceFileChip(trait)

        addFormatSpecificOptionChips(trait)

        addParameterChips(trait)
    }

    private fun addResourceFileChip(trait: TraitObject) {
        val chipLabel = if (trait.resourceFile.isNotEmpty()) {
            val fileObject = FieldFileObject.create(requireContext(), trait.resourceFile.toUri(), null, null)
            fileObject.fileStem
        } else {
            getString(R.string.trait_parameter_resource_file).capitalizeFirstLetter()
        }

        addChip(chipLabel, R.drawable.ic_tb_folder) { chip ->
            val dir = BaseDocumentTreeUtil.Companion.getDirectory(requireContext(), R.string.dir_resources)
            if (dir != null && dir.exists()) {
                val dialog = FileExploreDialogFragment().apply {

                    arguments = Bundle().apply {
                        putString("path", dir.uri.toString())
                        putString("dialogTitle", this@TraitDetailFragment.getString(R.string.main_toolbar_resources))
                        putStringArray("include", arrayOf("jpg", "jpeg", "png", "bmp"))
                    }

                    setOnFileSelectedListener { selectedUri ->
                        viewModel.updateResourceFile(trait.id, selectedUri.toString())
                        chip.text = selectedUri.lastPathSegment?.substringAfterLast('/') ?: selectedUri.toString()
                    }
                }

                dialog.show(parentFragmentManager, "FileExploreDialog")
            } else {
                Toast.makeText(requireContext(), R.string.error_storage_directory, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun addFormatSpecificOptionChips(trait: TraitObject) {
        if (trait.format == "date") {
            val formatString = if (trait.useDayOfYear) getTodayDayOfYear() else getTodayFormattedDate()
            val chipLabel = getString(R.string.trait_detail_chip_format_date, formatString)
            addChip(chipLabel, R.drawable.ic_calendar_edit) { showDateFormatDialog(trait) }
        }

        val isBrapiTrait = trait.externalDbId != null && (trait.externalDbId?.isNotEmpty() == true)
                || trait.traitDataSource.contains("brapi", ignoreCase = true) == true
        val traitHasBrapiCategories = isBrapiTrait && trait.format == "categorical" && trait.categories.isNotEmpty()

        if (traitHasBrapiCategories) {
            val firstCategory = parseCategoryExample(trait.categories)
            val chipLabel = if (trait.categoryDisplayValue) {
                getString(R.string.trait_brapi_value_display, firstCategory.second)
            } else {
                getString(R.string.trait_brapi_label_display, firstCategory.first)
            }

            addChip(chipLabel, R.drawable.ic_tag_edit) {
                showBrapiLabelValueDialog(trait)
            }
        }
    }

    private fun addParameterChips(trait: TraitObject) {
        val format = Formats.entries.find { it.getDatabaseName() == trait.format } ?: return
        val formatDefinition = format.getTraitFormatDefinition()

        val displayableParams = formatDefinition.parameters.filter {
            it.parameter !in excludedParams
        }

        displayableParams.forEach { param ->
            addParameterChip(param, trait)
        }
    }

    private fun addParameterChip(parameter: BaseFormatParameter, trait: TraitObject) {

        val iconRes = when (parameter) {
            is DefaultToggleParameter -> {
                val currentValue = parameter.getToggleValue(trait)
                when (parameter) {
                    is AutoSwitchPlotParameter -> if (currentValue) R.drawable.ic_auto_switch else R.drawable.ic_auto_switch_off
                    is CloseKeyboardParameter -> if (currentValue) R.drawable.ic_keyboard_close else R.drawable.ic_keyboard_close_off
                    is CropImageParameter -> if (currentValue) R.drawable.ic_crop_image else R.drawable.ic_crop_image_off
                    is InvalidValueParameter -> if (currentValue) R.drawable.ic_outlier else R.drawable.ic_outlier_off
                    is MathSymbolsParameter -> if (currentValue) R.drawable.ic_symbol else R.drawable.ic_symbol_off
                    is MultipleCategoriesParameter -> if (currentValue) R.drawable.ic_trait_multicat else R.drawable.ic_trait_multicat_off
                    is RepeatedMeasureParameter -> if (currentValue) R.drawable.ic_repeated_measures else R.drawable.ic_repeated_measures_off
                    is SaveImageParameter -> if (currentValue) R.drawable.ic_transfer else R.drawable.ic_transfer_off
                    else -> R.drawable.ic_tag_edit
                }
            }

            // non-toggle based parameters
            is CategoriesParameter -> R.drawable.ic_trait_categorical
            is DecimalPlacesParameter -> R.drawable.ic_decimal
            is UnitParameter -> R.drawable.ic_tag_edit
            is ResourceFileParameter -> R.drawable.ic_tb_folder
            else -> R.drawable.ic_tag_edit
        }

        val chipLabel = when (parameter) {
            is AutoSwitchPlotParameter -> getString(R.string.trait_detail_chip_automatic_switch)
            is InvalidValueParameter -> getString(R.string.trait_detail_chip_invalid_value)
            is MathSymbolsParameter -> getString(R.string.trait_detail_chip_math_symbols)
            is MultipleCategoriesParameter -> getString(R.string.trait_detail_chip_multiple_categories)
            is SaveImageParameter -> getString(R.string.trait_detail_chip_transfer_images)
            else -> context?.let { parameter.getName(it).capitalizeFirstLetter() }
        }

        chipLabel?.let { label ->
            addChip(label, iconRes) { chip ->
                parameter.toggleValue(trait)?.let { newValue -> // toggle parameter
                    viewModel.updateTraitOptions(database.valueFormatter, trait)
                } ?: run { // not a toggle parameter, show dialog
                    showParameterEditDialog(parameter, trait)
                }
            }
        }
    }

    private fun String.capitalizeFirstLetter(): String {
        return if (isEmpty()) this else this.lowercase().replaceFirstChar { it.uppercaseChar() }
    }

    private fun addChip(label: String, iconRes: Int, onClick: (Chip) -> Unit) {
        val chip = Chip(requireContext()).apply {
            text = label
            chipIcon = ContextCompat.getDrawable(requireContext(), iconRes)

            val typedValue = TypedValue()
            requireContext().theme.resolveAttribute(
                R.attr.selectableChipBackground,
                typedValue,
                true
            )
            setChipBackgroundColorResource(typedValue.resourceId)
            requireContext().theme.resolveAttribute(R.attr.selectableChipStroke, typedValue, true)
            setChipStrokeColorResource(typedValue.resourceId)
            chipStartPadding = resources.getDimension(R.dimen.chip_start_padding)
            chipStrokeWidth = resources.getDimension(R.dimen.chip_stroke_width)
            chipIconSize = resources.getDimension(R.dimen.chip_icon_size)
            isCloseIconVisible = false

            setEnsureMinTouchTargetSize(false)
            layoutParams = ChipGroup.LayoutParams(
                ChipGroup.LayoutParams.WRAP_CONTENT,
                ChipGroup.LayoutParams.WRAP_CONTENT
            )

            setOnClickListener {
                onClick.invoke(this)
            }
        }
        binding.optionsChipGroup.addView(chip)
    }

    private fun showParameterEditDialog(parameter: BaseFormatParameter, trait: TraitObject) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_trait_parameter_edit, null)
        val format = Formats.entries.find { it.getDatabaseName() == trait.format }
        val formatDefinition = format?.getTraitFormatDefinition()

        val parameterContainer = dialogView.findViewById<LinearLayout>(R.id.parameter_container)

        parameter.createViewHolder(parameterContainer)?.let { holder ->
            holder.bind(parameter, trait)
            parameterContainer.addView(holder.itemView)

            val dialog = AlertDialog.Builder(requireContext(), R.style.AppAlertDialog)
                .setView(dialogView)
                .setPositiveButton(R.string.dialog_save, null)
                .setNegativeButton(R.string.dialog_cancel, null)
                .create()

            dialog.setOnShowListener {
                val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                positiveButton.setOnClickListener {
                    holder.textInputLayout.error = null

                    // parameter specific validation
                    val paramValidationResult = holder.validate(database, trait)

                    if (paramValidationResult.result != true) {
                        val errorMessage = paramValidationResult.error
                            ?: getString(R.string.error_loading_trait_detail)
                        holder.textInputLayout.error = errorMessage
                        soundHelperImpl.playError()
                        vibrator.vibrate()
                        return@setOnClickListener
                    }

                    val updatedTrait = holder.merge(trait.clone())

                    // inter-parameter validation if needed
                    if (formatDefinition != null && paramHasValidationDependency(formatDefinition, parameter)) {

                        val errorMessage = validateInterParameterValidation(formatDefinition, updatedTrait)

                        if (errorMessage != null) {
                            holder.textInputLayout.error = errorMessage
                            soundHelperImpl.playError()
                            vibrator.vibrate()
                            return@setOnClickListener
                        }
                    }

                    viewModel.updateTraitOptions(database.valueFormatter, updatedTrait)
                    Utils.makeToast(context, getString(R.string.edit_traits))
                    CollectActivity.reloadData = true

                    dialog.dismiss()
                }
            }

            dialog.show()
        }
    }

    /**
     * Returns whether a specific parameter of a trait has validation dependency on another parameter
     * eg. for numeric trait, decimalPlaces restriction and mathSymbols cannot both be enabled
     */
    private fun paramHasValidationDependency(formatDefinition: TraitFormat, parameter: BaseFormatParameter): Boolean {
        return when (formatDefinition) {
            is NumericFormat -> {
                parameter is DecimalPlacesParameter || parameter is MathSymbolsParameter
            }
            else -> false
        }
    }

    /**
     * Validates inter-parameter dependencies and returns error message if validation fails
     */
    private fun validateInterParameterValidation(formatDefinition: TraitFormat, trait: TraitObject): String? {
        return when (formatDefinition) {
            is NumericFormat -> validateNumericInterParameters(trait)
            else -> null
        }
    }

    /**
     * Validates numeric trait inter-parameter dependencies (decimal places vs math symbols)
     */
    private fun validateNumericInterParameters(trait: TraitObject): String? {
        val mathSymbolsEnabled = trait.mathSymbolsEnabled
        val decimalPlaces = trait.maxDecimalPlaces.toIntOrNull() ?: -1

        if (mathSymbolsEnabled && decimalPlaces >= 0) {
            return getString(R.string.traits_create_warning_math_symbols_conflict)
        }

        return null
    }

    private fun showSwapNameDialog(trait: TraitObject) {
        val synonyms = trait.synonyms.toTypedArray()

        // check if current alias is present in synonyms array
        val currentSelection = synonyms.indexOf(trait.alias).takeIf { it >= 0 } ?: -1

        var selectedSynonym: String? = null
        AlertDialog.Builder(requireContext(), R.style.AppAlertDialog)
            .setTitle(getString(R.string.trait_swap_name_dialog_title))
            .setSingleChoiceItems(synonyms, currentSelection) { _, which ->
                selectedSynonym = synonyms[which]
            }
            .setPositiveButton(getString(R.string.trait_swap_name_set_alias)) { _, _ ->
                selectedSynonym?.let { newAlias ->
                    val errorRes = TraitNameValidator.validateTraitAlias(newAlias, database, trait)
                    if (errorRes != null) {
                        Utils.makeToast(context, getString(errorRes))
                        return@setPositiveButton
                    }
                    viewModel.updateTraitAlias(trait, newAlias)
                }
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .setNeutralButton(getString(R.string.trait_add_synonym_new)) { _, _ ->
                showAddSynonymDialog(trait)
            }
            .show()
    }

    private fun showAddSynonymDialog(trait: TraitObject) {

        val dialog = Dialog(requireContext(), R.style.AppAlertDialog)

        val composeView = ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setViewTreeLifecycleOwner(viewLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(this@TraitDetailFragment)

            setContent {
                DialogTheme {
                    TextInputDialog(
                        title = getString(R.string.trait_add_synonym_new_dialog),
                        hint = getString(R.string.trait_synonym),
                        positiveButtonText = getString(R.string.trait_swap_name_set_alias),
                        onPositive = { newSynonym ->
                            when {
                                newSynonym.isEmpty() -> getString(R.string.trait_add_synonym_new_dialog_blank)

                                trait.synonyms.contains(newSynonym) -> getString(R.string.trait_add_synonym_already_exists)

                                else -> {
                                    val validationError = TraitNameValidator.validateTraitAlias(newSynonym, database, trait)
                                    if (validationError != null) {
                                        getString(validationError)
                                    } else {
                                        viewModel.updateTraitAlias(trait, newSynonym)
                                        dialog.dismiss()
                                        null // no error
                                    }
                                }
                            }
                        },
                        negativeButtonText = getString(R.string.dialog_cancel),
                        onNegative = { dialog.dismiss() },
                        neutralButtonText = getString(R.string.dialog_clear),
                        onNeutral = { }
                    )
                }
            }
        }

        dialog.setContentView(composeView)

        dialog.show()
    }

    private fun showDateFormatDialog(trait: TraitObject) {
        val options = arrayOf(
            getString(R.string.trait_date_format_display, getTodayFormattedDate()),
            getString(R.string.trait_day_format_display, getTodayDayOfYear())
        )

        val currentSelection = if (trait.useDayOfYear) 1 else 0

        AlertDialog.Builder(requireContext(), R.style.AppAlertDialog)
            .setTitle(getString(R.string.trait_date_format_dialog_title))
            .setSingleChoiceItems(options, currentSelection) { dialog, which ->
                val useDayOfYear = which == 1
                viewModel.updateTraitOptions(database.valueFormatter, trait.also {
                    it.useDayOfYear = useDayOfYear
                })
                dialog.dismiss()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun getTodayFormattedDate(): String {
        val calendar = Calendar.getInstance()

        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1 // Calendar months are 0-based
        val day = calendar.get(Calendar.DAY_OF_MONTH)
       return String.format("%04d-%02d-%02d", year, month, day)
    }

    private fun getTodayDayOfYear(): String = Calendar.getInstance().get(Calendar.DAY_OF_YEAR).toString()

    private fun showBrapiLabelValueDialog(trait: TraitObject) {
        val categories = trait.categories
        val firstCategory = parseCategoryExample(categories)

        val options = arrayOf(
            getString(R.string.trait_brapi_label_display, firstCategory.first),
            getString(R.string.trait_brapi_value_display, firstCategory.second)
        )

        val currentSelection = if (trait.categoryDisplayValue) 1 else 0

        AlertDialog.Builder(requireContext(), R.style.AppAlertDialog)
            .setTitle(getString(R.string.trait_brapi_display_dialog_title))
            .setSingleChoiceItems(options, currentSelection) { dialog, which ->
                val useValues = which == 1
                trait.categoryDisplayValue = useValues
                viewModel.updateTraitOptions(database.valueFormatter, trait)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    // Add a helper method to parse category examples
    private fun parseCategoryExample(categories: String): Pair<String, String> {
        return try {
            if (categories.startsWith("[")) {
                // JSON format
                val parsedCategories = CategoryJsonUtil.Companion.decode(categories)
                if (parsedCategories.isNotEmpty()) {
                    val firstItem = parsedCategories[0]
                    val labelField = firstItem.javaClass.getDeclaredField("label")
                    labelField.isAccessible = true
                    val label = labelField.get(firstItem)?.toString() ?: "Label"

                    Pair(label, firstItem.value)
                } else {
                    Pair("Example", "Value")
                }
            } else {
                // Simple format (values only)
                val values = categories.split("/").map { it.trim() }
                if (values.isNotEmpty()) {
                    Pair(values[0], values[0])
                } else {
                    Pair("Example", "Value")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse categories: $categories", e)
            Pair("Example", "Value")
        }
    }

    private fun updateVisibilityChip(trait: TraitObject) {
        binding.visibilityChip.text =
            if (trait.visible) getString(R.string.trait_visible)
            else getString(R.string.trait_hidden)

        binding.visibilityChip.chipIcon = ContextCompat.getDrawable(requireContext(),
            if (trait.visible) R.drawable.ic_eye else R.drawable.ic_eye_off)
    }

    private fun setupClickListeners() {
        setupRenameChipClickListener()
        setupVisibilityChipClickListener()
    }

    private fun setupRenameChipClickListener() {
        binding.renameTrait.setOnClickListener {
            traitObject?.let { trait ->
                if (trait.synonyms.isNotEmpty()) showSwapNameDialog(trait)
                else showAddSynonymDialog(trait)
            }
        }
    }

    private fun setupVisibilityChipClickListener() {
        binding.visibilityChip.setOnClickListener {
            traitObject?.let { trait ->
                val newVisibility = !trait.visible
                viewModel.updateTraitVisibility(trait, newVisibility)

                (activity as? TraitEditorActivity)?.queryAndLoadTraits()
                CollectActivity.reloadData = true
            }
        }
    }

    private fun setupObservationChart(trait: TraitObject, observations: List<String>, textSize: Float) {
        val nonChartableFormats = setOf("audio", "gnss", "gopro", "location", "photo", "text", "usb camera")

        if (trait.format in nonChartableFormats) {
            showNoChartMessage(getString(R.string.field_trait_chart_incompatible_format))
            return
        }

        val filteredObservations = observations.filter { it.isNotEmpty() && it != "NA" }

        if (filteredObservations.isEmpty()) {
            showNoChartMessage(getString(R.string.field_trait_chart_no_data))
            return
        }

        try {
            if (trait.format == "categorical" || trait.format == "boolean") {
                throw NumberFormatException("Categorical traits must use bar chart")
            }

            val numericObservations = filteredObservations.map { BigDecimal(it) }
            binding.barChart.visibility = View.GONE
            binding.histogram.visibility = View.VISIBLE
            binding.noChartAvailableTextView.visibility = View.GONE

            // for numeric traits
            HistogramChartHelper.setupHistogram(
                requireContext(),
                binding.histogram,
                numericObservations,
                textSize
            )
        } catch (_: NumberFormatException) { // for categorical data
            binding.barChart.visibility = View.VISIBLE
            binding.histogram.visibility = View.GONE
            binding.noChartAvailableTextView.visibility = View.GONE

            val parsedCategories = parseCategories(trait.categories)

            HorizontalBarChartHelper.setupHorizontalBarChart(
                requireContext(),
                binding.barChart,
                filteredObservations,
                parsedCategories.takeIf { it.isNotEmpty() },
                textSize
            )
        }
    }

    private fun showNoChartMessage(message: String) {
        binding.barChart.visibility = View.GONE
        binding.histogram.visibility = View.GONE
        binding.noChartAvailableTextView.visibility = View.VISIBLE
        binding.noChartAvailableTextView.text = message
    }

    private fun parseCategories(categories: String): List<String> {
        return try {
            if (categories.startsWith("[")) {
                val parsedCategories = CategoryJsonUtil.Companion.decode(categories)
                parsedCategories.map { it.value }
            } else {
                categories.split("/").map { it.trim() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse categories: $categories", e)
            emptyList()
        }
    }

    private fun getChartTextSize(): Float {
        val typedValue = TypedValue()
        requireContext().theme.resolveAttribute(R.attr.fb_subheading_text_size, typedValue, true)
        val textSizePx = resources.getDimension(typedValue.resourceId)
        return textSizePx / resources.displayMetrics.scaledDensity
    }

    private fun setupToolbar() {
        binding.toolbar.apply {
            inflateMenu(R.menu.menu_trait_details)
            setTitle(R.string.trait_detail_title)
            setNavigationIcon(R.drawable.arrow_left)
            setNavigationOnClickListener {
                parentFragmentManager.popBackStack()
            }
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    android.R.id.home -> {
                        parentFragmentManager.popBackStack()
                    }
                    R.id.copy -> {
                        traitObject?.let { showCopyTraitDialog(it) }
                        true
                    }
                    R.id.edit -> {
                        traitObject?.let { (activity as? TraitEditorActivity)?.showTraitDialog(it) }
                        true
                    }
                    R.id.delete -> {
                        traitObject?.let { showDeleteTraitDialog(it) }
                        true
                    }
                }
                true
            }
        }
    }

    private fun copyTraitName(traitName: String): String {
        var baseName = traitName
        if (baseName.contains("-Copy")) {
            baseName = baseName.substring(0, baseName.indexOf("-Copy"))
        }

        val allTraits = database.getAllTraitObjects()

        var i = 0
        while (true) { // run until no match against names AND aliases
            val newTraitName = "$baseName-Copy-($i)"
            if (!allTraits.any { it.name == newTraitName } &&
                !allTraits.any { it.alias == newTraitName }) {
                return newTraitName
            }
            i++
        }
    }

    private fun showCopyTraitDialog(trait: TraitObject) {
        val input = EditText(requireContext())
        val suggestedName = copyTraitName(trait.alias)
        input.setText(suggestedName)

        AlertDialog.Builder(requireContext(), R.style.AppAlertDialog)
            .setTitle(getString(R.string.traits_options_copy_title))
            .setView(input)
            .setPositiveButton(getString(R.string.dialog_save)) { _, _ ->
                viewModel.copyTrait(trait, input.text.toString().trim())
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }

    private fun showDeleteTraitDialog(trait: TraitObject) {
        AlertDialog.Builder(requireContext(), R.style.AppAlertDialog)
            .setTitle(getString(R.string.traits_options_delete_title))
            .setMessage(getString(R.string.traits_warning_delete))
            .setPositiveButton(getString(R.string.dialog_yes)) { d, _ ->
                (activity as? TraitEditorActivity)?.deleteTrait(trait)
                parentFragmentManager.popBackStack()
                d.dismiss()
            }
            .setNegativeButton(getString(R.string.dialog_no)) { d, _ ->
                d.dismiss()
            }
            .show()
    }
}