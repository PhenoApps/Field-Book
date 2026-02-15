package com.fieldbook.tracker.traits.formats

import com.fieldbook.tracker.R
import com.fieldbook.tracker.traits.formats.parameters.AutoSwitchPlotParameter
import com.fieldbook.tracker.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.traits.formats.parameters.AttachMediaParameter
import com.fieldbook.tracker.traits.formats.parameters.NameParameter
import com.fieldbook.tracker.traits.formats.parameters.ResourceFileParameter

class ZebraLabelPrintFormat : TraitFormat(
    format = Formats.LABEL_PRINT,
    defaultLayoutId = R.layout.trait_label_print,
    layoutView = null,
    databaseName = "zebra label print",
    nameStringResourceId = R.string.traits_format_labelprint,
    iconDrawableResourceId = R.drawable.ic_trait_labelprint,
    stringNameAux = null,
    NameParameter(),
    DetailsParameter(),
    AutoSwitchPlotParameter(),
    ResourceFileParameter(),
    AttachMediaParameter()
)