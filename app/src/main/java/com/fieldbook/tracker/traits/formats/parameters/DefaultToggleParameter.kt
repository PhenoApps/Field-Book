package com.fieldbook.tracker.traits.formats.parameters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ToggleButton
import com.fieldbook.tracker.database.repository.TraitRepository
import com.fieldbook.tracker.enums.traits.ToggleLayoutType
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.formats.ValidationResult

abstract class DefaultToggleParameter(
    nameStringResourceId: Int,
    parameter: Parameters,
    private val defaultValue: Boolean? = null,
    private val layoutType: ToggleLayoutType = ToggleLayoutType.ENABLED_DISABLED
) : BaseFormatParameter(
    nameStringResourceId = nameStringResourceId,
    defaultLayoutId = layoutType.layoutId,
    parameter = parameter
) {

    override fun createViewHolder(parent: ViewGroup): BaseFormatParameter.ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(defaultLayoutId, parent, false)
        return ViewHolder(view)
    }

    abstract fun getToggleValue(traitObject: TraitObject?): Boolean
    abstract fun setToggleValue(traitObject: TraitObject, value: Boolean)

    inner class ViewHolder(itemView: View) : BaseFormatParameter.ViewHolder(itemView) {

        internal val toggleButton: ToggleButton =
            itemView.findViewById<ToggleButton>(layoutType.toggleButtonId).also {
                defaultValue?.let { value ->
                    it.isChecked = value
                }
            }

        override fun merge(traitObject: TraitObject) = traitObject.apply {
            setToggleValue(this, toggleButton.isChecked)
        }

        override fun load(traitObject: TraitObject?): Boolean {
            try {
                toggleButton.isChecked = getToggleValue(traitObject)
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
            return true
        }

        override fun validate(
            traitRepo: TraitRepository,
            initialTraitObject: TraitObject?
        ) = ValidationResult()
    }

    /**
     * Toggle the parameter value for toggle based parameters.
     * @return The new boolean value if this parameter supports toggling, null otherwise
     */
     fun toggleValue(trait: TraitObject): Boolean {
        val newValue = !getToggleValue(trait)
        setToggleValue(trait, newValue)
        return newValue
    }
}