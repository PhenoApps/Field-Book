package com.fieldbook.tracker.traits.formats.presenters

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri

class UriPresenter : ValuePresenter {
    override fun represent(context: Context, value: Any): String {

        var repr = value.toString()

        try {
            //query the content resolver for the value uri and return the human readable string file name
            context.contentResolver.query(repr.toUri(), null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index > -1) {
                        repr = cursor.getString(index)
                    }
                }
            }

        } catch (e: Exception) {

            return repr.split("%2F").lastOrNull() ?: repr

        }

        return repr

    }
}
