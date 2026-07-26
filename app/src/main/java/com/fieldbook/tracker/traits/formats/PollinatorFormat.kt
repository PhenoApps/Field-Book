package com.fieldbook.tracker.traits.formats

import android.content.Context
import com.fieldbook.tracker.R
import com.fieldbook.tracker.traits.formats.parameters.AttachMediaParameter
import com.fieldbook.tracker.traits.formats.parameters.BaseFormatParameter
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
    CategoriesParameter(),
    DurationParameter(),
    RepeatedMeasureParameter(),
    AttachMediaParameter()
), ValuePresenter by PollinatorValuePresenter() {

    //counts are recorded per category, the trait is unusable without at least one
    override fun validate(
        context: Context,
        parameterViewHolders: List<BaseFormatParameter.ViewHolder>
    ) = ValidationResult().apply {

        val categoriesHolder = parameterViewHolders
            .find { it is CategoriesParameter.ViewHolder } as? CategoriesParameter.ViewHolder

        if (categoriesHolder?.hasCategories() != true) {

            val message = context.getString(R.string.traits_create_warning_categories_required)

            result = false

            error = message

            categoriesHolder?.let { it.valueEt.error = message }
        }
    }
}
