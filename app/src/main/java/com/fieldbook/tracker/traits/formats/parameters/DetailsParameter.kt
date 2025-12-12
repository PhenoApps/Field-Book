package com.fieldbook.tracker.traits.formats.parameters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.widget.addTextChangedListener
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.repository.TraitRepository
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.formats.ValidationResult

class DetailsParameter : BaseFormatParameter(
    nameStringResourceId = R.string.traits_create_details,
    defaultLayoutId = R.layout.list_item_trait_parameter_details,
    parameter = Parameters.DETAILS
) {

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

        init {

            detailsEt.addTextChangedListener { editable ->

                if (editable.toString().isNotBlank()) {

                    textInputLayout.setEndIconDrawable(android.R.drawable.ic_notification_clear_all)

                    textInputLayout.setEndIconOnClickListener {

                        detailsEt.text?.clear()

                        textInputLayout.endIconDrawable = null
                    }

                } else {

                    textInputLayout.endIconDrawable = null

                }
            }
        }

        override fun merge(traitObject: TraitObject) = traitObject.apply {
            details = detailsEt.text.toString()
        }

        override fun load(traitObject: TraitObject?): Boolean {
            try {
                traitObject?.details?.let { details ->
                    detailsEt.setText(details)
                }
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
}