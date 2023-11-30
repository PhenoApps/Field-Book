package com.fieldbook.tracker.offbeat.traits.formats.parameters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.offbeat.traits.formats.ValidationResult

class DetailsParameter : BaseFormatParameter() {

    override val viewType = Parameters.DETAILS.ordinal
    override fun getName(ctx: Context) = ctx.getString(R.string.traits_create_details)

    override fun createViewHolder(
        parent: ViewGroup,
    ): BaseFormatParameter.ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_trait_parameter_details, parent, false)
        return ViewHolder(v)
    }

    class ViewHolder(itemView: View) : BaseFormatParameter.ViewHolder(itemView) {

        val detailsEt: EditText =
            itemView.findViewById(R.id.list_item_trait_parameter_details_et)

        override fun merge(traitObject: TraitObject) = traitObject.apply {
            details = detailsEt.text.toString()
        }

        override fun load(traitObject: TraitObject?): Boolean {
            try {
                detailsEt.setText(traitObject?.details ?: "")
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
}