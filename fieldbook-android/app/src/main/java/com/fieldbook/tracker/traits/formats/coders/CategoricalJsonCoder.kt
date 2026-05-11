package com.fieldbook.tracker.traits.formats.coders

import com.fieldbook.tracker.utilities.CategoryJsonUtil
import org.brapi.v2.model.pheno.BrAPIScaleValidValuesCategories

class CategoricalJsonCoder : StringCoder {
    override fun encode(value: Any): String {

        return CategoryJsonUtil.encode(value as ArrayList<BrAPIScaleValidValuesCategories>)
    }

    override fun decode(value: String): Any {

        var decodedValue: Any = value

        try {

            decodedValue = CategoryJsonUtil.decode(value)

        } catch (_: Exception) { /*defaults to encoded string value */ }

        return decodedValue
    }
}
