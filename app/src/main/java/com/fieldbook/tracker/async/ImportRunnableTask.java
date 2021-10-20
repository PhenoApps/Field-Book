package com.fieldbook.tracker.async;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.text.Html;
import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.CollectActivity;
import com.fieldbook.tracker.activities.ConfigActivity;
import com.fieldbook.tracker.activities.FieldEditorActivity;
import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.objects.FieldFileObject;
import com.fieldbook.tracker.objects.FieldObject;
import com.fieldbook.tracker.utilities.Utils;
import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;

public class ImportRunnableTask extends AsyncTask<Integer, Integer, Integer> {
    ProgressDialog dialog;

    WeakReference<Context> mContext;
    FieldFileObject.FieldFileBase mFieldFile;
    String unique, primary, secondary;
    int idColPosition;
    SharedPreferences mPrefs;

    boolean fail;
    boolean uniqueFail;

    public ImportRunnableTask(Context context, FieldFileObject.FieldFileBase fieldFile,
                              int idColPosition, String unique, String primary, String secondary) {

        mFieldFile = fieldFile;

        mContext = new WeakReference<>(context);

        this.mPrefs = context.getSharedPreferences("Settings", Context.MODE_PRIVATE);
        this.idColPosition = idColPosition;
        this.unique = unique;
        this.primary = primary;
        this.secondary = secondary;
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

        int exp_id = -1;

        try {
            if (!verifyUniqueColumn(mFieldFile)) {
                uniqueFail = true;
                return 0;
            }

            if (mFieldFile.hasSpecialCharasters()) {
                return 0;
            }

            mFieldFile.open();
            String[] data;
            String[] columns = mFieldFile.readNext();
            ArrayList<String> nonEmptyColumns = new ArrayList<>();
            ArrayList<Integer> nonEmptyIndices = new ArrayList<>();

            //match and delete special characters from header line
            for (int i = 0; i < columns.length; i++) {

                String header = columns[i];

                if (DataHelper.hasSpecialChars(header)) {
                    columns[i] = DataHelper.replaceSpecialChars(header);

                }

                if (!columns[i].isEmpty()) {
                    nonEmptyColumns.add(columns[i]);
                    nonEmptyIndices.add(i);
                }
            }

            FieldObject f = mFieldFile.createFieldObject();
            f.setUnique_id(unique);
            f.setPrimary_id(primary);
            f.setSecondary_id(secondary);

            exp_id = ConfigActivity.dt.createField(f, nonEmptyColumns);

            DataHelper.db.beginTransaction();

            try {
                while (true) {
                    data = mFieldFile.readNext();
                    if (data == null)
                        break;

                    ArrayList<String> nonEmptyData = new ArrayList<>();
                    for (int j = 0; j < data.length; j++) {
                        if (nonEmptyIndices.contains(j)) {
                            nonEmptyData.add(data[j]);
                        }
                    }
                    ConfigActivity.dt.createFieldData(exp_id, nonEmptyColumns, nonEmptyData);
                }

                DataHelper.db.setTransactionSuccessful();
            } finally {
                DataHelper.db.endTransaction();
            }

            mFieldFile.close();

            ConfigActivity.dt.close();
            ConfigActivity.dt.open();

            File newDir = new File(mFieldFile.getPath());
            newDir.mkdirs();

            ConfigActivity.dt.updateExpTable(true, false, false, exp_id);

        } catch (Exception e) {
            e.printStackTrace();
            fail = true;

            ConfigActivity.dt.close();
            ConfigActivity.dt.open();
        }

        return exp_id;
    }

    @Override
    protected void onPostExecute(Integer result) {

        Context context = mContext.get();

        if (dialog.isShowing())
            dialog.dismiss();

        if (fail | uniqueFail | mFieldFile.hasSpecialCharasters()) {
            ConfigActivity.dt.deleteField(result);
            SharedPreferences.Editor ed = mPrefs.edit();
            ed.putString("FieldFile", null);
            ed.putBoolean("ImportFieldFinished", false);
            ed.apply();
        }
        if (fail) {
            //makeToast(getString(R.string.import_error_general));
        } else if (uniqueFail && context != null) {
            Utils.makeToast(context,context.getString(R.string.import_error_unique));
        } else if (mFieldFile.hasSpecialCharasters()) {
            Utils.makeToast(context,context.getString(R.string.import_error_unique_characters_illegal));
        } else {
            SharedPreferences.Editor ed = mPrefs.edit();

            ed.putString("ImportUniqueName", unique);
            ed.putString("ImportFirstName", primary);
            ed.putString("ImportSecondName", secondary);
            ed.putBoolean("ImportFieldFinished", true);
            ed.putInt("SelectedFieldExpId", result);

            ed.apply();

            CollectActivity.reloadData = true;
            FieldEditorActivity.loadData();

            ConfigActivity.dt.open();
            ConfigActivity.dt.switchField(result);
        }
    }

    private boolean verifyUniqueColumn(FieldFileObject.FieldFileBase fieldFile) {
        HashMap<String, String> check = fieldFile.getColumnSet(idColPosition);
        if (check.isEmpty()) {
            return false;
        } else {
            return ConfigActivity.dt.checkUnique(check);
        }
    }
}
