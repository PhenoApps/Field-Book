package com.fieldbook.tracker.traits.formats.parameters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ToggleButton
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.formats.ValidationResult

class CropImageParameter(private val initialDefaultValue: Boolean? = null) :
    BaseFormatParameter(
        nameStringResourceId = R.string.traits_crop_image_parameter,
        defaultLayoutId = R.layout.list_item_trait_parameter_default_toggle_value,
        parameter = Parameters.CROP_IMAGE
    ) {

    override fun createViewHolder(
        parent: ViewGroup,
    ): BaseFormatParameter.ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_trait_parameter_default_toggle_value, parent, false)
        return ViewHolder(v)
    }

    inner class ViewHolder(itemView: View) : BaseFormatParameter.ViewHolder(itemView) {

        val defaultValueToggle =
            itemView.findViewById<ToggleButton>(R.id.dialog_new_trait_default_toggle_btn).also {
                initialDefaultValue?.let { value ->
                    it.isChecked = value
                }
            }

        override fun merge(traitObject: TraitObject) = traitObject.apply {
            cropImage = defaultValueToggle.isChecked
        }

        override fun load(traitObject: TraitObject?): Boolean {
            try {
                defaultValueToggle.isChecked = traitObject?.cropImage == true
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
            return true
        }

        override fun validate(
            database: DataHelper,
            initialTraitObject: TraitObject?
        ) = ValidationResult()
    }

    override fun toggleValue(trait: TraitObject): Boolean {
        trait.cropImage = !trait.cropImage
        return trait.cropImage
    }
}