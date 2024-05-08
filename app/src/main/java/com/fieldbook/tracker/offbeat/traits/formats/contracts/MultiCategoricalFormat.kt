package com.fieldbook.tracker.offbeat.traits.formats.contracts

import com.fieldbook.tracker.R
import com.fieldbook.tracker.offbeat.traits.formats.Formats
import com.fieldbook.tracker.offbeat.traits.formats.coders.StringCoder
import com.fieldbook.tracker.offbeat.traits.formats.TraitFormat
import com.fieldbook.tracker.offbeat.traits.formats.presenters.ValuePresenter
import com.fieldbook.tracker.offbeat.traits.formats.coders.CategoricalJsonCoder
import com.fieldbook.tracker.offbeat.traits.formats.parameters.CategoriesParameter
import com.fieldbook.tracker.offbeat.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.offbeat.traits.formats.parameters.NameParameter
import com.fieldbook.tracker.offbeat.traits.formats.presenters.CategoricalValuePresenter

class MultiCategoricalFormat : TraitFormat(
    format = Formats.MULTI_CATEGORICAL,
    defaultLayoutId = R.layout.trait_multicat,
    layoutView = null,
    nameStringResourceId = R.string.traits_format_multicategorical,
    iconDrawableResourceId = R.drawable.ic_trait_multicat,
    stringNameAux = null,
    NameParameter(),
    DetailsParameter(),
    CategoriesParameter()
), StringCoder by CategoricalJsonCoder(), ValuePresenter by CategoricalValuePresenter()