package com.fieldbook.tracker.traits.formats

import com.fieldbook.tracker.R
import com.fieldbook.tracker.traits.formats.coders.CategoricalJsonCoder
import com.fieldbook.tracker.traits.formats.coders.StringCoder
import com.fieldbook.tracker.traits.formats.parameters.CategoriesParameter
import com.fieldbook.tracker.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.traits.formats.parameters.NameParameter
import com.fieldbook.tracker.traits.formats.presenters.CategoricalValuePresenter
import com.fieldbook.tracker.traits.formats.presenters.ValuePresenter

class MultiCategoricalFormat : TraitFormat(
    format = Formats.MULTI_CATEGORICAL,
    defaultLayoutId = R.layout.trait_multicat,
    layoutView = null,
    databaseName = "multicat",
    nameStringResourceId = R.string.traits_format_multicategorical,
    iconDrawableResourceId = R.drawable.ic_trait_multicat,
    stringNameAux = null,
    NameParameter(),
    DetailsParameter(),
    CategoriesParameter()
), StringCoder by CategoricalJsonCoder(), ValuePresenter by CategoricalValuePresenter(), Scannable