package com.dropbox.chooser.android;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;


/**
 * DbxChooser is a utility class to launch Dropbox's Android Chooser.
 *
 * Example use:
 * <p><blockquote><pre>
 *   new DbxChooser(APP_KEY)
 *       .forResultType(DbxChooser.ResultType.PREVIEW_LINK)
 *       .limitToExtensions(".png", ".jpg", ".jpeg")
 *       .launch(this, CHOOSER_REQUEST_CODE);
 * </pre></blockquote></p>
 * 
 * <p>
 * The result will be received in the onActivityResult callback of the Activity or Fragment supplied to {@link DbxChooser#launch}.
 * </p>
 * 
 */
public class DbxChooser {

    public enum ResultType {
        PREVIEW_LINK("com.dropbox.android.intent.action.GET_PREVIEW"),
        DIRECT_LINK("com.dropbox.android.intent.action.GET_DIRECT"),
        FILE_CONTENT("com.dropbox.android.intent.action.GET_CONTENT");

        final String action; // package-private

        ResultType(String action) {
            this.action = action;
        }
    }

    private static final String[] intentResultExtras = {
        "EXTRA_CHOOSER_RESULTS", // new unified result
        "EXTRA_PREVIEW_RESULTS", // these others for backwards compatibility
        "EXTRA_CONTENT_RESULTS",
    };

    /**
     * This is passed in the Intent to identify the version of the
     * client SDK. It should be incremented for any change in behavior
     * in this code.
     */
    private static final int SDK_VERSION = 2;

    private String mAction = ResultType.FILE_CONTENT.action;

    private boolean mForceNotAvailable = false;

    private final String mAppKey;


    public DbxChooser(String appKey) {
        if (appKey == null || appKey.length() == 0) {
            throw new IllegalArgumentException("An app key must be supplied.");
        }
        mAppKey = appKey;
    }
    

    private static boolean isChooserAvailable(PackageManager pm) {
        ResultType[] resultTypes = { ResultType.FILE_CONTENT, ResultType.PREVIEW_LINK, ResultType.DIRECT_LINK };
        for (ResultType resultType : resultTypes) {
            ResolveInfo ri = pm.resolveActivity(new Intent(resultType.action), PackageManager.MATCH_DEFAULT_ONLY);
            if (ri == null) {
                return false;
            }
        }
        return true;
    }

    private boolean chooserAvailable(PackageManager pm) {
        if (mForceNotAvailable) {
            return false;
        }
        return isChooserAvailable(pm);
    }

    /**
     * Requests that the Chooser return a particular type of result.
     * If this is not called, the default result type is FILE_CONTENT, which
     * returns a URI that can be opened to retrieve the contents of the chosen
     * file.
     */
    public DbxChooser forResultType(ResultType resultType) {
        if (resultType == null) {
            throw new IllegalArgumentException("An app key must be supplied.");
        }
        mAction = resultType.action;
        return this;
    }


    /**
     * For testing purposes, this causes DbxChooser to behave as if
     * the Dropbox Chooser isn't available.
     */
    public DbxChooser pretendNotAvailable() {
        mForceNotAvailable = true;
        return this;
    }

    private Intent getIntent() {
        Intent intent = new Intent(mAction).putExtra("EXTRA_APP_KEY", mAppKey);
        intent.putExtra("EXTRA_SDK_VERSION", SDK_VERSION);
        return intent;
    }

    /**
     * Launches the Chooser with the supplied request code.
     * The result will be received in the onActivityResult callback of the
     * supplied Activity.
     */
    public void launch(Activity act, int requestCode) throws ActivityNotFoundException {
        final Activity mAct = act;
        ActivityLike thing = new ActivityLike() {

            @Override
            public void startActivity(Intent intent)
                    throws ActivityNotFoundException {
                mAct.startActivity(intent);
            }

            @Override
            public void startActivityForResult(Intent intent, int requestCode)
                    throws ActivityNotFoundException {
                mAct.startActivityForResult(intent, requestCode);
            }

            @Override
            public ContentResolver getContentResolver() {
                return mAct.getContentResolver();
            }

            @Override
            public PackageManager getPackageManager() {
                return mAct.getPackageManager();
            }

            @Override
            public FragmentManager getFragmentManager() {
                try {
                    return mAct.getFragmentManager();
                } catch (NoSuchMethodError e) {
                    return null;
                }
            }

            @Override
            public android.support.v4.app.FragmentManager getSupportFragmentManager() {
                if (mAct instanceof android.support.v4.app.FragmentActivity) {
                    return ((android.support.v4.app.FragmentActivity) mAct).getSupportFragmentManager();
                } else {
                    return null;
                }
            }
        };
        launch(thing, requestCode);
    }

    /**
     * Launches the Chooser with the supplied request code.
     * The result will be received in the onActivityResult callback of the
     * supplied Fragment. If the supplied Fragment is not attached to an Activity,
     * this will throw an IllegalStateException.
     * 
     * NOTE: this method requires Android API at least version 11.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void launch(Fragment frag, int requestCode) throws ActivityNotFoundException {
        final Fragment mFrag = frag;
        ActivityLike thing = new ActivityLike() {

            @Override
            public void startActivity(Intent intent)
                    throws ActivityNotFoundException {
                mFrag.startActivity(intent);
            }

            @Override
            public void startActivityForResult(Intent intent, int requestCode)
                    throws ActivityNotFoundException {
                mFrag.startActivityForResult(intent, requestCode);
            }

            @Override
            public ContentResolver getContentResolver() {
                Activity act = mFrag.getActivity();
                if (act == null) {
                    return null;
                }
                return act.getContentResolver();
            }

            @Override
            public PackageManager getPackageManager() {
                Activity act = mFrag.getActivity();
                if (act == null) {
                    return null;
                }
                return act.getPackageManager();
            }

            @Override
            public FragmentManager getFragmentManager() {
                Activity act = mFrag.getActivity();
                if (act == null) {
                    return null;
                }
                return act.getFragmentManager();
            }

            @Override
            public android.support.v4.app.FragmentManager getSupportFragmentManager() {
                return null;
            }
        };
        launch(thing, requestCode);
    }

    /**
     * Launches the Chooser with the supplied request code.
     * The result will be received in the onActivityResult callback of the
     * supplied Fragment. If the supplied Fragment is not attached to an Activity,
     * this will throw an IllegalStateException.
     */
    public void launch(android.support.v4.app.Fragment frag, int requestCode) throws ActivityNotFoundException {
        final android.support.v4.app.Fragment mFrag = frag;
        ActivityLike thing = new ActivityLike() {

            @Override
            public void startActivity(Intent intent)
                    throws ActivityNotFoundException {
                mFrag.startActivity(intent);
            }

            @Override
            public void startActivityForResult(Intent intent, int requestCode)
                    throws ActivityNotFoundException {
                mFrag.startActivityForResult(intent, requestCode);
            }

            @Override
            public ContentResolver getContentResolver() {
                Activity act = mFrag.getActivity();
                if (act == null) {
                    return null;
                }
                return act.getContentResolver();
            }

            @Override
            public PackageManager getPackageManager() {
                Activity act = mFrag.getActivity();
                if (act == null) {
                    return null;
                }
                return act.getPackageManager();
            }

            @Override
            public FragmentManager getFragmentManager() {
                return null;
            }

            @Override
            public android.support.v4.app.FragmentManager getSupportFragmentManager() {
                android.support.v4.app.FragmentActivity act = mFrag.getActivity();
                if (act == null) {
                    return null;
                }
                return act.getSupportFragmentManager();
            }
        };
        launch(thing, requestCode);
    }

    private void launch(ActivityLike thing, int requestCode) {
        if (requestCode < 0) {
            throw new IllegalArgumentException("requestCode must be non-negative");
        }

        // Check whether we can show a fragment (needed for the app store interstitial)
        if (thing.getSupportFragmentManager() == null && thing.getFragmentManager() == null) {
            throw new IllegalArgumentException("Dropbox Chooser requires Fragments. If below API level 11, pass in a FragmentActivity from the support library.");
        }

        // Check whether we can launch the app
        PackageManager pm = thing.getPackageManager();
        if (pm == null) {
            throw new IllegalStateException("DbxChooser's launch() must be called when there is an Activity available");
        }
        if (!chooserAvailable(thing.getPackageManager())) {
            doAppStoreFallback(thing, requestCode);
            return;
        }


        // Launch the app
        Intent intent = getIntent();
        try {
            thing.startActivityForResult(intent, requestCode);
        } catch (ActivityNotFoundException e) {
            // Just being explicit.
            throw e;
        }
    }


    /**
     * Show interstitial, and then app store
     */
    private void doAppStoreFallback(ActivityLike thing, int requestCode) throws ActivityNotFoundException {
        AppStoreInterstitial.showInterstitial(thing);
    }


    /**
     * Helper class to access the result of a successful invocation of the Dropbox Chooser.
     *
     * Example use:
     * <p><blockquote><pre>
     * protected void onActivityResult(int requestCode, int resultCode, Intent data) {
     *    if (requestCode == CHOOSER_REQUEST_CODE && resultCode == RESULT_OK) {
     *       handlePreviewLink(new DropboxChooser.Result(data).getPreviewLink());
     *    }
     * }
     * </pre></blockquote></p>
     */
    public static class Result {

        private final Intent mIntent;

        /**
         * @param intent The Intent passed to onActivityResult as a result of the Dropbox Chooser.
         * If the request code doesn't match the one provided to {@link DbxChooser#launch(Activity, int)} or if the result code
         * was anything but {@link Activity#RESULT_OK}, this class will be unable to extract useful values.
         */
        public Result(Intent intent) {
            mIntent = intent;
        }

        /**
         * @return A Uri referring to the file selected by the user.
         * <ul>
         *  <li>If the chooser was for the ResultType PREVIEW_LINK or DIRECT_LINK, the
         *      Uri is a web link to a preview page for the file or the file itself.</li>
         *  <li>If the chooser was for the ResultType FILE_CONTENT, the Uri can be opened
         *      via a ContentResolver to provide the contents of the file, already downloaded.</li>
         * </ul>
         * If the provided Intent wasn't from a Chooser or a file wasn't chosen, this returns null.
         */
        public Uri getLink() {
            Bundle[] results = getResults();
            if (results.length == 0) {
                return null;
            }
            return results[0].getParcelable("uri");
        }

        /**
         * @return The name of the selected file.
         * If the provided Intent wasn't from a ResultType *_LINK Chooser, a file wasn't
         * chosen, or the file was chosen from an old version of the Dropbox app, this returns null.
         */
        public String getName() {
            Bundle[] results = getResults();
            if (results.length == 0) {
                return null;
            }
            return results[0].getString("name");
        }

        /**
         * @return A Map where the keys are thumbnail sizes and the values are
         * Uris that, when opened via a ContentResolver, provide thumbnails of
         * the file selected by the user.
         *
         * The map may be empty--this indicates no thumbnails could be
         * generated for the file. If the map is not empty, it will contain at
         * least the keys:
         * <ul>
         *    <li><pre>"64x64"</pre></li>
         *    <li><pre>"200x200"</pre></li>
         *    <li><pre>"640x480"</pre></li>
         * </ul>
         * If the provided Intent wasn't from a ResultType *_LINK Chooser, a file wasn't
         * chosen, or the file was chosen from an old version of the Dropbox app, this returns null.
         */
        public Map<String, Uri> getThumbnails() {
            Bundle[] results = getResults();
            if (results.length == 0) {
                return null;
            }
            Bundle thumbsBundle = results[0].getParcelable("thumbnails");
            if (thumbsBundle == null) {
                return null;
            }
            HashMap<String, Uri> thumbs = new HashMap<String, Uri>();
            for (String key : thumbsBundle.keySet()) {
                thumbs.put(key, (Uri) thumbsBundle.getParcelable(key));
            }
            return thumbs;
        }

        /**
         * @return A Uri that refers to an icon appropriate for the type of the given file.
         * If the provided Intent wasn't from a ResultType *_LINK Chooser, a file wasn't
         * chosen, or the file was chosen from an old version of the Dropbox app, this returns null.
         */
        public Uri getIcon() {
            Bundle[] results = getResults();
            if (results.length == 0) {
                return null;
            }
            return results[0].getParcelable("icon");
        }

        /**
         * @return The size of the file selected by the user, in bytes.
         * If the provided Intent wasn't from a ResultType *_LINK Chooser, a file wasn't
         * chosen, or the file was chosen from an old version of the Dropbox app, this returns -1.
         */
        public long getSize() {
            Bundle[] results = getResults();
            if (results.length == 0) {
                return -1;
            }
            return results[0].getLong("bytes", -1);
        }

        /**
         * @return An array of Bundle objects, one for each file selected.
         * If the provided Intent wasn't from a Chooser, this returns an empty array.
         */
        private Bundle[] getResults() {
            if (mIntent == null) {
                return new Bundle[] {};
            }
            for (String resultExtra : intentResultExtras) {
                Parcelable[] results = mIntent.getParcelableArrayExtra(resultExtra);
                if (results != null) {
                    Bundle[] resultBundles = new Bundle[results.length];
                    for (int i = 0; i < results.length; i++) {
                        resultBundles[i] = (Bundle) results[i];
                    }
                    return resultBundles;
                }
            }
            return new Bundle[] {};
        }
    }
}
