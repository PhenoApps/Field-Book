package com.fieldbook.tracker.utilities.export

import android.content.Context
import android.util.Log
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.CategoricalTraitLayout
import com.fieldbook.tracker.traits.formats.Formats
import com.fieldbook.tracker.traits.formats.presenters.UriPresenter
import com.fieldbook.tracker.traits.formats.presenters.ValuePresenter
import com.fieldbook.tracker.utilities.CategoryJsonUtil
import com.fieldbook.tracker.utilities.DateJsonUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class ValueProcessorFormatAdapter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val spectralFileProcessor: SpectralFileProcessor
) {

    companion object {
        private const val TAG = "ValueProcessorFA"
    }

    fun processValue(value: String, trait: TraitObject): String? {
        return when (trait.format) {

            in setOf(Formats.DATE.getDatabaseName()) -> {

                val dateValue = DateJsonUtil.decode(value)

                return (Formats.DATE.getTraitFormatDefinition() as ValuePresenter).represent(context, dateValue, trait)
            }

            in CategoricalTraitLayout.POSSIBLE_VALUES + setOf("multicat") -> return CategoryJsonUtil.processValue(
                buildMap {
                    put("value", value)
                    put("observation_variable_field_book_format", trait.format)
                })

            in Formats.getSpectralFormats().map { it.getDatabaseName() } -> {
                spectralFileProcessor.processValue(value)
                    .onSuccess { value ->
                        val presentable = UriPresenter().represent(context, value)
                        return presentable
                    }
                    .onFailure {

                        Log.d(TAG, "Error processing value: ${it.message}")

                        return ""
                    }
            }

            else -> value

        }.toString()
    }
}