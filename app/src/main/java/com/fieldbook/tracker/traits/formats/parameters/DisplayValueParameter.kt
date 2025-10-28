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

class DisplayValueParameter @Inject constructor(
    uniqueId: String = UUID.randomUUID().toString(),
    override val nameStringResourceId: Int = R.string.trait_parameter_display_value,
    override val defaultLayoutId: Int = R.layout.list_item_trait_parameter_default_toggle_value,
    override val parameter: Parameters = Parameters.DISPLAY_VALUE
) : BaseFormatParameter(
    uniqueId,
    nameStringResourceId,
    defaultLayoutId,
    parameter
) {
    val defaultValue = false

    override fun createViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(defaultLayoutId, parent, false)
        return DisplayValueViewHolder(view)
    }

    inner class DisplayValueViewHolder(itemView: View) : ViewHolder(itemView) {
        // Use ToggleButton instead of Switch and the correct ID
        private val toggleButton: ToggleButton = itemView.findViewById(R.id.dialog_new_trait_default_toggle_btn)
        
        init {
            // Set up listener for toggle changes
            toggleButton.setOnCheckedChangeListener { _, _ ->
                // No additional action needed here, value will be read in merge()
            }
        }

        override fun merge(traitObject: TraitObject): TraitObject {
            traitObject.categoryDisplayValue = toggleButton.isChecked
            return traitObject
        }

        override fun load(traitObject: TraitObject?): Boolean {
            initialTraitObject = traitObject
            toggleButton.isChecked = traitObject?.categoryDisplayValue ?: defaultValue
            return true
        }

        override fun validate(
            database: DataHelper,
            initialTraitObject: TraitObject?
        ) = ValidationResult()
    }

    override fun toggleValue(trait: TraitObject): Boolean {
        trait.categoryDisplayValue = !trait.categoryDisplayValue
        return trait.categoryDisplayValue
    }
}