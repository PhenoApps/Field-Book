package com.fieldbook.tracker.traits.formats

import com.fieldbook.tracker.R
import com.fieldbook.tracker.traits.formats.coders.DateJsonCoder
import com.fieldbook.tracker.traits.formats.coders.StringCoder
import com.fieldbook.tracker.traits.formats.feature.ChartableData
import com.fieldbook.tracker.traits.formats.feature.DisplayValue
import com.fieldbook.tracker.traits.formats.feature.Scannable
import com.fieldbook.tracker.traits.formats.parameters.AutoSwitchPlotParameter
import com.fieldbook.tracker.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.traits.formats.parameters.AttachMediaParameter
import com.fieldbook.tracker.traits.formats.parameters.NameParameter
import com.fieldbook.tracker.traits.formats.parameters.RepeatedMeasureParameter
import com.fieldbook.tracker.traits.formats.parameters.ResourceFileParameter
import com.fieldbook.tracker.traits.formats.parameters.UseDayOfYearParameter
import com.fieldbook.tracker.traits.formats.presenters.DateValuePresenter
import com.fieldbook.tracker.traits.formats.presenters.ValuePresenter

class DateFormat : TraitFormat(
    format = Formats.DATE,
    defaultLayoutId = R.layout.trait_date,
    layoutView = null,
    databaseName = "date",
    nameStringResourceId = R.string.traits_format_date,
    iconDrawableResourceId = R.drawable.ic_trait_date,
    stringNameAux = null,
    NameParameter(),
    DetailsParameter(),
    UseDayOfYearParameter(),
    AutoSwitchPlotParameter(),
    RepeatedMeasureParameter(),
    ResourceFileParameter(),
    AttachMediaParameter()
), Scannable, StringCoder by DateJsonCoder(), ValuePresenter by DateValuePresenter(),
    ChartableData, DisplayValue