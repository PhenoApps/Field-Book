package com.fieldbook.tracker.traits.formats.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.ScrollView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.repository.TraitRepository
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.formats.Formats
import com.fieldbook.tracker.traits.formats.ValidationResult
import com.fieldbook.tracker.traits.formats.parameters.BaseFormatParameter

/**
 * Simple helper scroll view extension that uses a visitor pattern to iterate over view holder children.
 * Used in trait format -> trait object merging, and UI validation
 */
class ParameterScrollView constructor(
    context: Context,
    attr: AttributeSet?,
    defStyleAttr: Int
) :

    ScrollView(context, attr, defStyleAttr) {

    private val holders = arrayListOf<BaseFormatParameter.ViewHolder>()

    companion object {
        val TAG = ParameterScrollView::class.simpleName
    }

    constructor(context: Context, attr: AttributeSet?) : this(
        context,
        attr,
        androidx.core.R.attr.nestedScrollViewStyle
    )

    constructor(context: Context) : this(context, null)

    fun clear() {

        holders.clear()

        getViewGroup().removeAllViews()

    }

    fun addViewHolder(holder: BaseFormatParameter.ViewHolder) {

        holders.add(holder)

        getViewGroup().addView(holder.itemView)

    }

    private fun getViewGroup(): LinearLayout = findViewById(R.id.dialog_new_trait_parameters_ll)

    fun merge(traitObject: TraitObject): TraitObject {

        var t = traitObject

        holders.forEach {

            t = it.merge(t)

        }

        return t
    }

    fun validateFormat(format: Formats): ValidationResult {

        val traitFormat = format.getTraitFormatDefinition()

        return traitFormat.validate(context, holders)
    }

    fun validateParameters(
        traitRepo: TraitRepository,
        initialTraitObject: TraitObject? = null
    ): ValidationResult {

        val validated = ArrayList<ValidationResult>()

        holders.forEach {

            validated.add(it.validate(traitRepo, initialTraitObject))

        }

        val defaultResult = ValidationResult()
        return if (validated.isEmpty()) defaultResult
        else if (validated.all { it.result == true }) validated.first()
        else validated.find { it.result == false } ?: defaultResult
    }
}