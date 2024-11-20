package com.fieldbook.tracker.traits.formats.presenters

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

class UriPresenter : ValuePresenter {
    override fun represent(context: Context, value: Any): String {

        var repr = value.toString()

        //query the content resolver for the value uri and return the human readable string file name
        context.contentResolver.query(Uri.parse(repr), null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index > -1) {
                    repr = cursor.getString(index)
                }
            }
        }

        return repr

    }
}
