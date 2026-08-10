package com.fieldbook.tracker.traits.formats

import com.fieldbook.tracker.R
import com.fieldbook.tracker.traits.formats.feature.DisplayValue
import com.fieldbook.tracker.traits.formats.parameters.AttachMediaParameter
import com.fieldbook.tracker.traits.formats.parameters.CategoriesParameter
import com.fieldbook.tracker.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.traits.formats.parameters.DurationParameter
import com.fieldbook.tracker.traits.formats.parameters.NameParameter
import com.fieldbook.tracker.traits.formats.parameters.RepeatedMeasureParameter
import com.fieldbook.tracker.traits.formats.presenters.PollinatorValuePresenter
import com.fieldbook.tracker.traits.formats.presenters.ValuePresenter

class PollinatorFormat : TraitFormat(
    format = Formats.POLLINATOR,
    defaultLayoutId = R.layout.trait_pollinator,
    layoutView = null,
    databaseName = "pollinator",
    nameStringResourceId = R.string.traits_format_pollinator,
    iconDrawableResourceId = R.drawable.ic_trait_pollinator,
    stringNameAux = null,
    NameParameter(),
    DetailsParameter(),
    //counts are recorded per category, the trait cannot collect without at least one
    CategoriesParameter(isRequired = true),
    DurationParameter(),
    RepeatedMeasureParameter(),
    AttachMediaParameter()
), ValuePresenter by PollinatorValuePresenter(), DisplayValue
