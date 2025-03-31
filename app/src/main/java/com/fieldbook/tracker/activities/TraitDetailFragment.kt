package com.fieldbook.tracker.activities

import android.app.AlertDialog
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.adapters.TraitDetailAdapter
import com.fieldbook.tracker.adapters.TraitDetailItem
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.traits.formats.Formats
import com.fieldbook.tracker.utilities.SemanticDateUtil
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class TraitDetailFragment : Fragment() {

    @Inject
    lateinit var database: DataHelper

    @Inject
    lateinit var preferences: SharedPreferences

    private var toolbar: Toolbar? = null
    private var traitId: String? = null
    private var traitObject: TraitObject? = null
    private val TAG = "TraitDetailFragment"

    private lateinit var rootView: View
    private lateinit var traitNameTextView: TextView
    private lateinit var importDateTextView: TextView
    private lateinit var formatChip: Chip
    private lateinit var editNameChip: Chip
    private lateinit var visibilityChip: Chip
    private lateinit var detailRecyclerView: RecyclerView
    private var adapter: TraitDetailAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        Log.d(TAG, "onCreateView Start")
        rootView = inflater.inflate(R.layout.fragment_trait_detail, container, false)
        
        toolbar = rootView.findViewById(R.id.toolbar)
        traitNameTextView = rootView.findViewById(R.id.traitDisplayName)
        importDateTextView = rootView.findViewById(R.id.importDateTextView)
        formatChip = rootView.findViewById(R.id.formatChip)
        editNameChip = rootView.findViewById(R.id.editNameChip)
        visibilityChip = rootView.findViewById(R.id.visibilityChip)
        detailRecyclerView = rootView.findViewById(R.id.traitDetailRecyclerView)

        traitId = arguments?.getString("traitId")
        loadTraitDetails()

        // Set up collapsible sections
        setupCollapsibleSection(
            R.id.overview_collapsible_header,
            R.id.overview_collapsible_content,
            R.id.overview_expand_collapse_icon,
            GeneralKeys.TRAIT_DETAIL_OVERVIEW_COLLAPSED
        )
        
        setupCollapsibleSection(
            R.id.data_collapsible_header,
            R.id.data_collapsible_content,
            R.id.data_expand_collapse_icon,
            GeneralKeys.TRAIT_DETAIL_DATA_COLLAPSED
        )

        // Set up edit name chip click
        editNameChip.setOnClickListener {
            traitObject?.let { trait ->
                (activity as? TraitEditorActivity)?.showTraitDialog(trait)
            }
        }
        
        // Set up format chip click
        formatChip.setOnClickListener {
            traitObject?.let { trait ->
                (activity as? TraitEditorActivity)?.showTraitDialog(trait)
            }
        }
        
        // Set up visibility chip click
        visibilityChip.setOnClickListener {
            traitObject?.let { trait ->
                val newVisibility = !trait.visible
                database.updateTraitVisibility(trait.id, newVisibility)
                trait.visible = newVisibility
                updateVisibilityChip(trait)
            }
        }

        Log.d(TAG, "onCreateView End")
        return rootView
    }

    private fun setupCollapsibleSection(headerId: Int, contentId: Int, iconId: Int, prefKey: String) {
        val header = rootView.findViewById<LinearLayout>(headerId)
        val content = rootView.findViewById<LinearLayout>(contentId)
        val icon = rootView.findViewById<ImageView>(iconId)
        
        val isCollapsed = preferences.getBoolean(prefKey, false)
        content.visibility = if (isCollapsed) View.GONE else View.VISIBLE
        icon.setImageResource(if (isCollapsed) R.drawable.ic_chevron_down else R.drawable.ic_chevron_up)
        
        header.setOnClickListener {
            if (content.visibility == View.GONE) {
                content.visibility = View.VISIBLE
                icon.setImageResource(R.drawable.ic_chevron_up)
                preferences.edit().putBoolean(prefKey, false).apply()
            } else {
                content.visibility = View.GONE
                icon.setImageResource(R.drawable.ic_chevron_down)
                preferences.edit().putBoolean(prefKey, true).apply()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rootView.setOnTouchListener { v, event ->
            // Consume touch event to prevent propagation to TraitEditor RecyclerView
            true
        }
    }

    override fun onResume() {
        super.onResume()
        loadTraitDetails()
    }

    fun loadTraitDetails() {
        traitId?.let { idString ->
            try {
                // Convert the string ID to an integer
                val idInt = idString.toInt()
                CoroutineScope(Dispatchers.IO).launch {
                    val trait = database.getTraitById(idInt)
                    
                    withContext(Dispatchers.Main) {
                        traitObject = trait  // Store the trait object
                        
                        if (trait != null) {
                            updateTraitData(trait)
                            setupToolbar(trait)
                            
                            if (detailRecyclerView.adapter == null) { // initial load
                                detailRecyclerView.layoutManager = LinearLayoutManager(context)
                                val detailItems = createTraitDetailItems(trait)
                                adapter = TraitDetailAdapter(detailItems.toMutableList())
                                detailRecyclerView.adapter = adapter
                            } else { // reload after data change
                                val newItems = createTraitDetailItems(trait)
                                adapter?.updateItems(newItems)
                            }
                        }
                    }
                }
            } catch (e: NumberFormatException) {
                // Handle the case where the ID string cannot be converted to an integer
                Log.e(TAG, "Invalid trait ID format: $idString", e)
                parentFragmentManager.popBackStack()
            }
        } ?: Log.e(TAG, "Trait ID is null")
    }

    private fun updateTraitData(trait: TraitObject) {
        // Basic trait info
        traitNameTextView.text = trait.name
        
        // Import date
        // val importDate = trait.creation_date
        val importDate = ""
        if (!importDate.isNullOrEmpty()) {
            importDateTextView.text = SemanticDateUtil.getSemanticDate(requireContext(), importDate)
        } else {
            importDateTextView.text = getString(R.string.unknown_date)
        }
        
        // Format chip
        val format = trait.format
        formatChip.text = format
        
        // Set format icon
        val formatEnum = Formats.entries.find { it.getDatabaseName() == format }
        formatChip.chipIcon = ContextCompat.getDrawable(requireContext(), 
            formatEnum?.getIcon() ?: R.drawable.ic_trait_categorical)
        
        // Update visibility chip
        updateVisibilityChip(trait)
    }
    
    private fun updateVisibilityChip(trait: TraitObject) {
        visibilityChip.text = if (trait.visible) {
            getString(R.string.trait_visible)
        } else {
            getString(R.string.trait_hidden)
        }
        
        visibilityChip.chipIcon = ContextCompat.getDrawable(requireContext(),
            if (trait.visible) R.drawable.ic_eye else R.drawable.ic_eye_off)
    }

    private fun createTraitDetailItems(trait: TraitObject): List<TraitDetailItem> {
        val items = mutableListOf<TraitDetailItem>()
        
        // Add format details item
        val formatIcon = Formats.entries
            .find { it.getDatabaseName() == trait.format }?.getIcon()
            ?: R.drawable.ic_trait_categorical
            
        items.add(TraitDetailItem(
            id = trait.id,
            title = getString(R.string.trait_format_details),
            subtitle = trait.format,
            format = trait.format,
            categories = trait.categories ?: "",
            icon = ContextCompat.getDrawable(requireContext(), formatIcon),
            defaultValue = trait.defaultValue,
            minimum = trait.minimum,
            maximum = trait.maximum,
            details = trait.details
        ))
        
        // Get observation data for this trait
        CoroutineScope(Dispatchers.IO).launch {
            val observations = database.getAllObservationsOfVariable(trait.id)
            
            withContext(Dispatchers.Main) {
                if (observations.isNotEmpty()) {
                    val observationValues = observations.map { it.value }
                    // val completeness = observations.size.toFloat() / 
                        // (database.getTotalPossibleObservationsForTrait(trait.name) ?: 1).toFloat()
                    val totalPossible = 100 // Placeholder value
                    val completeness = if (observations.isNotEmpty()) 
                        observations.size.toFloat() / totalPossible.toFloat() 
                    else 0f

                    val dataItem = TraitDetailItem(
                        id = trait.id,
                        title = getString(R.string.trait_observation_data),
                        subtitle = getString(R.string.trait_observation_count, observations.size),
                        format = trait.format,
                        categories = trait.categories ?: "",
                        icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_chart_bar),
                        observations = observationValues,
                        completeness = completeness
                    )
                    
                    adapter?.addOrUpdateItem(dataItem)
                }
            }
        }
        
        return items
    }

    private fun setupToolbar(trait: TraitObject) {
        toolbar?.inflateMenu(R.menu.menu_trait_details)
        toolbar?.setTitle(R.string.trait_detail_title)
        toolbar?.setNavigationIcon(R.drawable.arrow_left)
        toolbar?.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
        
        toolbar?.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                android.R.id.home -> {
                    parentFragmentManager.popBackStack()
                }
                R.id.delete -> {
                    // Show confirmation dialog
                    AlertDialog.Builder(requireContext(), R.style.AppAlertDialog)
                        .setTitle(getString(R.string.traits_options_delete_title))
                        .setMessage(getString(R.string.traits_warning_delete))
                        .setPositiveButton(getString(R.string.dialog_yes)) { dialog, _ ->
                            // Call the existing deleteTrait method
                            (activity as? TraitEditorActivity)?.deleteTrait(trait)
                            // Pop back stack to return to the trait list
                            parentFragmentManager.popBackStack()
                            dialog.dismiss()
                        }
                        .setNegativeButton(getString(R.string.dialog_no)) { dialog, _ ->
                            dialog.dismiss()
                        }
                        .show()
                    true
                }
            }

            true
        }  
    }
    
}