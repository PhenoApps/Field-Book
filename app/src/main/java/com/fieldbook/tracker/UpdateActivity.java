package com.fieldbook.tracker;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;

import com.fieldbook.tracker.utilities.Constants;
import com.fieldbook.tracker.utilities.Utils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

// TODO work in progress activity to update app outside of google play

public class UpdateActivity extends AppCompatActivity {

    private String currentServerVersion = "";
    String versionName;
    int versionNum;

    private void checkNewVersion() {
        final PackageManager packageManager = this.getPackageManager();

        try {
            PackageInfo packageInfo = packageManager.getPackageInfo(this.getPackageName(), 0);
            versionName = packageInfo.versionName;
            versionNum = packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            versionName = null;
        }

        new checkVersion().execute();
    }

    private class checkVersion extends AsyncTask<Void, Void, Void> {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Void doInBackground(Void... params) {
            if (activeNetworkInfo != null && activeNetworkInfo.isConnected()) {
                try {
                    Document doc = Jsoup
                            .connect("http://wheatgenetics.org/appupdates/fieldbook/currentversion.html"
                            )
                            .get();
                    Elements spans = doc.select("div[itemprop=softwareVersion]");
                    currentServerVersion = spans.first().ownText();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            int currentServerVersionInt = 0;

            if(!currentServerVersion.equals("")) {
                currentServerVersionInt = Integer.parseInt(currentServerVersion.replace(".", ""));
            }

            System.out.println("Field.Book." + currentServerVersion + ".apk" + "\t" + versionName);
            if (activeNetworkInfo != null && activeNetworkInfo.isConnected() && currentServerVersionInt>versionNum && currentServerVersion.length()>0) {
                downloadUpdate();
            }
        }
    }

    private void downloadUpdate() {
        AlertDialog.Builder builder = new AlertDialog.Builder(UpdateActivity.this);

        builder.setTitle(getString(R.string.update));
        builder.setMessage(getString(R.string.newversion));

        if (isGooglePlayInstalled(this)) {
            builder.setPositiveButton(getString(R.string.googleplay), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + "com.fieldbook.tracker")));
                }
            });
        } else {
            builder.setPositiveButton(getString(R.string.installnow), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    new downloadUpdate().execute();
                }
            });
        }

        builder.setNeutralButton(getString(R.string.later), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }

        });

        AlertDialog alert = builder.create();
        alert.show();
    }

    private class downloadUpdate extends AsyncTask<Void, Void, Void> {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Void doInBackground(Void... params) {
            if (activeNetworkInfo != null && activeNetworkInfo.isConnected()) {
                try {
                    URL u = new URL("http://wheatgenetics.org/appupdates/fieldbook/" + "Field.Book."+ currentServerVersion +".apk");
                    HttpURLConnection c = (HttpURLConnection) u.openConnection();
                    c.setRequestMethod("GET");
                    c.setDoOutput(true);
                    c.connect();
                    FileOutputStream f = new FileOutputStream(new File(Constants.UPDATEPATH,"/Field.Book."+ currentServerVersion +".apk"));

                    InputStream in = c.getInputStream();

                    byte[] buffer = new byte[1024];
                    int len1;
                    while ( (len1 = in.read(buffer)) > 0 ) {
                        f.write(buffer,0, len1);
                    }
                    f.close();
                } catch (Exception e) {
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            Utils.scanFile(UpdateActivity.this, new File(Constants.UPDATEPATH,"/Field.Book."+ currentServerVersion +".apk"));
            installUpdate();
        }
    }

    private void installUpdate() {
        if (new File(Constants.UPDATEPATH, "/Field.Book."+ currentServerVersion + ".apk").exists()) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(new File(Constants.UPDATEPATH + "/Field.Book."+ currentServerVersion +".apk")), "application/vnd.android.package-archive");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // without this flag android returned a intent error!
            startActivity(intent);
        }

        //TODO delete downloaded apk
    }

    public static boolean isGooglePlayInstalled(Context context) {
        PackageManager pm = context.getPackageManager();
        boolean app_installed;
        try
        {
            PackageInfo info = pm.getPackageInfo("com.android.vending", PackageManager.GET_ACTIVITIES);
            String label = (String) info.applicationInfo.loadLabel(pm);
            app_installed = (label != null && !label.equals("Market"));
        }
        catch (PackageManager.NameNotFoundException e)
        {
            app_installed = false;
        }
        return app_installed;
    }
}