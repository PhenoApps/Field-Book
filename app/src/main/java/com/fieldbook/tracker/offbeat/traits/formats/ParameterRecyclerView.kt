package com.fieldbook.tracker.offbeat.traits.formats

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.offbeat.traits.formats.parameters.BaseFormatParameter

/**
 * Simple helper recycler view extension that uses a visitor pattern to iterate over view holder children.
 * Used in trait format -> trait object merging, and UI validation
 */
class ParameterRecyclerView constructor(context: Context, attr: AttributeSet?, defStyleAttr: Int) :
    RecyclerView(context, attr, defStyleAttr) {

    constructor(context: Context, attr: AttributeSet?) : this(
        context,
        attr,
        R.attr.recyclerViewStyle
    )

    constructor(context: Context) : this(context, null)

    private fun getParameterViewHolders(): List<BaseFormatParameter.ViewHolder> {

        return (adapter as TraitFormatParametersAdapter).holders
        //return children.map { getChildViewHolder(it) as BaseFormatParameter.ViewHolder }.toList()

    }


    private inline fun mapViewHolders(crossinline function: (BaseFormatParameter.ViewHolder) -> Unit) =
        getParameterViewHolders().forEach(function)

    fun merge(traitObject: TraitObject): TraitObject {

        var t = traitObject

        mapViewHolders { holder ->

            t = holder.merge(t)

        }

        return t
    }

    fun validateFormat(format: Formats): ValidationResult {

        val traitFormat = format.getTraitFormatDefinition()

        return traitFormat.validate(context, getParameterViewHolders())
    }

    fun validateParameters(
        database: DataHelper,
        initialTraitObject: TraitObject? = null
    ): ValidationResult {

        val validated = ArrayList<ValidationResult>()

        mapViewHolders { holder ->

            validated.add(holder.validate(database, initialTraitObject))

        }

        val defaultResult = ValidationResult()
        return if (validated.isEmpty()) defaultResult
        else if (validated.all { it.result == true }) validated.first()
        else validated.find { it.result == false } ?: defaultResult
    }
}