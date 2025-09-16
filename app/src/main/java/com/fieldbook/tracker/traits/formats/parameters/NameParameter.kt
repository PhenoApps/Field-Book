package com.fieldbook.tracker.traits.formats.parameters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.formats.ValidationResult
import com.google.android.material.textfield.TextInputEditText

class NameParameter : BaseFormatParameter(
    nameStringResourceId = R.string.traits_create_name,
    parameter = Parameters.NAME,
    defaultLayoutId = R.layout.list_item_trait_parameter_name
) {
    override fun createViewHolder(
        parent: ViewGroup,
    ): BaseFormatParameter.ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_trait_parameter_name, parent, false)
        return ViewHolder(v)
    }

    class ViewHolder(itemView: View) : BaseFormatParameter.ViewHolder(itemView) {

        private val emptyOrNullNameError =
            itemView.context.getString(R.string.traits_create_warning_name_blank)
        private val duplicateNameError =
            itemView.context.getString(R.string.traits_create_warning_duplicate)

        val nameEt: TextInputEditText =
            itemView.findViewById(R.id.list_item_trait_parameter_name_et)

        init {

            nameEt.error = null

            nameEt.addTextChangedListener { text ->

                nameEt.error = if (text.isNullOrBlank()) {

                    textInputLayout.endIconDrawable = null

                    emptyOrNullNameError

                } else {

                    textInputLayout.setEndIconDrawable(android.R.drawable.ic_notification_clear_all)

                    textInputLayout.setEndIconOnClickListener {

                        nameEt.text?.clear()

                        textInputLayout.endIconDrawable = null
                    }

                    null
                }
            }
        }

        override fun merge(traitObject: TraitObject) = traitObject.apply {
            val inputText = nameEt.text.toString().trim { it <= ' ' }

            if (id.isEmpty()) { // new trait - assign to both name and alias
                name = inputText
                alias = inputText
            } else { // edit trait - assign only to alias
                alias = inputText
            }
        }

        override fun load(traitObject: TraitObject?): Boolean {
            try {
                traitObject?.alias?.let { alias ->
                    nameEt.setText(alias)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
            return true
        }

        override fun validate(database: DataHelper, initialTraitObject: TraitObject?) =
            ValidationResult().apply {

                textInputLayout.endIconDrawable = null

                val inputText = nameEt.text.toString().trim { it <= ' ' }

                var backendError: String? = null

                if (inputText.isBlank()) {

                    result = false

                    backendError = emptyOrNullNameError

                } else {

                    val traitByName = database.getTraitByName(inputText)

                    val traitByAlias = database.getTraitByAlias(inputText)

                    val exists = (traitByName != null) || (traitByAlias != null)

                    if (initialTraitObject == null) {

                        if (exists) {

                            result = false

                            backendError = duplicateNameError

                        }

                    } else {

                        if (exists) {
                            //check if trait was renamed
                            val conflictingTrait = traitByName ?: traitByAlias
                            if (conflictingTrait.id != initialTraitObject.id) {

                                result = false

                                backendError = duplicateNameError

                            }
                        }
                    }
                }

                error = backendError

                nameEt.error = backendError
            }
    }
}