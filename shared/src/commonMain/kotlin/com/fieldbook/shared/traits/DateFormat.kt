package com.fieldbook.shared.traits

import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.ic_trait_date
import com.fieldbook.shared.generated.resources.traits_format_date

class DateFormat : TraitFormat(
    format = Formats.DATE,
    nameStringResource = Res.string.traits_format_date,
    iconDrawableResource = Res.drawable.ic_trait_date,
), Scannable
