package com.fieldbook.tracker.traits.formats.parameters

import com.fieldbook.tracker.R
import com.fieldbook.tracker.objects.TraitObject

class AutoSwitchPlotParameter : DefaultToggleParameter(
    nameStringResourceId = R.string.trait_parameter_auto_switch_plot,
    parameter = Parameters.AUTO_SWITCH_PLOT
) {
    override fun getToggleValue(traitObject: TraitObject?): Boolean =
        traitObject?.autoSwitchPlot == true

    override fun setToggleValue(traitObject: TraitObject, value: Boolean) {
        traitObject.autoSwitchPlot = value
    }
}