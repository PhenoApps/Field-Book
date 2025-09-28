package com.fieldbook.tracker.traits.formats.parameters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ToggleButton
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.formats.ValidationResult
import java.util.UUID
import javax.inject.Inject

class UseDayOfYearParameter @Inject constructor(
    uniqueId: String = UUID.randomUUID().toString(),
    override val nameStringResourceId: Int = R.string.trait_parameter_use_day_of_year,
    override val defaultLayoutId: Int = R.layout.list_item_trait_parameter_default_toggle_value,
    override val parameter: Parameters = Parameters.USE_DAY_OF_YEAR 
) : BaseFormatParameter(
    uniqueId,
    nameStringResourceId,
    defaultLayoutId,
    parameter
) {
    val attributeName = "useDayOfYear"
    val defaultValue = false

    override fun createViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(defaultLayoutId, parent, false)
        return UseDayOfYearViewHolder(view)
    }

    inner class UseDayOfYearViewHolder(itemView: View) : ViewHolder(itemView) {
        // Use ToggleButton instead of Switch and the correct ID
        private val toggleButton: ToggleButton = itemView.findViewById(R.id.dialog_new_trait_default_toggle_btn)
        
        init {
            // Set up listener for toggle changes
            toggleButton.setOnCheckedChangeListener { _, _ ->
                // No additional action needed here, value will be read in merge()
            }
        }

        override fun merge(traitObject: TraitObject): TraitObject {
            traitObject.useDayOfYear = toggleButton.isChecked
            return traitObject
        }

        override fun load(traitObject: TraitObject?): Boolean {
            initialTraitObject = traitObject
            toggleButton.isChecked = traitObject?.useDayOfYear ?: defaultValue
            return true
        }

        override fun validate(database: DataHelper, initialTraitObject: TraitObject?): ValidationResult {
            // Boolean toggle doesn't need validation
            return ValidationResult(true)
        }
    }
}