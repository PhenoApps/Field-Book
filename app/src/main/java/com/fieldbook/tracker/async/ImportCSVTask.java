package com.fieldbook.tracker.async;

import android.app.ProgressDialog;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.Html;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.objects.FieldFileObject;
import com.fieldbook.tracker.objects.TraitObject;
import com.fieldbook.tracker.utilities.CSVReader;
import com.fieldbook.tracker.utilities.Utils;

import org.phenoapps.utils.BaseDocumentTreeUtil;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

public class ImportCSVTask extends AsyncTask<Integer, Integer, Integer> {
        ProgressDialog dialog;

        boolean fail;

        WeakReference<Context> ctx;

        DataHelper database;

        Uri file;

        OnPostExecuteCsv onPostExecuteFunction;

        public interface OnPostExecuteCsv {
            void onPostExecute();
        }

        public ImportCSVTask(Context ctx, DataHelper database, Uri file, OnPostExecuteCsv onPostExecute) {
            this.ctx = new WeakReference<>(ctx);
            this.file = file;
            this.database = database;
            this.onPostExecuteFunction = onPostExecute;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            Context c = ctx.get();
            dialog = new ProgressDialog(c);
            dialog.setIndeterminate(true);
            dialog.setCancelable(false);
            dialog.setMessage(Html
                    .fromHtml(c.getString(R.string.import_dialog_importing)));
            dialog.show();
        }

        @Override
        protected Integer doInBackground(Integer... params) {
            try {
                String[] data;
                String[] columns;

                // Use methods from FieldFileObject to get the file info
                FieldFileObject.FieldFileBase fileObject = FieldFileObject.create(ctx.get(), file, null, null);
                String sourceString = fileObject.getFileStem();

                InputStream inputStream = BaseDocumentTreeUtil.Companion.getUriInputStream(ctx.get(), this.file);
                InputStreamReader fr = new InputStreamReader(inputStream);

                CSVReader cr = new CSVReader(fr);

                columns = cr.readNext();

                data = columns;

                //get variable with largest real position
                Optional<TraitObject> maxPosition = database.getAllTraitObjects().stream()
                        .max(Comparator.comparingInt(TraitObject::getRealPosition));

                //by default start from zero
                int positionOffset = 0;

                //if there are other traits, set offset to the max
                if (maxPosition.isPresent()) {

                    try {

                        positionOffset = maxPosition.get().getRealPosition();

                    } catch (NoSuchElementException e) {

                        e.printStackTrace();

                    }
                }

                while (data != null) {
                    data = cr.readNext();

                    //if trait format or name is null then don't import
                    if (data != null && data.length > 1
                            && data[0] != null && data[1] != null) {
                        TraitObject t = new TraitObject();
                        String traitName = data[0];
                        t.setName(traitName);
                        t.setAlias(traitName);
                        t.setSynonyms(List.of(traitName));
                        t.setFormat(data[1]);
                        t.setDefaultValue(data[2]);
                        t.setMinimum(data[3]);
                        t.setMaximum(data[4]);
                        t.setDetails(data[5]);
                        t.setCategories(data[6]);
                        //t.visible = data[7].toLowerCase();
                        t.setRealPosition(positionOffset + Integer.parseInt(data[8]));
                        t.setVisible(data[7].equalsIgnoreCase("true"));
                        t.setTraitDataSource(sourceString);

                        if (t.getFormat().equals("multicat")) {
                            t.setFormat("categorical");
                            t.setAllowMulticat(true);
                        }
                        database.insertTraits(t);
                    }
                }

                try {
                    cr.close();
                } catch (Exception ignore) {
                }

                try {
                    fr.close();
                } catch (Exception ignore) {
                }

                try {
                    if (inputStream != null) {
                        inputStream.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                database.close();
                database.open();

            } catch (Exception e) {
                e.printStackTrace();
                fail = true;
            }

            return 0;
        }

        @Override
        protected void onPostExecute(Integer result) {

            this.onPostExecuteFunction.onPostExecute();

            if (dialog.isShowing()) {
                dialog.dismiss();
            }

            if (fail) {
                Utils.makeToast(ctx.get(), ctx.get().getString(R.string.import_error_general));
            }
        }
    }