package com.fieldbook.tracker.brapi;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.arch.core.util.Function;

import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.R;
import com.fieldbook.tracker.preferences.GeneralKeys;
import com.fieldbook.tracker.utilities.Constants;

import io.swagger.client.ApiException;

public class BrapiLoadDialog extends Dialog implements android.view.View.OnClickListener {

    private Button saveBtn;
    private BrapiStudySummary study;
    private BrapiStudyDetails studyDetails;
    private BrAPIService brAPIService;
    private Context context;
    private Boolean studyLoadStatus = false;
    private Boolean plotLoadStatus = false;
    private Boolean traitLoadStatus = false;
    // Creates a new thread to do importing
    private Runnable importRunnable = new Runnable() {
        public void run() {
            new BrapiLoadDialog.ImportRunnableTask().execute(0);
        }
    };

    public BrapiLoadDialog(@NonNull Context context) {
        super(context);
        this.context = context;
    }

    public void setSelectedStudy(BrapiStudySummary selectedStudy) {
        this.study = selectedStudy;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setCanceledOnTouchOutside(false);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_brapi_import);

        String brapiBaseURL = this.context.getSharedPreferences("Settings", 0)
                .getString(GeneralKeys.BRAPI_BASE_URL, "") + Constants.BRAPI_PATH;
        brAPIService = new BrAPIService(brapiBaseURL, new DataHelper(this.context));
        saveBtn = findViewById(R.id.brapi_save_btn);
        saveBtn.setOnClickListener(this);
        Button cancelBtn = findViewById(R.id.brapi_cancel_btn);
        cancelBtn.setOnClickListener(this);
        studyDetails = new BrapiStudyDetails();

        // Set our OK button to be disabled until we are finished loading
        saveBtn.setVisibility(View.GONE);
        buildStudyDetails();
        loadStudy();
    }

    private void buildStudyDetails() {

        final String brapiToken = BrAPIService.getBrapiToken(this.context);

        findViewById(R.id.loadingPanel).setVisibility(View.VISIBLE);
        brAPIService.getStudyDetails(brapiToken, study.getStudyDbId(), new Function<BrapiStudyDetails, Void>() {
            @Override
            public Void apply(final BrapiStudyDetails study) {

                ((Activity) context).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        BrapiStudyDetails.merge(studyDetails, study);
                        loadStudy();
                        // Check if user should save yet
                        studyLoadStatus = true;
                        if (checkAllLoadsFinished()) {
                            findViewById(R.id.loadingPanel).setVisibility(View.GONE);
                            saveBtn.setVisibility(View.VISIBLE);
                            resetLoadStatus();
                        }
                    }
                });
                return null;
            }
        }, new Function<ApiException, Void>() {

            @Override
            public Void apply(final ApiException error) {
                ((Activity) context).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        findViewById(R.id.loadingPanel).setVisibility(View.GONE);
                        Toast.makeText(context, context.getString(R.string.brapi_study_detail_error), Toast.LENGTH_LONG).show();
                    }
                });
                return null;
            }
        });


        brAPIService.getPlotDetails(brapiToken, study.getStudyDbId(), new Function<BrapiStudyDetails, Void>() {
            @Override
            public Void apply(final BrapiStudyDetails study) {

                ((Activity) context).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        BrapiStudyDetails.merge(studyDetails, study);
                        loadStudy();
                        // Check if user should save yet
                        plotLoadStatus = true;
                        if (checkAllLoadsFinished()) {
                            findViewById(R.id.loadingPanel).setVisibility(View.GONE);
                            saveBtn.setVisibility(View.VISIBLE);
                            resetLoadStatus();
                        }
                    }
                });
                return null;
            }

        }, new Function<ApiException, Void>() {

            @Override
            public Void apply(final ApiException error) {
                ((Activity) context).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        findViewById(R.id.loadingPanel).setVisibility(View.GONE);
                        Toast.makeText(context, context.getString(R.string.brapi_plot_detail_error), Toast.LENGTH_LONG).show();
                    }
                });
                return null;
            }
        });


        brAPIService.getTraits(brapiToken, study.getStudyDbId(), new Function<BrapiStudyDetails, Void>() {
            @Override
            public Void apply(final BrapiStudyDetails study) {

                ((Activity) context).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        BrapiStudyDetails.merge(studyDetails, study);
                        loadStudy();
                        // Check if user should save yet
                        traitLoadStatus = true;
                        if (checkAllLoadsFinished()) {
                            findViewById(R.id.loadingPanel).setVisibility(View.GONE);
                            saveBtn.setVisibility(View.VISIBLE);
                            resetLoadStatus();
                        }
                    }
                });
                return null;
            }
        }, new Function<ApiException, Void>() {

            @Override
            public Void apply(final ApiException error) {
                ((Activity) context).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        findViewById(R.id.loadingPanel).setVisibility(View.GONE);
                        Toast.makeText(context, context.getString(R.string.brapi_study_traits_error), Toast.LENGTH_LONG).show();
                    }
                });
                return null;
            }
        });
    }

    private void loadStudy() {
        if (this.studyDetails.getStudyName() != null)
            ((TextView) findViewById(R.id.studyNameValue)).setText(this.studyDetails.getStudyName());
        if (this.studyDetails.getStudyDescription() != null)
            ((TextView) findViewById(R.id.studyDescriptionValue)).setText(this.studyDetails.getStudyDescription());
        if (this.studyDetails.getStudyLocation() != null)
            ((TextView) findViewById(R.id.studyLocationValue)).setText(this.studyDetails.getStudyLocation());
        if (this.studyDetails.getNumberOfPlots() != null)
            ((TextView) findViewById(R.id.studyNumPlotsValue)).setText(this.studyDetails.getNumberOfPlots().toString());
        if (this.studyDetails.getTraits() != null)
            ((TextView) findViewById(R.id.studyNumTraitsValue)).setText(String.valueOf(this.studyDetails.getTraits().size()));
    }

    private Boolean checkAllLoadsFinished() {
        return studyLoadStatus && plotLoadStatus && traitLoadStatus;
    }

    private void resetLoadStatus() {
        studyLoadStatus = false;
        traitLoadStatus = false;
        plotLoadStatus = false;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.brapi_save_btn:
                saveStudy();
                break;
            case R.id.brapi_cancel_btn:
                dismiss();
                break;
            default:
                break;
        }
        dismiss();
    }

    private void saveStudy() {

        // Dismiss this dialog
        dismiss();

        // Run saving task in the background so we can showing progress dialog
        Handler mHandler = new Handler();
        mHandler.post(importRunnable);

    }

    // Mimics the class used in the csv field importer to run the saving
    // task in a different thread from the UI thread so the app doesn't freeze up.
    private class ImportRunnableTask extends AsyncTask<Integer, Integer, Integer> {

        ProgressDialog dialog;

        BrapiControllerResponse brapiControllerResponse;
        boolean fail;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            dialog = new ProgressDialog(context);
            dialog.setIndeterminate(true);
            dialog.setCancelable(false);
            dialog.setMessage(Html.fromHtml(context.getResources().getString(R.string.import_dialog_importing)));
            dialog.show();
        }

        @Override
        protected Integer doInBackground(Integer... params) {
            try {

                brapiControllerResponse = brAPIService.saveStudyDetails(studyDetails);

            } catch (Exception e) {
                e.printStackTrace();
                fail = true;
            }

            return 0;
        }

        @Override
        protected void onPostExecute(Integer result) {
            if (dialog.isShowing())
                dialog.dismiss();

            // Finish our BrAPI import activity
            ((Activity) context).finish();

            // Display our message.
            if (!brapiControllerResponse.status) {
                if (brapiControllerResponse.message == BrAPIService.notUniqueFieldMessage) {
                    Toast.makeText(context, R.string.fields_study_exists_message, Toast.LENGTH_LONG).show();
                } else if (brapiControllerResponse.message == BrAPIService.notUniqueIdMessage) {
                    Toast.makeText(context, R.string.import_error_unique, Toast.LENGTH_LONG).show();
                } else {
                    Log.e("error", brapiControllerResponse.message);
                    Toast.makeText(context, R.string.brapi_save_field_error, Toast.LENGTH_LONG).show();
                }
            }

            // This is an unhandled failed that we should not run into unless there is
            // an error in the saveStudyDetails code outside of that handling.
            if (fail) {
                Log.e("error", brapiControllerResponse.message);
                Toast.makeText(context, R.string.brapi_save_field_error, Toast.LENGTH_LONG).show();
            }

        }
    }

}
