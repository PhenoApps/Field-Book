package com.fieldbook.tracker.utilities;

import android.content.Context;
import android.text.Spannable;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.widget.TextView;

import com.michaelflisar.changelog.ChangelogBuilder;
import com.michaelflisar.changelog.classes.ChangelogRenderer;
import com.michaelflisar.changelog.internal.ChangelogRecyclerViewAdapter;
import com.michaelflisar.changelog.items.ItemMore;
import com.michaelflisar.changelog.items.ItemRelease;
import com.michaelflisar.changelog.items.ItemRow;

/**
 * Html.fromHtml leaves unstyled changelog body text black; tint rows from the active theme.
 */
public class FieldBookChangelogRenderer extends ChangelogRenderer {

    @Override
    public void bindHeader(ChangelogRecyclerViewAdapter adapter, Context context,
                           ViewHolderHeader holder, ItemRelease item, ChangelogBuilder builder) {
        super.bindHeader(adapter, context, holder, item, builder);
        if (item == null) {
            return;
        }
        int primary = resolveThemeColor(context, android.R.attr.textColorPrimary);
        holder.getTvVersion().setTextColor(primary);
        holder.getTvDate().setTextColor(primary);
    }

    @Override
    public void bindRow(ChangelogRecyclerViewAdapter adapter, Context context,
                        ViewHolderRow holder, ItemRow item, ChangelogBuilder builder) {
        super.bindRow(adapter, context, holder, item, builder);
        if (item == null) {
            return;
        }
        int bodyColor = resolveThemeColor(context, android.R.attr.textColorSecondary);
        TextView text = holder.getTvText();
        text.setTextColor(bodyColor);
        colorizeBodyAfterColoredPrefix(text, bodyColor);
        holder.getTvBullet().setTextColor(bodyColor);
    }

    @Override
    public void bindMore(ChangelogRecyclerViewAdapter adapter, Context context,
                         ViewHolderMore holder, ItemMore item, ChangelogBuilder builder) {
        super.bindMore(adapter, context, holder, item, builder);
    }

    private static int resolveThemeColor(Context context, int attr) {
        TypedValue typedValue = new TypedValue();
        if (context.getTheme().resolveAttribute(attr, typedValue, true)) {
            return typedValue.data;
        }
        return 0;
    }

    /**
     * Rows are built as a colored "New:" / "Bug:" prefix plus plain body text; only the suffix is recolored.
     */
    private static void colorizeBodyAfterColoredPrefix(TextView textView, int bodyColor) {
        CharSequence content = textView.getText();
        if (!(content instanceof Spannable)) {
            return;
        }
        Spannable spannable = (Spannable) content;
        ForegroundColorSpan[] colorSpans =
                spannable.getSpans(0, spannable.length(), ForegroundColorSpan.class);
        int prefixEnd = 0;
        for (ForegroundColorSpan span : colorSpans) {
            prefixEnd = Math.max(prefixEnd, spannable.getSpanEnd(span));
        }
        if (prefixEnd < spannable.length()) {
            spannable.setSpan(
                    new ForegroundColorSpan(bodyColor),
                    prefixEnd,
                    spannable.length(),
                    Spannable.SPAN_EXCLUSIVE_INCLUSIVE
            );
        }
    }
}
