package com.dropbox.chooser.android;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager.LayoutParams;
import android.widget.Button;
import android.widget.TextView;

import com.dropbox.chooser.android.R;

class AppStoreInterstitial {
    public static final String DIALOG_TAG = "com.dropbox.chooser.android.DIALOG";
    private static final String DROPBOX_PACKAGE_NAME = "com.dropbox.android";

    @SuppressLint("NewApi") // lint isn't clever enough to figure out that we branched manually :(
    public static void showInterstitial(ActivityLike thing) {
        if (thing.getSupportFragmentManager() != null) {
            SupportFragment frag = SupportFragment.newInstance();
            android.support.v4.app.FragmentManager fm = thing.getSupportFragmentManager();
            frag.show(fm, DIALOG_TAG);
        } else {
            NativeFragment frag = NativeFragment.newInstance();
            FragmentManager fm = thing.getFragmentManager();
            frag.show(fm, DIALOG_TAG);
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class NativeFragment extends DialogFragment {
        public static NativeFragment newInstance() {
            return new NativeFragment();
        }

        @Override
        public Dialog onCreateDialog(Bundle savedinstanceState) {
            final NativeFragment frag = this;
            View v = getActivity().getLayoutInflater().inflate(R.layout.app_store_interstitial, null);

            setStrings(v, isDropboxInstalled(getActivity()));

            Button okButton = (Button) v.findViewById(R.id.dbx_bottom_bar_ok_button);
            okButton.setOnClickListener(new OnClickListener() {
                public void onClick(View v_clicked) {
                    frag.dismiss();
                    launchMarket(frag.getActivity());
                }
            });

            Button cancelButton = (Button) v.findViewById(R.id.dbx_bottom_bar_cancel_button);
            cancelButton.setOnClickListener(new OnClickListener() {
                public void onClick(View v_clicked) {
                    frag.dismiss();
                }
            });

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setView(v);
            return builder.create();
        }

        @Override
        public void onStart() {
            super.onStart();
            centerWindow(getDialog().getWindow());
        }
    }

    public static class SupportFragment extends android.support.v4.app.DialogFragment {
        public static SupportFragment newInstance() {
            return new SupportFragment();
        }

        @Override
        public Dialog onCreateDialog(Bundle savedinstanceState) {
            final SupportFragment frag = this;
            View v = getActivity().getLayoutInflater().inflate(R.layout.app_store_interstitial, null);

            setStrings(v, isDropboxInstalled(getActivity()));

            Button okButton = (Button) v.findViewById(R.id.dbx_bottom_bar_ok_button);
            okButton.setOnClickListener(new OnClickListener() {
                public void onClick(View v_clicked) {
                    frag.dismiss();
                    launchMarket(frag.getActivity());
                }
            });

            Button cancelButton = (Button) v.findViewById(R.id.dbx_bottom_bar_cancel_button);
            cancelButton.setOnClickListener(new OnClickListener() {
                public void onClick(View v_clicked) {
                    frag.dismiss();
                }
            });

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setView(v);
            return builder.create();
        }

        @Override
        public void onStart() {
            super.onStart();
            centerWindow(getDialog().getWindow());
        }
    }

    private static final int MAX_DIALOG_WIDTH_DP = 590;
    private static final int MAX_DIALOG_HEIGHT_DP = 700;

    private static final int DLG_PADDING_DP = 10;
    private static final int APPROX_STATUSBAR_HEIGHT_DP = 25; // TODO: be better to have the right one and know if it's on top or bottom

    private static void centerWindow(Window w) {
        DisplayMetrics metrics = new DisplayMetrics();
        Display display = w.getWindowManager().getDefaultDisplay();
        display.getMetrics(metrics);

        int width = Math.min(metrics.widthPixels - (int)(2 * DLG_PADDING_DP * metrics.density),
                             (int)(MAX_DIALOG_WIDTH_DP * metrics.density));
        int height = Math.min(metrics.heightPixels - (int)((2 * DLG_PADDING_DP + APPROX_STATUSBAR_HEIGHT_DP) * metrics.density),
                              (int)(MAX_DIALOG_HEIGHT_DP * metrics.density));

        int x = (metrics.widthPixels - width) / 2;
        int y = (metrics.heightPixels - height - ((int) (APPROX_STATUSBAR_HEIGHT_DP * metrics.density))) / 2;

        LayoutParams params = w.getAttributes();
        params.x = x;
        params.y = y;
        params.width = width;
        params.height = height;
        w.setAttributes(params);
        w.setGravity(Gravity.LEFT|Gravity.TOP);
    }

    private static void setStrings(View v, boolean needUpdate) {
        TextView title = (TextView) v.findViewById(R.id.dbx_install_title);
        TextView main = (TextView) v.findViewById(R.id.dbx_install_main);
        TextView sub = (TextView) v.findViewById(R.id.dbx_install_sub);
        Button okButton = (Button) v.findViewById(R.id.dbx_bottom_bar_ok_button);
        Button cancelButton = (Button) v.findViewById(R.id.dbx_bottom_bar_cancel_button);

        if (needUpdate) {
            title.setText(R.string.dbx_update);
            main.setText(R.string.dbx_update_main);
            sub.setText(R.string.dbx_update_sub);
            okButton.setText(R.string.dbx_update_button_ok);
        } else {
            // first-time install
            title.setText(R.string.dbx_install);
            main.setText(R.string.dbx_install_main);
            sub.setText(R.string.dbx_install_sub);
            okButton.setText(R.string.dbx_install_button_ok);
        }
        cancelButton.setText(R.string.dbx_install_button_cancel);
    }

    private static boolean isDropboxInstalled(Activity act) {
        PackageManager pm = act.getPackageManager();
        try {
            pm.getPackageInfo(DROPBOX_PACKAGE_NAME, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private static void launchMarket(Activity act) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        // Market page for the official Dropbox App.
        intent.setData(Uri.parse("market://details?id=" + DROPBOX_PACKAGE_NAME));
        act.startActivity(intent);
    }
}
