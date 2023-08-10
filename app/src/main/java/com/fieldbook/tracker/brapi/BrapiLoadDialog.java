package com.fieldbook.tracker.brapi;

import android.app.Activity;
import androidx.appcompat.app.AlertDialog;
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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.arch.core.util.Function;

import com.fieldbook.tracker.brapi.model.BrapiObservationLevel;
import com.fieldbook.tracker.brapi.model.BrapiStudyDetails;
import com.fieldbook.tracker.brapi.model.Observation;
import com.fieldbook.tracker.brapi.service.BrAPIService;
import com.fieldbook.tracker.brapi.service.BrAPIServiceFactory;
import com.fieldbook.tracker.R;
import com.fieldbook.tracker.brapi.service.BrapiPaginationManager;
import com.fieldbook.tracker.objects.TraitObject;
import com.fieldbook.tracker.utilities.DialogUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BrapiLoadDialog extends Dialog implements android.view.View.OnClickListener {

    private Button saveBtn;
    private BrapiStudyDetails study;
    private BrapiStudyDetails studyDetails;
    private BrAPIService brAPIService;
    private Context context;
    private Boolean studyLoadStatus = false;
    private Boolean plotLoadStatus = false;
    private Boolean traitLoadStatus = false;
    private BrapiPaginationManager paginationManager;

    // Creates a new thread to do importing
    private Runnable importRunnable = new Runnable() {
        public void run() {
            new BrapiLoadDialog.ImportRunnableTask().execute(0);
        }
    };
    private BrapiObservationLevel selectedObservationLevel;
    private String selectedPrimary;
    private String selectedSecondary;

    public BrapiLoadDialog(@NonNull Context context) {
        super(context);
        this.context = context;
    }

    public void setSelectedStudy(BrapiStudyDetails selectedStudy) {
        this.study = selectedStudy;
    }

    public void setPaginationManager(BrapiPaginationManager paginationManager) {
        this.paginationManager = paginationManager;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setCanceledOnTouchOutside(false);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_brapi_import);
        brAPIService = BrAPIServiceFactory.getBrAPIService(this.context);

        saveBtn = findViewById(R.id.brapi_save_btn);
        saveBtn.setOnClickListener(this);
        Button cancelBtn = findViewById(R.id.brapi_cancel_btn);
        cancelBtn.setOnClickListener(this);
    }

    @Override
    protected void onStart(){
        // Set our OK button to be disabled until we are finished loading
        saveBtn.setVisibility(View.GONE);
        studyDetails = new BrapiStudyDetails();
        updateNumPlotsLabel();
        buildStudyDetails();
        //        loadObservations(); //Uncomment this if you want the app to sync the observations when field is loaded.
        loadStudy();
    }

    private void updateNumPlotsLabel() {
        if (selectedObservationLevel != null) {
            ((TextView) findViewById(R.id.studyNumPlotsLbl)).setText(
                    makePlural(this.selectedObservationLevel.getObservationLevelName()));
        }
    }

    private String makePlural(String observationLevelName) {
        if(Locale.getDefault().getLanguage().equals("en")) {
            StringBuilder plural = new StringBuilder(observationLevelName.substring(0, 1).toUpperCase());
            if (observationLevelName.endsWith("y")) {
                plural.append(observationLevelName.substring(1, observationLevelName.length() - 1)).append("ies");
            } else {
                plural.append(observationLevelName.substring(1)).append("s");
            }

            return plural.toString();
        } else {
            return observationLevelName;
        }
    }

    private void buildStudyDetails() {
        findViewById(R.id.loadingPanel).setVisibility(View.VISIBLE);
        brAPIService.getStudyDetails(study.getStudyDbId(), new Function<BrapiStudyDetails, Void>() {
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
        }, new Function<Integer, Void>() {

            @Override
            public Void apply(final Integer code) {
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

        brAPIService.getPlotDetails(study.getStudyDbId(), selectedObservationLevel, new Function<BrapiStudyDetails, Void>() {
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

        }, new Function<Integer, Void>() {

            @Override
            public Void apply(final Integer code) {
                ((Activity) context).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        findViewById(R.id.loadingPanel).setVisibility(View.GONE);
                        new AlertDialog.Builder(context).setTitle(R.string.dialog_save_error_title)
                                .setPositiveButton(R.string.okButtonText, (dialogInterface, i) -> {
                                    ((Activity) context).finish();
                                }).setMessage(R.string.brapi_plot_detail_error).create().show();
                    }
                });
                return null;
            }
        });


        brAPIService.getTraits(study.getStudyDbId(), new Function<BrapiStudyDetails, Void>() {
            @Override
            public Void apply(final BrapiStudyDetails study) {

                ((Activity) context).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        BrapiStudyDetails.merge(studyDetails, study);

                        // This is BMS specific. Remove the traits that are not part of the selected Observation Level.
                        // To ensure that only relevant traits are included in the imported study/field.
                        studyDetails.getTraits().removeIf(t -> t.getObservationLevelNames() != null &&
                                t.getObservationLevelNames().stream().noneMatch(s -> s.equalsIgnoreCase(selectedObservationLevel.getObservationLevelName())));

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
        }, new Function<Integer, Void>() {

            @Override
            public Void apply(final Integer code) {
                ((Activity) context).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // Allow load to continue even if Traits fail to load
                        Toast.makeText(context, context.getString(R.string.brapi_study_traits_error), Toast.LENGTH_LONG).show();

                        BrapiStudyDetails emptyTraits = new BrapiStudyDetails();
                        emptyTraits.setTraits(new ArrayList<>());
                        BrapiStudyDetails.merge(studyDetails, emptyTraits);
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
        });
    }

    /**
     * Function to load the observations.
     *
     * TODO: Decide if we want to delete.  It is likely a better approach to use the BrapiSyncObsDialog as it gives the user more control
     */
    private void loadObservations() {
        System.out.println("Study DBId: "+study.getStudyDbId());

        List<String> observationVariableDbIds = new ArrayList<String>();

        //Trying to get the traits as well:
        brAPIService.getTraits(study.getStudyDbId(), new Function<BrapiStudyDetails, Void>() {
            @Override
            public Void apply(BrapiStudyDetails input) {
                for(TraitObject obj : input.getTraits()) {
                    System.out.println("Trait:"+obj.getTrait());
                    System.out.println("ObsIds: "+obj.getExternalDbId());
                    observationVariableDbIds.add(obj.getExternalDbId());
                }

                return null;
            }
        }, new Function<Integer, Void>() {
            @Override
            public Void apply(Integer input) {
                return null;
            }
        });

        System.out.println("obsIds Size:"+observationVariableDbIds.size());
        brAPIService.getObservations(study.getStudyDbId(), observationVariableDbIds, paginationManager, new Function<List<Observation>, Void>() {
            @Override
            public Void apply(List<Observation> input) {
                study.setObservations(input);
                BrapiStudyDetails.merge(studyDetails, study);
                System.out.println("StudyId: " + study.getStudyDbId());
                System.out.println("StudyName: " + study.getStudyName());
                for(Observation obs : input) {

                    System.out.println("***************************");
                    System.out.println("StudyId: "+obs.getStudyId());
                    System.out.println("ObsId: "+obs.getDbId());
                    System.out.println("UnitDbId: "+obs.getUnitDbId());

                    System.out.println("VariableDbId: "+obs.getVariableDbId());
                    System.out.println("VariableName: "+obs.getVariableName());
                    System.out.println("Value: "+obs.getValue());
                }
                return null;
            }
        }, new Function<Integer, Void>() {
            @Override
            public Void apply(Integer input) {
                System.out.println("Stopped:");
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


        if(this.studyDetails.getAttributes() != null && !this.studyDetails.getAttributes().isEmpty()) {
            ArrayAdapter<String> keyOptions = new ArrayAdapter<String>(this.context,
                    android.R.layout.simple_spinner_dropdown_item, studyDetails.getAttributes());

            Spinner primary = findViewById(R.id.studyPrimaryKey);
            primary.setAdapter(keyOptions);
            primary.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> adapterView, View view, int index, long id) {
                    selectedPrimary = studyDetails.getAttributes().get(index);
                }

                @Override
                public void onNothingSelected(AdapterView<?> adapterView) {

                }
            });

            Spinner secondary = findViewById(R.id.studySecondaryKey);
            secondary.setAdapter(keyOptions);
            secondary.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> adapterView, View view, int index, long id) {
                    selectedSecondary = studyDetails.getAttributes().get(index);
                }

                @Override
                public void onNothingSelected(AdapterView<?> adapterView) {

                }
            });

            if(studyDetails.getAttributes().contains("Row") || studyDetails.getAttributes().contains("Column")) {
                primary.setSelection(studyDetails.getAttributes().indexOf("Row"));
                secondary.setSelection(studyDetails.getAttributes().indexOf("Column"));
            }
        }
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

    public void setObservationLevel(BrapiObservationLevel selectedObservationLevel) {
        this.selectedObservationLevel = selectedObservationLevel;
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

                brapiControllerResponse = brAPIService.saveStudyDetails(studyDetails, selectedObservationLevel, selectedPrimary, selectedSecondary);

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

            AlertDialog.Builder alertDialogBuilder = null;
            // Display our message.
            if (brapiControllerResponse != null && !brapiControllerResponse.status) {
                alertDialogBuilder = new AlertDialog.Builder(context);
                alertDialogBuilder.setTitle(R.string.dialog_save_error_title)
                        .setPositiveButton(R.string.dialog_ok, (dialogInterface, i) -> {
                            // Finish our BrAPI import activity
                            ((Activity) context).finish();
                        });

                if (brapiControllerResponse.message.equals(BrAPIService.notUniqueFieldMessage)) {
                    alertDialogBuilder.setMessage(R.string.fields_study_and_obs_exists_message);
                } else if (brapiControllerResponse.message.equals(BrAPIService.notUniqueIdMessage)) {
                    alertDialogBuilder.setMessage(R.string.import_error_unique);
                } else if (brapiControllerResponse.message.equals(BrAPIService.noPlots)) {
                    alertDialogBuilder.setMessage(R.string.act_collect_no_plots);
                } else {
                    Log.e("error-ope", brapiControllerResponse.message);
                    alertDialogBuilder.setMessage(R.string.brapi_save_field_error);
                }
            }

            // This is an unhandled failed that we should not run into unless there is
            // an error in the saveStudyDetails code outside of that handling.
            if (fail) {
                if (brapiControllerResponse != null) {
                    Log.e("error-opef", brapiControllerResponse.message);

                } else {
                    Log.e("error-opef", "unknown");
                }
                alertDialogBuilder = new AlertDialog.Builder(context);
                alertDialogBuilder.setTitle(R.string.dialog_save_error_title)
                        .setPositiveButton(R.string.okButtonText, (dialogInterface, i) -> {
                            // Finish our BrAPI import activity
                            ((Activity) context).finish();
                        });
                alertDialogBuilder.setMessage(R.string.brapi_study_incompatible);
            }

            if(alertDialogBuilder == null) {
                // Finish our BrAPI import activity
                ((Activity) context).finish();
            } else {
                AlertDialog alertDialog = alertDialogBuilder.create();
                alertDialog.show();
                DialogUtils.styleDialogs(alertDialog);
            }

        }
    }

}
