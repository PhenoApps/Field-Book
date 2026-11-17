package com.fieldbook.tracker.async;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.text.Html;
import android.util.Log;

import androidx.annotation.StringRes;
import androidx.preference.PreferenceManager;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.CollectActivity;
import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.interfaces.FieldAdapterController;
import com.fieldbook.tracker.objects.FieldFileObject;
import com.fieldbook.tracker.objects.FieldImportError;
import com.fieldbook.tracker.objects.FieldImportErrorMessages;
import com.fieldbook.tracker.objects.FieldImportValidator;
import com.fieldbook.tracker.objects.FieldObject;
import com.fieldbook.tracker.preferences.GeneralKeys;
import com.fieldbook.tracker.utilities.Utils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ImportRunnableTask extends AsyncTask<Integer, Integer, Integer> {

    private static final String TAG = "ImportRunnableTask";

    ProgressDialog dialog;

    WeakReference<Context> mContext;
    FieldAdapterController controller;
    FieldFileObject.FieldFileBase mFieldFile;
    String unique;
    SharedPreferences preferences;

    /** Problems in the file, or ids another field already uses; any of these stops the import. */
    private List<FieldImportError> errors = Collections.emptyList();
    /** Row being inserted when an unexpected error stopped the import, or -1. */
    private int failedRow = -1;
    boolean fail;
    boolean containsDuplicates = false;
    boolean fieldNameExists = false;

    public ImportRunnableTask(Context context, FieldFileObject.FieldFileBase fieldFile, String unique) {

        mFieldFile = fieldFile;

        if (context instanceof FieldAdapterController) {
            controller = (FieldAdapterController) context;
        }

        mContext = new WeakReference<>(context);

        preferences = PreferenceManager.getDefaultSharedPreferences(context);

        this.unique = unique;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();

        Context context = mContext.get();

        if (context != null) {
            dialog = new ProgressDialog(context);
            dialog.setIndeterminate(true);
            dialog.setCancelable(false);
            dialog.setMessage(Html.fromHtml(context.getString(R.string.import_dialog_importing)));
            dialog.show();
        }
    }

    @Override
    protected Integer doInBackground(Integer... params) {

        int studyId = -1;

        try {
            FieldImportValidator.Result file = FieldImportValidator.read(mFieldFile, unique);
            containsDuplicates = file.getHasDuplicateColumns();

            errors = file.getErrors();
            if (errors.isEmpty()) {
                errors = findIdsInUse(file.getUniqueIds());
            }
            if (!errors.isEmpty()) {
                return studyId;
            }

            FieldObject f = mFieldFile.createFieldObject();
            f.setUniqueId(unique);

            DataHelper database = controller.getDatabase();
            database.beginTransaction();

            try {
                studyId = database.createField(f, file.getColumns(), false);

                if (studyId == -1) {
                    fieldNameExists = true;
                    return studyId;
                }

                for (FieldImportValidator.Row row : file.getRows()) {
                    failedRow = row.getNumber();
                    database.createFieldData(studyId, file.getColumns(), row.getValues());
                }
                failedRow = -1;

                database.setTransactionSuccessfull();

            } finally {
                database.endTransaction();
            }

            database.close();
            database.open();

            database.updateImportDate(studyId);

        } catch (Exception e) {
            Log.e(TAG, "Field import failed at row " + failedRow, e);
            fail = true;
            controller.getDatabase().close();
            controller.getDatabase().open();
        }

        return studyId;
    }

    /**
     * @return one error per existing field that already uses some of {@code ids}, listing them in file order
     */
    private List<FieldImportError> findIdsInUse(List<String> ids) {
        Map<String, String> fieldById = controller.getDatabase().getFieldAliasesForIds(ids);
        if (fieldById.isEmpty()) return Collections.emptyList();

        Map<String, List<String>> idsByField = new LinkedHashMap<>();
        for (String id : ids) {
            String field = fieldById.get(id);
            if (field != null) {
                idsByField.computeIfAbsent(field, k -> new ArrayList<>()).add(id);
            }
        }

        List<FieldImportError> inUse = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : idsByField.entrySet()) {
            inUse.add(new FieldImportError.UniqueIdsExist(entry.getKey(), entry.getValue()));
        }
        return inUse;
    }

    @Override
    protected void onPostExecute(Integer result) {

        Context context = mContext.get();

        if (dialog != null && dialog.isShowing())
            dialog.dismiss();

        if (fail || fieldNameExists || !errors.isEmpty()) {
            if (result != -1) {
                controller.getDatabase().deleteField(result);
            }
            SharedPreferences.Editor ed = preferences.edit();
            ed.putString(GeneralKeys.FIELD_FILE, null);
            ed.putBoolean(GeneralKeys.IMPORT_FIELD_FINISHED, false);
            ed.apply();

            if (context != null) {
                showAlertDialog(context, R.string.import_error_title, failureMessage(context));
            }
        } else {
            SharedPreferences.Editor ed = preferences.edit();

            CollectActivity.reloadData = true;

            controller.queryAndLoadFields();

            try {

                controller.getFieldSwitcher().switchField(result);

            } catch (Exception e) {

                if (context != null) {

                    Utils.makeToast(context, context.getString(R.string.import_runnable_db_failed_to_switch));

                }

                ed.putBoolean(GeneralKeys.IMPORT_FIELD_FINISHED, false);
                ed.putInt(GeneralKeys.SELECTED_FIELD_ID, -1);
                ed.apply();

                e.printStackTrace();

            }

            if (containsDuplicates && context != null) {
                showAlertDialog(context, R.string.import_warning_title,
                        context.getString(R.string.import_runnable_duplicates_skipped));
            }
        }
    }

    private CharSequence failureMessage(Context context) {
        if (fieldNameExists) {
            return context.getString(R.string.import_runnable_field_name_exists);
        } else if (!errors.isEmpty()) {
            return FieldImportErrorMessages.format(context, errors);
        } else if (failedRow > 0) {
            return context.getString(R.string.import_runnable_create_field_data_failed, failedRow);
        } else {
            return context.getString(R.string.act_field_editor_load_file_failed);
        }
    }

    private void showAlertDialog(Context context, @StringRes int title, CharSequence message) {
        new AlertDialog.Builder(context, R.style.AppAlertDialog)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> dialog.dismiss())
                .setCancelable(false)
                .show();
    }
}
