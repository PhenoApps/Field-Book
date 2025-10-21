package com.fieldbook.tracker.async;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.text.Html;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.CollectActivity;
import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.interfaces.FieldAdapterController;
import com.fieldbook.tracker.objects.FieldFileObject;
import com.fieldbook.tracker.objects.FieldObject;
import com.fieldbook.tracker.preferences.GeneralKeys;
import com.fieldbook.tracker.utilities.StringUtil;
import com.fieldbook.tracker.utilities.Utils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;

public class ImportRunnableTask extends AsyncTask<Integer, Integer, Integer> {
    ProgressDialog dialog;

    WeakReference<Context> mContext;
    FieldAdapterController controller;
    FieldFileObject.FieldFileBase mFieldFile;
    String unique;
    int idColPosition;
    SharedPreferences preferences;

    int lineFail = -1;
    boolean fail;
    private CharSequence failMessage;
    boolean uniqueFail;
    boolean containsDuplicates = false;
    boolean fieldNameExists = false;
    private static final String TAG = "ImportRunnableTask";

    public ImportRunnableTask(Context context, FieldFileObject.FieldFileBase fieldFile,
                              int idColPosition, String unique) {

        mFieldFile = fieldFile;

        if (context instanceof FieldAdapterController) {
            controller = (FieldAdapterController) context;
        }

        mContext = new WeakReference<>(context);

        preferences = PreferenceManager.getDefaultSharedPreferences(context);

        this.idColPosition = idColPosition;
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
            if (!verifyUniqueColumn(mFieldFile)) {
                uniqueFail = true;
                return 0;
            }

            if (mFieldFile.hasSpecialCharacters()) {
                Log.d(TAG, "doInBackground: Special characters found in file column names");
                return 0;
            }


            mFieldFile.open();
            String[] data;
            String[] columns = mFieldFile.readNext();
            ArrayList<String> nonEmptyColumns = new ArrayList<>();
            ArrayList<Integer> nonEmptyIndices = new ArrayList<>();

            int uniqueIndex = -1;
            int primaryIndex = -1;
            int secondaryIndex = -1;

            //match and delete special characters from header line
            for (int i = 0; i < columns.length; i++) {

                String header = columns[i];

                if (DataHelper.hasSpecialChars(header)) {
                    columns[i] = DataHelper.replaceSpecialChars(header);
                }

                //populate an array of indices that have a non empty column
                //later we will only add data rows with the non empty columns
                //also find the unique/primary/secondary indices
                //later we will return an error if these are not present
                if (!columns[i].isEmpty()) {

                    if (!nonEmptyColumns.contains(columns[i])) {
                        nonEmptyColumns.add(columns[i]);
                        nonEmptyIndices.add(i);

                        if (columns[i].equals(unique)) {
                            uniqueIndex = i;
                        }

                    } else containsDuplicates = true;
                }
            }

            FieldObject f = mFieldFile.createFieldObject();
            f.setUniqueId(unique);

            controller.getDatabase().beginTransaction();

            studyId = controller.getDatabase().createField(f, nonEmptyColumns, false);

            if (studyId == -1) {
                fieldNameExists = true;
                throw new RuntimeException();
            }

            //start iterating over all the rows of the csv file only if we found the u/p/s indices
            if (uniqueIndex > -1) {
                int line = 0;
                try {
                    while (true) {
                        data = mFieldFile.readNext();
                        if (data == null)
                            break;

                        //only load the row if it contains u/p/s data
                        int rowSize = data.length;

                        //ensure next check won't cause an AIOB
                        if (rowSize > uniqueIndex) {

                            //check that all u/p/s strings are not empty
                            if (!data[uniqueIndex].isEmpty()) {

                                ArrayList<String> nonEmptyData = new ArrayList<>();
                                for (int j = 0; j < data.length; j++) {
                                    if (nonEmptyIndices.contains(j)) {
                                        nonEmptyData.add(data[j]);
                                    }
                                }

                                controller.getDatabase().createFieldData(studyId, nonEmptyColumns, nonEmptyData);

                            } else {
                                fail = true;

                                String fixFileMessage = mContext.get().getString(R.string.import_runnable_create_field_fix_file);
                                String missingIdMessageTemplate = mContext.get().getString(R.string.import_runnable_create_field_missing_identifier);

                                String missingField = null;
                                String fieldValue = null;

                                if (data[uniqueIndex].isEmpty()) {
                                    missingField = mContext.get().getString(R.string.import_dialog_unique).toLowerCase();
                                    fieldValue = unique;
                                } else if (data[primaryIndex].isEmpty()) {
                                    missingField = mContext.get().getString(R.string.import_dialog_primary).toLowerCase();
                                    fieldValue = primary;
                                } else if (data[secondaryIndex].isEmpty()) {
                                    missingField = mContext.get().getString(R.string.import_dialog_secondary).toLowerCase();
                                    fieldValue = secondary;
                                }

                                if (missingField != null) {
                                    String missingIdMessage = String.format(missingIdMessageTemplate, missingField, fieldValue, line + 1);
                                    failMessage = StringUtil.INSTANCE.applyBoldStyleToString(
                                            String.format("%s\n\n%s", missingIdMessage, fixFileMessage),
                                            fieldValue,
                                            String.valueOf(line + 1)
                                    );
                                }
                            }
                        }

                        line++;
                    }

                    controller.getDatabase().setTransactionSuccessfull();
                    Log.d(TAG, "doInBackground: Field data created successfully for study ID: " + studyId);

                } catch (Exception e) {
                    Log.e(TAG, "doInBackground: Exception at line " + line + ", Error: " + e.getMessage(), e);

                    lineFail = line;

                    e.printStackTrace();

                    throw e;

                } finally {

                    controller.getDatabase().endTransaction();

                }
            } else {
                Log.d(TAG, "doInBackground: Required indices not found. UniqueIndex: " + uniqueIndex + ", PrimaryIndex: " + primaryIndex + ", SecondaryIndex: " + secondaryIndex);
            }


            mFieldFile.close();

            controller.getDatabase().close();
            controller.getDatabase().open();

            controller.getDatabase().updateImportDate(studyId);

        } catch (Exception e) {
            e.printStackTrace();
            fail = true;
            failMessage = mContext.get().getString(R.string.import_runnable_create_field_data_failed);
            controller.getDatabase().close();
            controller.getDatabase().open();
        }


        return studyId;
    }

    @Override
    protected void onPostExecute(Integer result) {

        Context context = mContext.get();

        if (dialog.isShowing())
            dialog.dismiss();

        // Display user feedback in an alert dialog
        if (context != null && (uniqueFail || mFieldFile.hasSpecialCharacters())) {
            CharSequence errorMessage = mFieldFile.getLastError();
            showAlertDialog(context, "Unable to Import", errorMessage);
        } else if (context != null && fail ) {
            showAlertDialog(context, "Unable to Import", failMessage);
        } else if (containsDuplicates) {
            showAlertDialog(context, "Import Warning", context.getString(R.string.import_runnable_duplicates_skipped));
        }

        if (fail || uniqueFail || mFieldFile.hasSpecialCharacters()) {
            controller.getDatabase().deleteField(result);
            SharedPreferences.Editor ed = preferences.edit();
            ed.putString(GeneralKeys.FIELD_FILE, null);
            ed.putBoolean(GeneralKeys.IMPORT_FIELD_FINISHED, false);
            ed.apply();
        } else {
            Log.d(TAG, "onPostExecute: Import successful. Field setup for ID: " + result);

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
        }
    }

    private boolean verifyUniqueColumn(FieldFileObject.FieldFileBase fieldFile) {
        HashMap<String, String> result = fieldFile.getColumnSet(unique, idColPosition);
        if (result == null) {
            return false;
        } else {
            return controller.getDatabase().checkUnique(result);
        }
    }

    private void showAlertDialog(Context context, String title, CharSequence message) {
        new AlertDialog.Builder(context, R.style.AppAlertDialog)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setCancelable(false)
                .show();
    }

}
