package com.fieldbook.tracker.traits.formats

import com.fieldbook.tracker.R
import com.fieldbook.tracker.traits.formats.coders.CategoricalJsonCoder
import com.fieldbook.tracker.traits.formats.coders.StringCoder
import com.fieldbook.tracker.traits.formats.parameters.CategoriesParameter
import com.fieldbook.tracker.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.traits.formats.parameters.NameParameter
import com.fieldbook.tracker.traits.formats.presenters.CategoricalValuePresenter
import com.fieldbook.tracker.traits.formats.presenters.ValuePresenter

/**
 * TODO add multicategorical checkbox UI catch-all "CategoricalOptionsParameter"
 */
class CategoricalFormat : TraitFormat(
    format = Formats.CATEGORICAL,
    defaultLayoutId = R.layout.trait_categorical,
    layoutView = null,
    databaseName = "categorical",
    nameStringResourceId = R.string.traits_format_categorical,
    iconDrawableResourceId = R.drawable.ic_trait_categorical,
    stringNameAux = null,
    NameParameter(),
    DetailsParameter(),
    CategoriesParameter()
),
    StringCoder by CategoricalJsonCoder(),
    ValuePresenter by CategoricalValuePresenter(),
    Scannable