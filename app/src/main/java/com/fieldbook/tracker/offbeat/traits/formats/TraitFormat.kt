package com.fieldbook.tracker.offbeat.traits.formats

import android.content.Context
import android.view.View
import com.fieldbook.tracker.offbeat.traits.formats.parameters.BaseFormatParameter

abstract class TraitFormat {

    /**
     * Link to the defined enum which helps get defined resources
     */
    abstract val format: Formats

    /**
     * The source defined default layout id, which can be inflated to collect observations
     */
    abstract var defaultLayoutId: Int

    /**
     * Optional View that can be defined
     */
    open lateinit var layoutView: View

    /**
     * Each format must define a list of parameter views that will be used to define the trait.
     * Parameter views will be used during validation, and to create or load Trait Objects.
     * Trait Objects will have links to attributes/values based on these items.
     */
    abstract val parameters: List<BaseFormatParameter>

    /**
     * Displayable name resource id
     */
    abstract fun getName(): Int

    /**
     * Get resource id for the format.
     */
    abstract fun getIcon(): Int

    open fun validate(context: Context, parameters: List<BaseFormatParameter.ViewHolder>) =
        ValidationResult()

}