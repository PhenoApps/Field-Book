package com.fieldbook.tracker.activities

import android.app.AlertDialog
import android.content.Intent
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
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import com.fieldbook.tracker.R
import com.fieldbook.tracker.charts.HistogramChartHelper
import com.fieldbook.tracker.charts.HorizontalBarChartHelper
import com.fieldbook.tracker.charts.PieChartHelper
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.objects.FieldFileObject
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.preferences.PreferenceKeys
import com.fieldbook.tracker.traits.formats.Formats
import com.fieldbook.tracker.utilities.CategoryJsonUtil
import dagger.hilt.android.AndroidEntryPoint
import org.phenoapps.utils.BaseDocumentTreeUtil
import java.math.BigDecimal
import java.util.Calendar
import javax.inject.Inject
import androidx.core.view.size
import androidx.fragment.app.viewModels
import com.fieldbook.tracker.database.dao.spectral.SpectralDao
import com.fieldbook.tracker.database.repository.SpectralRepository
import com.fieldbook.tracker.databinding.FragmentTraitDetailBinding
import com.fieldbook.tracker.utilities.TraitNameValidator
import com.fieldbook.tracker.utilities.TraitNameValidator.validateTraitAlias
import com.fieldbook.tracker.utilities.Utils
import com.fieldbook.tracker.utilities.export.SpectralFileProcessor
import com.fieldbook.tracker.utilities.export.ValueProcessorFormatAdapter
import com.fieldbook.tracker.viewmodels.CopyTraitStatus
import com.fieldbook.tracker.viewmodels.TraitDetailUiState
import com.fieldbook.tracker.viewmodels.TraitDetailViewModel
import com.fieldbook.tracker.viewmodels.factory.TraitDetailViewModelFactory

@AndroidEntryPoint
class TraitDetailFragment : Fragment() {

    companion object {
        private const val TAG = "TraitDetailFragment"
    }

    @Inject
    lateinit var database: DataHelper

    @Inject
    lateinit var preferences: SharedPreferences

    private lateinit var binding: FragmentTraitDetailBinding

    private val viewModel: TraitDetailViewModel by viewModels { TraitDetailViewModelFactory(database) }

    private var traitId: String? = null
    private var traitObject: TraitObject? = null
    private var traitHasBrapiCategories = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View? {
        binding = FragmentTraitDetailBinding.inflate(layoutInflater)

        traitId = arguments?.getString("traitId")

        traitId?.let { viewModel.loadTraitDetails(database.valueFormatter, it) }

        observeTraitDetailViewModel()

        setupAllCollapsibleSections()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.root.setOnTouchListener { v, event ->
            // Consume touch event to prevent propagation to TraitEditor RecyclerView
            true
        }
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

                    setupClickListeners(state.trait)

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

    private fun setupClickListeners(trait: TraitObject) {
        binding.resourceChip.setOnClickListener {
            val dir = BaseDocumentTreeUtil.Companion.getDirectory(requireContext(), R.string.dir_resources)
            if (dir != null && dir.exists()) {
                val intent = Intent(requireContext(), FileExploreActivity::class.java)
                intent.putExtra("path", dir.uri.toString())
                intent.putExtra("title", requireContext().getString(R.string.main_toolbar_resources))

                // Use the same request code as defined in TraitEditorActivity
                (activity as? TraitEditorActivity)?.startActivityForResult(
                    intent,
                    TraitEditorActivity.REQUEST_RESOURCE_FILE_CODE
                )
            } else {
                Toast.makeText(requireContext(), R.string.error_storage_directory, Toast.LENGTH_LONG).show()
            }
        }

        binding.brapiSwapNameChip.setOnClickListener {
            showSwapNameDialog(trait)
        }

        binding.dateFormatChip.setOnClickListener {
            showDateFormatDialog(trait)
        }

        binding.brapiLabelValueChip.setOnClickListener {
            showBrapiLabelValueDialog()
        }
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

    fun updateResourceFile(fileUri: String) {
        traitId?.let { id ->
            viewModel.updateResourceFile(id, fileUri)
            val fileObject = FieldFileObject.create(requireContext(), fileUri.toUri(), null, null)
            binding.resourceChip.text = fileObject.fileStem
            Toast.makeText(requireContext(),
                getString(R.string.trait_resource_file_updated),
                Toast.LENGTH_SHORT).show()
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

        binding.resourceChip.text = if (trait.resourceFile.isNotEmpty()) {
            val fileObject = FieldFileObject.create(requireContext(), trait.resourceFile.toUri(), null, null)
            fileObject.fileStem
        } else {
            getString(R.string.trait_resource_chip_title) // default text
        }

        if (trait.format == "date") {
            binding.dateFormatChip.visibility = View.VISIBLE
            val isUseDayOfYear = trait.useDayOfYear
            updateDateFormatChip(isUseDayOfYear)
        } else {
            binding.dateFormatChip.visibility = View.GONE
        }

        // Only show the BrAPI label chip for BrAPI traits
        val isBrapiTrait =
            trait.externalDbId != null && (trait.externalDbId?.isNotEmpty() == true)
                    || trait.traitDataSource.contains("brapi", ignoreCase = true) == true

        val hasSynonyms = trait.synonyms.isNotEmpty()

        binding.brapiSwapNameChip.visibility = if (isBrapiTrait && hasSynonyms) View.VISIBLE else View.GONE
        binding.brapiSwapNameChip.text = getString(R.string.trait_brapi_swap_name)

        // Show/hide BrAPI label/value chip based on trait source and format
        // A trait is from BrAPI if it has an external ID or the data source contains "brapi"
        
        traitHasBrapiCategories = isBrapiTrait && 
                                (trait.format == "categorical" || trait.format == "multicat") &&
                                trait.categories.isNotEmpty()
        
        // For BrAPI label/value toggle
        if (traitHasBrapiCategories) {
            binding.brapiLabelValueChip.visibility = View.VISIBLE
            // Check if there's a trait-specific preference, otherwise use the global one
            val traitSpecificKey = "LABELVAL_CUSTOMIZE_${trait.id}"
            val defaultValue = preferences.getString(PreferenceKeys.LABELVAL_CUSTOMIZE, "label")
            val useValues = preferences.getString(traitSpecificKey, defaultValue) == "value"
            
            updateBrapiLabelValueChip(useValues)
        } else {
            binding.brapiLabelValueChip.visibility = View.GONE
        }
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
                    val errorRes = validateTraitAlias(newAlias, database, trait)
                    if (errorRes != null) {
                        Utils.makeToast(context, getString(errorRes))
                        return@setPositiveButton
                    }
                    viewModel.updateTraitAlias(trait, newAlias)
                }
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }

    private fun showDateFormatDialog(trait: TraitObject) {
        val calendar = Calendar.getInstance()
        
        // Format as standard date (YYYY-MM-DD)
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1 // Calendar months are 0-based
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val formattedDate = String.format("%04d-%02d-%02d", year, month, day)
        
        // Format as day of year
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
        
        val options = arrayOf(
            getString(R.string.trait_date_format_display, formattedDate),
            getString(R.string.trait_day_format_display, dayOfYear)
        )
        
        val currentSelection = if (trait.useDayOfYear) 1 else 0
        
        AlertDialog.Builder(requireContext(), R.style.AppAlertDialog)
            .setTitle(getString(R.string.trait_date_format_dialog_title))
            .setSingleChoiceItems(options, currentSelection) { dialog, which ->
                val useDayOfYear = which == 1
                viewModel.updateTraitOptions(database.valueFormatter, trait.also {
                    it.useDayOfYear = useDayOfYear
                })
                // Update the chip text
                updateDateFormatChip(useDayOfYear)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    // Update the showBrapiLabelValueDialog method
    private fun showBrapiLabelValueDialog() {
        if (!traitHasBrapiCategories) return

        // Get the first category example
        val categories = traitObject?.categories ?: return
        val firstCategory = parseCategoryExample(categories)
        
        val options = arrayOf(
            getString(R.string.trait_brapi_label_display, firstCategory.first),
            getString(R.string.trait_brapi_value_display, firstCategory.second)
        )
        
        // Get trait-specific preference or fall back to global preference
        val traitSpecificKey = "LABELVAL_CUSTOMIZE_${traitId}"
        val defaultValue = preferences.getString(PreferenceKeys.LABELVAL_CUSTOMIZE, "label")
        val currentSelection = if (preferences.getString(traitSpecificKey, defaultValue) == "value") 1 else 0
        
        AlertDialog.Builder(requireContext(), R.style.AppAlertDialog)
            .setTitle(getString(R.string.trait_brapi_display_dialog_title))
            .setSingleChoiceItems(options, currentSelection) { dialog, which ->
                // Save the preference
                val value = if (which == 1) "value" else "label"
                preferences.edit { putString(traitSpecificKey, value) }
                // Update the chip text
                updateBrapiLabelValueChip(which == 1)
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
                val parsedCategories = CategoryJsonUtil.decode(categories)
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

    private fun updateDateFormatChip(useDayOfYear: Boolean) {
        val calendar = Calendar.getInstance()
        
        // Format as standard date (YYYY-MM-DD)
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1 // Calendar months are 0-based
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val formattedDate = String.format("%04d-%02d-%02d", year, month, day)
        
        // Format as day of year
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
        
        // Set the chip text based on the selected format
        if (useDayOfYear) {
            binding.dateFormatChip.text = getString(R.string.trait_day_format_display, dayOfYear)
        } else {
            binding.dateFormatChip.text = getString(R.string.trait_date_format_display, formattedDate)
        }
    }

    // Update the updateBrapiLabelValueChip method
    private fun updateBrapiLabelValueChip(useValues: Boolean) {
        if (!traitHasBrapiCategories) return

        // Get the first category from the trait to use as an example
        val categories = traitObject?.categories ?: return
        val firstCategory = parseCategoryExample(categories)

        // Set the chip text based on the selected format
        if (useValues) {
            binding.brapiLabelValueChip.text = getString(R.string.trait_brapi_value_display, firstCategory.second)
        } else {
            binding.brapiLabelValueChip.text = getString(R.string.trait_brapi_label_display, firstCategory.first)
        }
    }

    private fun updateVisibilityChip(trait: TraitObject) {
        binding.visibilityChip.text = if (trait.visible) getString(R.string.trait_visible) else getString(R.string.trait_hidden)

        binding.visibilityChip.chipIcon = ContextCompat.getDrawable(requireContext(),
            if (trait.visible) R.drawable.ic_eye else R.drawable.ic_eye_off)
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
            if (trait.format == "categorical" || trait.format == "multicat" || trait.format == "boolean") {
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
                val parsedCategories = CategoryJsonUtil.decode(categories)
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