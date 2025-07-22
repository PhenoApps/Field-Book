package com.fieldbook.tracker.utilities;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.storage.StorageManager;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;

import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.preferences.GeneralKeys;
import com.fieldbook.tracker.preferences.PreferenceKeys;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.LinkedHashSet;

public final class FileUtil {

    static String TAG = "FileUtil";

    private static final String PRIMARY_VOLUME_NAME = "primary";

    //https://stackoverflow.com/questions/2679699/what-characters-allowed-in-file-names-on-android
    public static String sanitizeFileName(String name) {
        //erase all C0 set characters (0x00-0x1F) and replace some other illegal characters with '_'
        return name.replaceAll("[\\x00-\\x1f]", "")
                .replaceAll("[|\\?\\*<\"\\\\:>'\";]", "_");
    }

    /**
     * Checks a name for illegal characters and returns details about any that are found.
     */
    public static String checkForIllegalCharacters(String name) {
        String illegalChars = "|\\?*<\":>'/;";
        Set<Character> foundIllegalChars = new LinkedHashSet<>();

        for (char c : name.toCharArray()) {
            if (illegalChars.indexOf(c) >= 0) {
                foundIllegalChars.add(c);
            }
        }

        if (!foundIllegalChars.isEmpty()) {
            StringBuilder foundChars = new StringBuilder();
            for (Character illegalChar : foundIllegalChars) {
                foundChars.append(illegalChar).append(" ");
            }
            return foundChars.toString().trim();
        } else {
            return "";
        }
    }


    /**
     * Scan file to update file list and share exported file
     */
    public static void shareFile(Context context, SharedPreferences preferences, DocumentFile docFile) {
        if (docFile != null && docFile.exists()) {
            if (preferences.getBoolean(PreferenceKeys.ENABLE_SHARE, true)) {
                Intent intent = new Intent();
                intent.setAction(android.content.Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_STREAM, docFile.getUri());
                String sendingFileText = context.getString(R.string.share_file_title);
                context.startActivity(Intent.createChooser(intent, sendingFileText));
            }
        }
    }

    public String getFileName(Context context, Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index > 0) {
                        result = cursor.getString(index);
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    @Nullable
    public static String getFullPathFromTreeUri(@Nullable final Uri treeUri, Context con) {
        if (treeUri == null) return null;
        String volumePath = getVolumePath(getVolumeIdFromTreeUri(treeUri),con);
        if (volumePath == null) return File.separator;
        if (volumePath.endsWith(File.separator))
            volumePath = volumePath.substring(0, volumePath.length() - 1);

        String documentPath = getDocumentPathFromTreeUri(treeUri);
        if (documentPath.endsWith(File.separator))
            documentPath = documentPath.substring(0, documentPath.length() - 1);

        if (documentPath.length() > 0) {
            if (documentPath.startsWith(File.separator))
                return volumePath + documentPath;
            else
                return volumePath + File.separator + documentPath;
        }
        else return volumePath;
    }


    @SuppressLint("ObsoleteSdkInt")
    private static String getVolumePath(final String volumeId, Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return null;
        try {
            StorageManager mStorageManager =
                    (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
            Class<?> storageVolumeClazz = Class.forName("android.os.storage.StorageVolume");
            Method getVolumeList = mStorageManager.getClass().getMethod("getVolumeList");
            Method getUuid = storageVolumeClazz.getMethod("getUuid");
            Method getPath = storageVolumeClazz.getMethod("getPath");
            Method isPrimary = storageVolumeClazz.getMethod("isPrimary");
            Object result = getVolumeList.invoke(mStorageManager);

            final int length = Array.getLength(result);
            for (int i = 0; i < length; i++) {
                Object storageVolumeElement = Array.get(result, i);
                String uuid = (String) getUuid.invoke(storageVolumeElement);
                Boolean primary = (Boolean) isPrimary.invoke(storageVolumeElement);

                // primary volume?
                if (primary && PRIMARY_VOLUME_NAME.equals(volumeId))
                    return (String) getPath.invoke(storageVolumeElement);

                // other volumes?
                if (uuid != null && uuid.equals(volumeId))
                    return (String) getPath.invoke(storageVolumeElement);
            }
            // not found.
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static String getVolumeIdFromTreeUri(final Uri treeUri) {
        final String docId = DocumentsContract.getTreeDocumentId(treeUri);
        final String[] split = docId.split(":");
        if (split.length > 0) return split[0];
        else return null;
    }


    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static String getDocumentPathFromTreeUri(final Uri treeUri) {
        final String docId = DocumentsContract.getTreeDocumentId(treeUri);
        final String[] split = docId.split(":");
        if ((split.length >= 2) && (split[1] != null)) return split[1];
        else return File.separator;
    }
}