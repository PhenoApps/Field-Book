package com.fieldbook.shared.traits

import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

/**
 *
 * The trait format is a base-level inheritance class that all trait formats should inherit from.
 *
 * @param format                    Link to the defined enum which helps get defined resources
 *                                      which can be inflated to collect observations
 * @param nameStringResource      Displayable name resource id
 * @param iconDrawableResource    Get resource id for the format.
 *
 */
open class TraitFormat(
    open val format: Formats,
    open var nameStringResource: StringResource,
    open var iconDrawableResource: DrawableResource,
) {
}
