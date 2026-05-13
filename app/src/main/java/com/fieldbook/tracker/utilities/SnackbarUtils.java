package com.fieldbook.tracker.utilities;

import android.content.Context;
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
    private static int resolveThemeColor(@NonNull Context ctx, int attrId, int fallbackColor) {
        TypedValue tv = new TypedValue();
        if (!ctx.getTheme().resolveAttribute(attrId, tv, true)) {
            return fallbackColor;
        }
        if (tv.resourceId != 0) {
            return ContextCompat.getColor(ctx, tv.resourceId);
        }
        return tv.data;
    }

    private static void showSnackbar(@NonNull View view, @NonNull String message, int duration) {
        if (message.isEmpty()) {
            return;
        }

        Snackbar snackbar = Snackbar.make(view, message.trim(), duration);
        TextView textView = snackbar.getView().findViewById(com.google.android.material.R.id.snackbar_text);
        textView.setSingleLine(false);

        // Theme-aware snackbar colors (fallbacks only if attrs are missing)
        try {
            Context ctx = view.getContext();

            int bgColor = resolveThemeColor(ctx, R.attr.fb_color_primary_dark, 0);
            if (bgColor == 0) {
                bgColor = resolveThemeColor(ctx, R.attr.fb_color_background, 0);
            }
            if (bgColor == 0) {
                bgColor = resolveThemeColor(ctx, android.R.attr.colorPrimaryDark, ContextCompat.getColor(ctx, android.R.color.darker_gray));
            }

            int txtColor = resolveThemeColor(ctx, R.attr.fb_color_text_light, 0);
            if (txtColor == 0) {
                txtColor = resolveThemeColor(ctx, android.R.attr.textColorPrimary, ContextCompat.getColor(ctx, android.R.color.white));
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