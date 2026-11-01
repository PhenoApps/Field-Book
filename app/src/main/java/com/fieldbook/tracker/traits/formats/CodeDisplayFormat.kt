package com.fieldbook.tracker.traits.formats

import com.fieldbook.tracker.R
import com.fieldbook.tracker.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.traits.formats.parameters.NameParameter

/**
 * Displays a barcode or QR code for each entry, the code format and data are configured
 * from the collect screen rather than as trait parameters.
 */
class CodeDisplayFormat : TraitFormat(
    format = Formats.CODE_DISPLAY,
    defaultLayoutId = R.layout.trait_code_display,
    layoutView = null,
    databaseName = "code_display",
    nameStringResourceId = R.string.traits_format_code_display,
    iconDrawableResourceId = R.drawable.ic_trait_code_display,
    stringNameAux = null,
    NameParameter(),
    DetailsParameter()
)
