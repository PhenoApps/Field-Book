package com.fieldbook.tracker.utilities;

import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;

import com.fieldbook.tracker.R;
import com.google.android.material.snackbar.Snackbar;

public final class SnackbarUtils {
    private static final int DURATION_SHORT = 3500;
    private static final int DURATION_LONG = 5500;

    private SnackbarUtils() {

    }

    public static void showShortSnackbar(@NonNull View view, @NonNull String message) {
        showSnackbar(view, message, DURATION_SHORT);
    }

    public static void showLongSnackbar(@NonNull View view, @NonNull String message) {
        showSnackbar(view, message, DURATION_LONG);
    }

    /**
     * Displays snackbar with {@param message}
     * and multi-line message enabled.
     *
     * @param view    The view to find a parent from.
     * @param message The text to show.  Can be formatted text.
     */
    private static void showSnackbar(@NonNull View view, @NonNull String message, int duration) {
        if (message.isEmpty()) {
            return;
        }

        Snackbar snackbar = Snackbar.make(view, message.trim(), duration);
        TextView textView = snackbar.getView().findViewById(com.google.android.material.R.id.snackbar_text);
        textView.setSingleLine(false);

        // Apply theme-aware colors: prefer app-specific attributes (fb_color_primary_dark for bg,
        // fb_color_text_light for text). Fall back to Material default colors if attrs not present.
        try {
            Context ctx = view.getContext();
            TypedValue tv = new TypedValue();

            int bgColor = Color.parseColor("#323232"); // default material snack background
            int txtColor = Color.WHITE; // default material snack text

            // Resolve background color from theme attribute fb_color_primary_dark, fallback to colorPrimaryDark
            if (ctx.getTheme().resolveAttribute(R.attr.fb_color_primary_dark, tv, true)) {
                if (tv.resourceId != 0) bgColor = ContextCompat.getColor(ctx, tv.resourceId);
                else bgColor = tv.data;
            } else if (ctx.getTheme().resolveAttribute(android.R.attr.colorPrimaryDark, tv, true)) {
                if (tv.resourceId != 0) bgColor = ContextCompat.getColor(ctx, tv.resourceId);
                else bgColor = tv.data;
            }

            // Resolve text color from fb_color_text_light, fallback to white or android:textColorPrimary
            if (ctx.getTheme().resolveAttribute(R.attr.fb_color_text_light, tv, true)) {
                if (tv.resourceId != 0) txtColor = ContextCompat.getColor(ctx, tv.resourceId);
                else txtColor = tv.data;
            } else if (ctx.getTheme().resolveAttribute(android.R.attr.textColorPrimary, tv, true)) {
                if (tv.resourceId != 0) txtColor = ContextCompat.getColor(ctx, tv.resourceId);
                else txtColor = tv.data;
            }

            // Apply background tint using ColorStateList for broader compatibility
            try {
                snackbar.getView().setBackgroundTintList(ColorStateList.valueOf(bgColor));
            } catch (NoSuchMethodError ignored) {
                // fallback
                snackbar.setBackgroundTint(bgColor);
            }
            textView.setTextColor(txtColor);
        } catch (Exception ignored) {
            // ignore and leave defaults
        }

        snackbar.show();
    }

    public static void showNavigateSnack(LayoutInflater inflater, View view,
                                         String msg,
                                         @Nullable Integer anchorViewId,
                                         int duration,
                                         @Nullable Boolean showGeoNavIcon,
                                         View.OnClickListener onClickListener) {

        Snackbar snackbar = Snackbar.make(view, msg, duration);

        ViewGroup snackLayout = (ViewGroup) snackbar.getView();
        View snackView = inflater.inflate(R.layout.geonav_snackbar_layout, snackLayout, false);
        ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        snackView.setLayoutParams(params);
        snackLayout.addView(snackView);
        snackLayout.setPadding(0, 0, 0, 0);

        TextView tv = snackView.findViewById(R.id.geonav_snackbar_tv);
        if (tv != null) {
            tv.setText(msg);
        }

        ImageButton btn = snackView.findViewById(R.id.geonav_snackbar_btn);

        if (onClickListener == null) {
            btn.setVisibility(View.GONE);
        } else if (btn != null) {
            btn.setOnClickListener((v) -> {

                snackbar.dismiss();

                onClickListener.onClick(v);

            });
        }

        if (!(showGeoNavIcon != null && showGeoNavIcon)) {
            ((ImageButton) snackView.findViewById(R.id.geonav_snackbar_icn))
                    .setImageResource(R.drawable.ic_snackbar_fields);
        }

        snackbar.setBackgroundTint(Color.TRANSPARENT);

        if (anchorViewId != null) {
            snackbar.setAnchorView(anchorViewId);
        }

        snackbar.show();
    }
}