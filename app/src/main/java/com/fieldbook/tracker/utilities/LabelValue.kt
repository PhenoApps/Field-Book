package com.fieldbook.tracker.utilities

import android.content.Context
import android.util.Log
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.preferences.GeneralKeys
import java.util.StringJoiner
import com.fieldbook.tracker.database.DataHelper
import javax.inject.Inject

open class LabelValue {

    @Inject
    open lateinit var database: DataHelper

    /**
     * Queries the database for the value of the label.
     * Attributes use the getDropDownRange call, where traits use getUserDetail
     */
    fun queryForLabelValue(
        context: Context, plotId: String, label: String, isAttribute: Boolean, s: String
    ): String {
        Log.d(CollectActivity.TAG, "queryForLabelValue: $s")

        val dataMissingString: String = context.getString(R.string.main_infobar_data_missing)

        return if (isAttribute) {

            val values = database.getDropDownRange(label, plotId)
            if (values == null || values.isEmpty()) {
                dataMissingString
            } else {
                values[0]
            }

        } else {

            var value = database.getUserDetail(plotId)?.get(label) ?: dataMissingString

            value = try {

                val labelValPref: String = (context as CollectActivity).getPreferences()
                    .getString(GeneralKeys.LABELVAL_CUSTOMIZE, "value") ?: "value"

                val joiner = StringJoiner(":")
                val scale = CategoryJsonUtil.decode(value)
                for (s in scale) {
                    if (labelValPref == "label") {
                        joiner.add(s.label)
                    } else joiner.add(s.value)
                }

                joiner.toString()

            } catch (ignore: Exception) {
                value
            }

            value
        }
    }
}