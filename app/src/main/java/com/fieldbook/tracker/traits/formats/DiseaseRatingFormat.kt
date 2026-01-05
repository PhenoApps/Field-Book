package com.fieldbook.tracker.traits.formats

import com.fieldbook.tracker.R
import com.fieldbook.tracker.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.traits.formats.parameters.AttachMediaParameter
import com.fieldbook.tracker.traits.formats.parameters.NameParameter
import com.fieldbook.tracker.traits.formats.parameters.RepeatedMeasureParameter
import com.fieldbook.tracker.traits.formats.parameters.ResourceFileParameter

class DiseaseRatingFormat : TraitFormat(
    format = Formats.DISEASE_RATING,
    defaultLayoutId = R.layout.trait_disease_rating,
    layoutView = null,
    databaseName = "disease rating",
    nameStringResourceId = R.string.traits_format_disease_rating,
    iconDrawableResourceId = R.drawable.ic_trait_disease_rating,
    stringNameAux = null,
    NameParameter(),
    DetailsParameter(),
    RepeatedMeasureParameter(),
    ResourceFileParameter(),
    AttachMediaParameter()
), Scannable