package com.fieldbook.tracker.dialogs

import android.content.Context
import android.text.Html
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.fieldbook.tracker.R

class CitationDialog(private val context: Context) {

    fun show() {
        val citationMessage = context.getString(R.string.citation_string)
        val citationText = context.getString(R.string.citation_text)
        val citationLink = "http://dx.doi.org/10.2135/cropsci2013.08.0579"

        val builder = AlertDialog.Builder(context, R.style.AppAlertDialog)
        builder.setTitle(context.getString(R.string.citation_title))
            .setMessage(Html.fromHtml("$citationMessage<br/><br/><a href=\"$citationLink\">$citationText</a>"))
            .setCancelable(false)
            .setPositiveButton(context.getString(R.string.dialog_ok)) { dialog, _ ->
                dialog.dismiss()
            }

        val alert = builder.create()
        alert.show()
        (alert.findViewById(android.R.id.message) as? TextView)?.movementMethod = LinkMovementMethod.getInstance()
    }
}
