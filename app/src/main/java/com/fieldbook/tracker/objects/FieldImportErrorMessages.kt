package com.fieldbook.tracker.objects

import android.content.Context
import android.text.Html
import android.text.TextUtils
import com.fieldbook.tracker.R

/**
 * Turns [FieldImportError]s into the message shown when a field import fails, with the values to look for in bold.
 */
object FieldImportErrorMessages {

    /** Problems described in full before the rest are only counted. */
    private const val MAX_LISTED_ERRORS = 5

    /** Row numbers listed for one problem before the rest are elided. */
    private const val MAX_LISTED_ROWS = 10

    @JvmStatic
    fun format(context: Context, errors: List<FieldImportError>): CharSequence {
        val paragraphs = errors.take(MAX_LISTED_ERRORS).mapTo(ArrayList()) { describe(context, it) }

        val unlisted = errors.size - MAX_LISTED_ERRORS
        if (unlisted > 0) {
            paragraphs += context.resources.getQuantityString(R.plurals.import_error_more, unlisted, unlisted)
        }

        if (errors.any { it !is FieldImportError.UniqueIdsExist }) {
            paragraphs += context.getString(R.string.import_error_fix_file)
        }

        return Html.fromHtml(paragraphs.joinToString("<br><br>"), Html.FROM_HTML_MODE_LEGACY)
    }

    private fun describe(context: Context, error: FieldImportError): String = when (error) {

        is FieldImportError.UniqueColumnNotFound ->
            context.getString(R.string.import_error_unique_column_not_found, bold(error.column))

        is FieldImportError.NoData ->
            context.getString(R.string.import_error_no_data)

        is FieldImportError.IllegalCharacter ->
            context.getString(R.string.import_error_unique_id_illegal_character,
                bold(error.value), error.row, bold(error.character.toString()))

        is FieldImportError.DuplicateUniqueId ->
            context.getString(R.string.import_error_unique_id_duplicated,
                bold(error.value), rowList(error.rows))

        is FieldImportError.MissingUniqueIds ->
            context.resources.getQuantityString(R.plurals.import_error_unique_id_missing, error.rows.size,
                bold(error.column), rowList(error.rows))

        is FieldImportError.UniqueIdsExist ->
            context.resources.getQuantityString(R.plurals.import_error_unique_id_exists, error.values.size,
                bold(error.values.first()), bold(error.field), error.values.size)
    }

    private fun bold(value: String) = "<b>${TextUtils.htmlEncode(value)}</b>"

    private fun rowList(rows: List<Int>): String {
        val listed = rows.take(MAX_LISTED_ROWS).joinToString(", ")
        return if (rows.size > MAX_LISTED_ROWS) "$listed, …" else listed
    }
}
