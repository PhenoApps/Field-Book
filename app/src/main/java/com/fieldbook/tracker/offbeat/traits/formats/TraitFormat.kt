package com.fieldbook.tracker.offbeat.traits.formats

import android.content.Context
import android.view.View
import com.fieldbook.tracker.offbeat.traits.formats.parameters.BaseFormatParameter
import javax.inject.Inject

/**
 *
 * The trait format is a base-level inheritance class that all trait formats should inherit from.
 * Trait Formats define the UI-layout that FieldBook will use to observe values on entries.
 * When creating a new observation variable, trait format layouts are chosen, and each format
 * layout contains a list of parameters such as minimum, maximum. The 'parameters' parameter of this
 * class defines these pieces of UI using Hilt DI, and are later dynamically inflated into a specialized
 * scroll view ParameterScrollView which can map to and from TraitObject, which is central to FieldBook.
 *
 * Furthermore, each Trait Format defines a format-level validation function, which can optionally use the
 * database and its parameter components to validate data entry from the UI.
 *
 * @param format                    Link to the defined enum which helps get defined resources
 * @param defaultLayoutId           The source defined default layout id,
 *                                      which can be inflated to collect observations
 * @param layoutView                Optional View that can be defined (unused)
 * @param nameStringResourceId      Displayable name resource id
 * @param iconDrawableResourceId    Get resource id for the format.
 * @param stringNameAux             Auxiliary function for generating a name using Context instead of
 *                                      using String resources via nameStringResourceId
 * @param parameters                List of parameters displayed during trait creation
 *
 */
open class TraitFormat @Inject constructor(
    open val format: Formats,
    open var defaultLayoutId: Int,
    open var layoutView: View? = null,
    open var nameStringResourceId: Int,
    open var iconDrawableResourceId: Int,
    open var stringNameAux: ((Context) -> String?)? = null,
    open vararg val parameters: BaseFormatParameter,
) {
    open fun validate(
        context: Context,
        parameterViewHolders: List<BaseFormatParameter.ViewHolder>
    ) = ValidationResult()
}