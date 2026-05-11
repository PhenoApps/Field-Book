package com.fieldbook.tracker.traits.formats.presenters

import android.content.Context
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.utilities.CategoryJsonUtil
import org.brapi.v2.model.pheno.BrAPIScaleValidValuesCategories

class CategoricalValuePresenter : ValuePresenter {
    override fun represent(context: Context, value: Any, trait: TraitObject?): String {

        val useLabel = trait?.categoryDisplayValue == false

        return CategoryJsonUtil.flattenMultiCategoryValue(
            value as ArrayList<BrAPIScaleValidValuesCategories>,
            useLabel
        )
    }
}
