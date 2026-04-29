package com.fieldbook.tracker.async;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.text.Html;

import androidx.preference.PreferenceManager;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.CollectActivity;
import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.interfaces.FieldAdapterController;
import com.fieldbook.tracker.objects.FieldFileObject;
import com.fieldbook.tracker.objects.FieldObject;
import com.fieldbook.tracker.preferences.GeneralKeys;
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
    boolean uniqueFail;
    boolean containsDuplicates = false;
    boolean fieldNameExists = false;

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
                //later we will skip the rows if these are not present
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

                            }
                        }

                        line++;
                    }

                    controller.getDatabase().setTransactionSuccessfull();

                } catch (Exception e) {

                    lineFail = line;

                    e.printStackTrace();

                    throw e;

                } finally {

                    controller.getDatabase().endTransaction();

                }
            }


            mFieldFile.close();

            controller.getDatabase().close();
            controller.getDatabase().open();

            controller.getDatabase().updateImportDate(studyId);

        } catch (Exception e) {
            e.printStackTrace();
            fail = true;

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

        if (fail | uniqueFail | mFieldFile.hasSpecialCharacters()) {
            controller.getDatabase().deleteField(result);
            SharedPreferences.Editor ed = preferences.edit();
            ed.putString(GeneralKeys.FIELD_FILE, null);
            ed.putBoolean(GeneralKeys.IMPORT_FIELD_FINISHED, false);
            ed.apply();
        }

        if (containsDuplicates) {
            Utils.makeToast(context, context.getString(R.string.import_runnable_duplicates_skipped));
        }

        if (fieldNameExists) {
            Utils.makeToast(context, context.getString(R.string.import_runnable_field_name_exists));
        } else if (fail) {
            Utils.makeToast(context, context.getString(R.string.import_runnable_create_field_data_failed, lineFail));
            //makeToast(getString(R.string.import_error_general));
        } else if (uniqueFail && context != null) {
            Utils.makeToast(context,context.getString(R.string.import_error_unique));
        } else if (mFieldFile.hasSpecialCharacters()) {
            Utils.makeToast(context,context.getString(R.string.import_error_unique_characters_illegal));
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
        }
    }

    private boolean verifyUniqueColumn(FieldFileObject.FieldFileBase fieldFile) {
        HashMap<String, String> check = fieldFile.getColumnSet(idColPosition);
        if (check.isEmpty()) {
            return false;
        } else {
            return controller.getDatabase().checkUnique(check);
        }
    }
}
