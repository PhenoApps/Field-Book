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

class DefaultValueParameter(
    override val nameStringResourceId: Int = R.string.traits_create_value_default,
    override val defaultLayoutId: Int = R.layout.list_item_trait_parameter_default_value,
    override val parameter: Parameters = Parameters.DEFAULT_VALUE,
    private val initialDefaultValue: String? = null
) : BaseFormatParameter(
    nameStringResourceId = nameStringResourceId,
    defaultLayoutId = defaultLayoutId,
    parameter = parameter
) {
    override fun createViewHolder(
        parent: ViewGroup,
    ): BaseFormatParameter.ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_trait_parameter_default_value, parent, false)
        return ViewHolder(v)
    }

    inner class ViewHolder(itemView: View) : BaseFormatParameter.ViewHolder(itemView) {

        val defaultValueEt: EditText =
            itemView.findViewById<EditText>(R.id.list_item_trait_parameter_default_value_et).also {
                initialDefaultValue?.let { value ->
                    it.setText(value)
                }
            }

        init {

            initialize()

        }

        private fun setDefaultValue() {

            initialDefaultValue?.let { value ->

                defaultValueEt.setText(value)

            }
        }

        private fun setupTextWatchers() {

            defaultValueEt.addTextChangedListener { editable ->

                if (editable.toString().isNotBlank()) {

                    textInputLayout.setEndIconDrawable(android.R.drawable.ic_notification_clear_all)

                    textInputLayout.setEndIconOnClickListener {

                        defaultValueEt.text?.clear()

                        textInputLayout.endIconDrawable = null
                    }

                } else {

                    textInputLayout.endIconDrawable = null

                }
            }
        }

        private fun setHint() {

            textInputLayout.hint = itemView.context.getString(R.string.traits_create_optional)

        }

        fun initialize() {

            setDefaultValue()

            setupTextWatchers()

            setHint()
        }

        override fun merge(traitObject: TraitObject) = traitObject.apply {
            defaultValue = defaultValueEt.text.toString()
        }

        override fun load(traitObject: TraitObject?): Boolean {
            try {
                traitObject?.defaultValue?.let { defaultValue ->
                    defaultValueEt.setText(defaultValue)
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