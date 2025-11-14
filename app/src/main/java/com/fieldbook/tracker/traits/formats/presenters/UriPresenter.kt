package com.fieldbook.tracker.traits.formats.presenters

import android.content.Context
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.fieldbook.tracker.objects.TraitObject

class UriPresenter : ValuePresenter {
    override fun represent(context: Context, value: Any, trait: TraitObject?): String {

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
