package com.fieldbook.tracker.dialogs

import com.fieldbook.tracker.utilities.ThemedAlertDialog
import android.content.Context
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.text.HtmlCompat
import com.fieldbook.tracker.R

class CitationDialog(private val context: Context) {

    fun show() {
        val citationMessage = context.getString(R.string.citation_string)
        val citationText = context.getString(R.string.citation_text)
        val citationLink = "http://dx.doi.org/10.2135/cropsci2013.08.0579"

        val htmlMessage = """
            $citationMessage<br/><br/>
            <a href="$citationLink">
                <tt>$citationText</tt>
            </a>
        """.trimIndent()

        val builder = ThemedAlertDialog.builder(context)
        builder.setTitle(context.getString(R.string.citation_title))
            .setMessage(HtmlCompat.fromHtml(htmlMessage, HtmlCompat.FROM_HTML_MODE_LEGACY))
            .setCancelable(false)
            .setPositiveButton(context.getString(R.string.dialog_ok)) { dialog, _ ->
                dialog.dismiss()
            }

        val alert = builder.create()
        alert.show()
        (alert.findViewById(android.R.id.message) as? TextView)?.movementMethod = LinkMovementMethod.getInstance()
    }
}
