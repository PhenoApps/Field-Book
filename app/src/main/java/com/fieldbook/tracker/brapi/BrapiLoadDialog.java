package com.fieldbook.tracker.brapi;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.brapi.model.BrapiObservationLevel;
import com.fieldbook.tracker.brapi.model.BrapiStudyDetails;
import com.fieldbook.tracker.brapi.model.Observation;
import com.fieldbook.tracker.brapi.service.BrAPIService;
import com.fieldbook.tracker.brapi.service.BrAPIServiceFactory;
import com.fieldbook.tracker.brapi.service.BrapiPaginationManager;
import com.fieldbook.tracker.objects.FieldObject;
import com.fieldbook.tracker.objects.TraitObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BrapiLoadDialog extends DialogFragment {

    private Button saveBtn;
    private BrapiStudyDetails study;
    private BrapiStudyDetails studyDetails;
    private BrAPIService brAPIService;
    private Context context;
    private Boolean studyLoadStatus = false;
    private Boolean plotLoadStatus = false;
    private Boolean traitLoadStatus = false;
    private BrapiPaginationManager paginationManager;

    private static String EXTRA_FIELD_ID = "fieldId";

    // Creates a new thread to do importing
    private final Runnable importRunnable = new Runnable() {
        public void run() {
            new BrapiLoadDialog.ImportRunnableTask().execute(0);
        }
    };
    private BrapiObservationLevel selectedObservationLevel;
    private String selectedPrimary;
    private String selectedSecondary;
    private String selectedSort;

    private TextView studyNumPlotsLbl;
    private RelativeLayout loadingPanel;

    private TextView studyNameValue;
    private TextView studyDescriptionValue;
    private TextView studyLocationValue;
    private TextView studyNumPlotsValue;
    private TextView studyNumTraitsValue;
    private Spinner studyPrimaryKey;
    private Spinner studySecondaryKey;
    private Spinner studySortOrder;

    public BrapiLoadDialog() {
    }

    public static BrapiLoadDialog newInstance() {
        return new BrapiLoadDialog();
    }

    public void setSelectedStudy(BrapiStudyDetails selectedStudy) {
        this.study = selectedStudy;
    }

    public void setPaginationManager(BrapiPaginationManager paginationManager) {
        this.paginationManager = paginationManager;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        this.context = requireActivity();

        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.AppAlertDialog);

//        builder.setTitle(R.string.field_import_string);
        builder.setNegativeButton(R.string.dialog_cancel, (dialog, which) -> dialog.dismiss());

        builder.setPositiveButton(R.string.dialog_save, (dialog, which) -> {
                saveStudy();
                dialog.dismiss();
        });


        LayoutInflater inflater = this.getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_brapi_import, null);
        builder.setView(view);

        AlertDialog brapiLoadDialog = builder.create();
        brapiLoadDialog.show();


        brAPIService = BrAPIServiceFactory.getBrAPIService(this.context);

        saveBtn = brapiLoadDialog.getButton(AlertDialog.BUTTON_POSITIVE);

        studyNumPlotsLbl = view.findViewById(R.id.studyNumPlotsLbl);
        loadingPanel = view.findViewById(R.id.loadingPanel);
        studyNameValue = view.findViewById(R.id.studyNameValue);
        studyDescriptionValue = view.findViewById(R.id.studyDescriptionValue);
        studyLocationValue = view.findViewById(R.id.studyLocationValue);
        studyNumPlotsValue = view.findViewById(R.id.studyNumPlotsValue);
        studyNumTraitsValue = view.findViewById(R.id.studyNumTraitsValue);
        studyPrimaryKey = view.findViewById(R.id.studyPrimaryKey);
        studySecondaryKey = view.findViewById(R.id.studySecondaryKey);
        studySortOrder = view.findViewById(R.id.studySortOrder);

        return brapiLoadDialog;
    }

    @Override
    public void onStart() {
        super.onStart();
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
            studyNumPlotsLbl.setText(
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
        loadingPanel.setVisibility(View.VISIBLE);
        brAPIService.getStudyDetails(study.getStudyDbId(), study -> {

            ((Activity) context).runOnUiThread(() -> {
                BrapiStudyDetails.merge(studyDetails, study);
                loadStudy();
                // Check if user should save yet
                studyLoadStatus = true;
                if (checkAllLoadsFinished()) {
                    loadingPanel.setVisibility(View.GONE);
                    saveBtn.setVisibility(View.VISIBLE);
                    resetLoadStatus();
                }
            });
            return null;
        }, code -> {
            ((Activity) context).runOnUiThread(() -> {
                loadingPanel.setVisibility(View.GONE);
                Toast.makeText(context, context.getString(R.string.brapi_study_detail_error), Toast.LENGTH_LONG).show();
            });
            return null;
        });

        brAPIService.getPlotDetails(study.getStudyDbId(), selectedObservationLevel, study -> {

            ((Activity) context).runOnUiThread(() -> {
                BrapiStudyDetails.merge(studyDetails, study);
                loadStudy();
                // Check if user should save yet
                plotLoadStatus = true;
                if (checkAllLoadsFinished()) {
                    loadingPanel.setVisibility(View.GONE);
                    saveBtn.setVisibility(View.VISIBLE);
                    resetLoadStatus();
                }
            });
            return null;
        }, code -> {
            ((Activity) context).runOnUiThread(() -> {
                loadingPanel.setVisibility(View.GONE);
                new AlertDialog.Builder(context, R.style.AppAlertDialog)
                        .setTitle(R.string.dialog_save_error_title)
                        .setPositiveButton(org.phenoapps.androidlibrary.R.string.okButtonText, (dialogInterface, i) -> {
                            ((Activity) context).finish();
                        }).setMessage(R.string.brapi_plot_detail_error).create().show();
            });
            return null;
        });


        brAPIService.getTraits(study.getStudyDbId(), study -> {

            ((Activity) context).runOnUiThread(() -> {
                BrapiStudyDetails.merge(studyDetails, study);

                // This is BMS specific. Remove the traits that are not part of the selected Observation Level.
                // To ensure that only relevant traits are included in the imported study/field.
                studyDetails.getTraits().removeIf(t -> t.getObservationLevelNames() != null &&
                        t.getObservationLevelNames().stream().noneMatch(s -> s.equalsIgnoreCase(selectedObservationLevel.getObservationLevelName())));

                loadStudy();
                // Check if user should save yet
                traitLoadStatus = true;
                if (checkAllLoadsFinished()) {
                    loadingPanel.setVisibility(View.GONE);
                    saveBtn.setVisibility(View.VISIBLE);
                    resetLoadStatus();
                }
            });
            return null;
        }, code -> {
            ((Activity) context).runOnUiThread(() -> {
                // Allow load to continue even if Traits fail to load
                Toast.makeText(context, context.getString(R.string.brapi_study_traits_error), Toast.LENGTH_LONG).show();

                BrapiStudyDetails emptyTraits = new BrapiStudyDetails();
                emptyTraits.setTraits(new ArrayList<>());
                BrapiStudyDetails.merge(studyDetails, emptyTraits);
                loadStudy();
                // Check if user should save yet
                traitLoadStatus = true;
                if (checkAllLoadsFinished()) {
                    loadingPanel.setVisibility(View.GONE);
                    saveBtn.setVisibility(View.VISIBLE);
                    resetLoadStatus();
                }
            });
            return null;
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
        brAPIService.getTraits(study.getStudyDbId(), input -> {
            for(TraitObject obj : input.getTraits()) {
                System.out.println("Trait:" + obj.getName());
                System.out.println("ObsIds: " + obj.getExternalDbId());
                observationVariableDbIds.add(obj.getExternalDbId());
            }

            return null;
        }, input -> null);

        System.out.println("obsIds Size:"+observationVariableDbIds.size());
        brAPIService.getObservations(study.getStudyDbId(), observationVariableDbIds, paginationManager, input -> {
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
        }, input -> {
            System.out.println("Stopped:");
            return null;
        });
    }

    private void loadStudy() {
        if (this.studyDetails.getStudyName() != null)
            studyNameValue.setText(this.studyDetails.getStudyName());
        if (this.studyDetails.getStudyDescription() != null)
            studyDescriptionValue.setText(this.studyDetails.getStudyDescription());
        if (this.studyDetails.getStudyLocation() != null)
            studyLocationValue.setText(this.studyDetails.getStudyLocation());
        if (this.studyDetails.getNumberOfPlots() != null)
            studyNumPlotsValue.setText(this.studyDetails.getNumberOfPlots().toString());
        if (this.studyDetails.getTraits() != null)
           studyNumTraitsValue.setText(String.valueOf(this.studyDetails.getTraits().size()));


        if(this.studyDetails.getAttributes() != null && !this.studyDetails.getAttributes().isEmpty()) {
            ArrayAdapter<String> keyOptions = new ArrayAdapter<>(this.context,
                    android.R.layout.simple_spinner_dropdown_item, studyDetails.getAttributes());

            Spinner primary = studyPrimaryKey;
            primary.setAdapter(keyOptions);
            primary.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> adapterView, View view, int index, long id) {
                    List<String> attrs = studyDetails.getAttributes();
                    if (attrs != null && !attrs.isEmpty() && index < attrs.size()) {
                        selectedPrimary = attrs.get(index);
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> adapterView) {

                }
            });

            Spinner secondary = studySecondaryKey;
            secondary.setAdapter(keyOptions);
            secondary.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> adapterView, View view, int index, long id) {
                    List<String> attrs = studyDetails.getAttributes();
                    if (attrs != null && !attrs.isEmpty() && index < attrs.size()) {
                        selectedSecondary = attrs.get(index);
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> adapterView) {

                }
            });

            Spinner sort = studySortOrder;
            sort.setAdapter(keyOptions);
            sort.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> adapterView, View view, int index, long id) {
                    List<String> attrs = studyDetails.getAttributes();
                    if (attrs != null && !attrs.isEmpty() && index < attrs.size()) {
                        selectedSort = attrs.get(index);
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> adapterView) {

                }
            });

            if(studyDetails.getAttributes().contains("Row")) {
                primary.setSelection(studyDetails.getAttributes().indexOf("Row"));
            }
            if(studyDetails.getAttributes().contains("Column")) {
                secondary.setSelection(studyDetails.getAttributes().indexOf("Column"));
            }
            if(studyDetails.getAttributes().contains("Plot")) {
                sort.setSelection(studyDetails.getAttributes().indexOf("Plot"));
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

                brapiControllerResponse = brAPIService.saveStudyDetails(studyDetails, selectedObservationLevel, selectedPrimary, selectedSecondary, selectedSort);

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
                alertDialogBuilder = new AlertDialog.Builder(context, R.style.AppAlertDialog);
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
                alertDialogBuilder = new AlertDialog.Builder(context, R.style.AppAlertDialog);
                alertDialogBuilder.setTitle(R.string.dialog_save_error_title)
                        .setPositiveButton(org.phenoapps.androidlibrary.R.string.okButtonText, (dialogInterface, i) -> {
                            // Finish our BrAPI import activity
                            ((Activity) context).finish();
                        });
                alertDialogBuilder.setMessage(R.string.brapi_study_incompatible);
            }

            if(alertDialogBuilder == null) {
                // Finish our BrAPI import activity
                FieldObject field = (FieldObject) brapiControllerResponse.getData();
                Intent returnIntent = new Intent();
                returnIntent.putExtra(EXTRA_FIELD_ID, field.getStudyId());
                ((Activity) context).setResult(Activity.RESULT_OK, returnIntent);
                ((Activity) context).finish();
            } else {
                AlertDialog alertDialog = alertDialogBuilder.create();
                alertDialog.show();
            }
        }
    }
}
