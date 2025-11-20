package com.fieldbook.tracker.traits.formats.parameters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.widget.addTextChangedListener
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.formats.ValidationResult

class UnitParameter : BaseFormatParameter(
    nameStringResourceId = R.string.traits_create_unit,
    defaultLayoutId = R.layout.list_item_trait_parameter_unit,
    parameter = Parameters.UNIT
) {

    override fun createViewHolder(
        parent: ViewGroup,
    ): BaseFormatParameter.ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_trait_parameter_unit, parent, false)
        return ViewHolder(v)
    }

    inner class ViewHolder(itemView: View) : BaseFormatParameter.ViewHolder(itemView) {

        private val unitEt: EditText =
            itemView.findViewById(R.id.list_item_trait_parameter_unit_et)

        init {

            unitEt.addTextChangedListener { editable ->

                if (editable.toString().isNotBlank()) {

                    textInputLayout.setEndIconDrawable(android.R.drawable.ic_notification_clear_all)

                    textInputLayout.setEndIconOnClickListener {

                        unitEt.text?.clear()

                        textInputLayout.endIconDrawable = null
                    }

                } else {

                    textInputLayout.endIconDrawable = null

                }
            }
        }

        override fun merge(traitObject: TraitObject) = traitObject.apply {
            unit = unitEt.text.toString()
        }

        override fun load(traitObject: TraitObject?): Boolean {

            unitEt.setText(traitObject?.unit)

            return true

        }

        override fun validate(
            database: DataHelper,
            initialTraitObject: TraitObject?
        ) = ValidationResult()
    }
}