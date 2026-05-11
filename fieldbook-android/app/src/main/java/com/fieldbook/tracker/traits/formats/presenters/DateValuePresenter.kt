package com.fieldbook.tracker.traits.formats.presenters

import android.content.Context
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.formats.coders.DateJsonCoder

class DateValuePresenter : ValuePresenter {
    override fun represent(context: Context, value: Any, trait: TraitObject?): String {

        val dateJson = value as? DateJsonCoder.DateJson
        if (dateJson != null) {
            if (trait != null) {
                trait.loadAttributeAndValues()
                return if (trait.useDayOfYear) {
                    dateJson.dayOfYear
                } else dateJson.formattedDate
            }
        }
        return (value as? DateJsonCoder.DateJson)?.formattedDate ?: value.toString()
    }
}
