package com.fieldbook.tracker.traits.formats.parameters

import com.fieldbook.tracker.R

class ResourceFileParameter : BaseFormatParameter<String> {
    override val attributeName = "resourceFile"
    override val defaultValue = ""
    override val nameResourceId = R.string.trait_parameter_resource_file

}