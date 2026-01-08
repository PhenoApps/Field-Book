package com.fieldbook.shared.traits

import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.ic_trait_disease_rating
import com.fieldbook.shared.generated.resources.traits_format_disease_rating

class DiseaseRatingFormat : TraitFormat(
    format = Formats.DISEASE_RATING,
    nameStringResource = Res.string.traits_format_disease_rating,
    iconDrawableResource = Res.drawable.ic_trait_disease_rating,
)

