package com.fieldbook.tracker.offbeat.traits.formats.contracts

import com.fieldbook.tracker.R
import com.fieldbook.tracker.offbeat.traits.formats.Formats
import com.fieldbook.tracker.offbeat.traits.formats.TraitFormat
import com.fieldbook.tracker.offbeat.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.offbeat.traits.formats.parameters.NameParameter

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
)