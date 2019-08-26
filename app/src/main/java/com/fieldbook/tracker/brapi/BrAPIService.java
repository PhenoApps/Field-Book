package com.fieldbook.tracker.brapi;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import androidx.arch.core.util.Function;

import com.fieldbook.tracker.DataHelper;
import com.fieldbook.tracker.fields.FieldObject;
import com.fieldbook.tracker.preferences.PreferencesActivity;
import com.fieldbook.tracker.traits.TraitObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.StudiesApi;
import io.swagger.client.model.ObservationUnit;
import io.swagger.client.model.ObservationUnitsResponse1;
import io.swagger.client.model.ObservationVariable;
import io.swagger.client.model.StudiesResponse;
import io.swagger.client.model.Study;
import io.swagger.client.model.StudyObservationVariablesResponse;
import io.swagger.client.model.StudyResponse;
import io.swagger.client.model.StudySummary;

public class BrAPIService {
    private Context context;
    private DataHelper dataHelper;
    private StudiesApi studiesApi;


    public BrAPIService(Context context, String brapiBaseURL) {
        this.context = context;
        this.dataHelper = new DataHelper(context);

        ApiClient apiClient = new ApiClient().setBasePath(brapiBaseURL);
        this.studiesApi = new StudiesApi(apiClient);

    }

    public void getStudies(final Function<List<BrapiStudySummary>, Void> function){
        try {

            BrapiApiCallBack<StudiesResponse> callback = new BrapiApiCallBack<StudiesResponse>() {
                @Override
                public void onSuccess(StudiesResponse studiesResponse, int i, Map<String, List<String>> map) {
                    final List<BrapiStudySummary> studies = new ArrayList<>();
                    final List<StudySummary> studySummaryList = studiesResponse.getResult().getData();
                    for(StudySummary studySummary: studySummaryList){
                        studies.add(mapStudy(studySummary));
                    }
                    ((Activity)context).runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            function.apply(studies);
                        }
                    });
                }
            };

            studiesApi.studiesGetAsync(
                    null,null, null,null,
                    null, null, null, null, null,
                    null, null, null, null,
                    0, 20, null, callback);

        } catch (ApiException e) {
            e.printStackTrace();
        }

    }

    public BrapiStudySummary mapStudy(StudySummary studySummary) {
        BrapiStudySummary study = new BrapiStudySummary();
        study.setStudyDbId(studySummary.getStudyDbId());
        study.setStudyName(studySummary.getStudyName());
        return study;
    }

    public void getStudyDetails(String studyDbId, final Function<BrapiStudyDetails, Void> function) {
        try {

            BrapiApiCallBack<StudyResponse> callback = new BrapiApiCallBack<StudyResponse>() {
                @Override
                public void onSuccess(StudyResponse studyResponse, int i, Map<String, List<String>> map) {
                    final BrapiStudyDetails study = mapStudy(studyResponse.getResult());
                    ((Activity)context).runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            function.apply(study);
                        }
                    });
                }
            };

            studiesApi.studiesStudyDbIdGetAsync(
                    studyDbId, null, callback);

        } catch (ApiException e) {
            e.printStackTrace();
        }
    }

    public BrapiStudyDetails mapStudy(Study study) {
        BrapiStudyDetails studyDetails = new BrapiStudyDetails();
        studyDetails.setStudyDbId(study.getStudyDbId());
        studyDetails.setStudyName(study.getStudyName());
        studyDetails.setCommonCropName(study.getCommonCropName());
        studyDetails.setStudyDescription(study.getStudyDescription());
        studyDetails.setStudyDescription(study.getLocation().getLocationName());
        return studyDetails;
    }

    public void getPlotDetails(final String studyDbId, final Function<BrapiStudyDetails, Void> function) {
        try {

            BrapiApiCallBack<ObservationUnitsResponse1> callback = new BrapiApiCallBack<ObservationUnitsResponse1>() {
                @Override
                public void onSuccess(ObservationUnitsResponse1 response, int i, Map<String, List<String>> map) {
                    final BrapiStudyDetails study = new BrapiStudyDetails();
                    study.setNumberOfPlots(response.getMetadata().getPagination().getTotalCount());
                    study.setAttributes(mapAttributes(response.getResult().getData().get(0)));
                    study.setValues(mapAttributeValues(study.getAttributes(), response.getResult().getData()));

                    ((Activity)context).runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            function.apply(study);
                        }
                    });
                }
            };
            studiesApi.studiesStudyDbIdObservationunitsGetAsync(
                    studyDbId, "plot", 0, 1000,
                    null, callback);
        } catch (ApiException e) {
            e.printStackTrace();
        }
    }

    private List<String> mapAttributes(ObservationUnit unit) {
        List<String> attributes = new ArrayList<>();
        if(checkField(unit.getX(), unit.getPositionCoordinateX()))
            attributes.add("Row");
        if(checkField(unit.getY(), unit.getPositionCoordinateY()))
            attributes.add("Column");
        if(checkField(unit.getBlockNumber()))
            attributes.add("Block");
        if(checkField(unit.getReplicate()))
            attributes.add("Replicate");
        if(checkField(unit.getEntryNumber(), unit.getEntryType()))
            attributes.add("Entry");
        if(checkField(unit.getPlotNumber(), unit.getObservationUnitName(), unit.getObservationUnitDbId()))
            attributes.add("Plot");
        if(checkField(unit.getPlantNumber(), unit.getObservationUnitName(), unit.getObservationUnitDbId()))
            attributes.add("Plant");
        if(checkField(unit.getGermplasmName(), unit.getGermplasmDbId()))
            attributes.add("Germplasm");
        if(checkField(unit.getPedigree()))
            attributes.add("Pedigree");

        return attributes;

    }

    private List<List<String>> mapAttributeValues(List<String> attributes, List<ObservationUnit> data) {
        List<List<String>> attributesTable = new ArrayList<>();
        for(ObservationUnit unit: data){
            List<String> dataRow = new ArrayList<>();
            for(String attribute: attributes){
                if (attribute.equalsIgnoreCase("Row"))
                    addAttributeDataItem(dataRow, unit.getX(), unit.getPositionCoordinateX());
                else if (attribute.equalsIgnoreCase("Column"))
                    addAttributeDataItem(dataRow, unit.getY(), unit.getPositionCoordinateY());
                else if (attribute.equalsIgnoreCase("Block"))
                    addAttributeDataItem(dataRow, unit.getBlockNumber());
                else if (attribute.equalsIgnoreCase("Replicate"))
                    addAttributeDataItem(dataRow, unit.getReplicate());
                else if (attribute.equalsIgnoreCase("Entry"))
                    addAttributeDataItem(dataRow, unit.getEntryNumber(), unit.getEntryType());
                else if (attribute.equalsIgnoreCase("Plot"))
                    addAttributeDataItem(dataRow, unit.getPlotNumber(), unit.getObservationUnitName(), unit.getObservationUnitDbId());
                else if (attribute.equalsIgnoreCase("Plant"))
                    addAttributeDataItem(dataRow, unit.getPlantNumber(), unit.getObservationUnitName(), unit.getObservationUnitDbId());
                else if (attribute.equalsIgnoreCase("Germplasm"))
                    addAttributeDataItem(dataRow, unit.getGermplasmName(), unit.getGermplasmDbId());
                else if (checkField(unit.getPedigree()) && attribute.equalsIgnoreCase("Pedigree"))
                    addAttributeDataItem(dataRow, unit.getPedigree());
            }
            attributesTable.add(dataRow);
        }

        return attributesTable;
    }

    private void addAttributeDataItem(List<String> dataRow, String ... values) {
        String goodValue = getPrioritizedValue(values);
        if (goodValue != null){
            dataRow.add(goodValue);
        }
    }

    private boolean checkField(String ... values) {
        return getPrioritizedValue(values) != null;
    }

    private String getPrioritizedValue(String... values) {
        String returnValue = null;
        for(String val: values){
            if(val != null && !val.isEmpty()){
                returnValue = val;
                break;
            }
        }
        return returnValue;
    }

    public void getTraits(final String studyDbId, final Function<BrapiStudyDetails, Void> function) {
        try {

            BrapiApiCallBack<StudyObservationVariablesResponse> callback = new BrapiApiCallBack<StudyObservationVariablesResponse>() {
                @Override
                public void onSuccess(StudyObservationVariablesResponse response, int i, Map<String, List<String>> map) {
                    final BrapiStudyDetails study = new BrapiStudyDetails();
                    study.setTraits(mapTraits(response.getResult().getData()));

                    ((Activity)context).runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            function.apply(study);
                        }
                    });
                }
            };
            studiesApi.studiesStudyDbIdObservationvariablesGetAsync(
                    studyDbId, 0, 200,
                    null, callback);
        } catch (ApiException e) {
            e.printStackTrace();
        }
    }

    private List<TraitObject> mapTraits(List<ObservationVariable> variables) {
        List<TraitObject> traits = new ArrayList<>();
        for(ObservationVariable var: variables){
            TraitObject trait = new TraitObject();
            trait.setDefaultValue(var.getDefaultValue());
            String synonym = var.getSynonyms().size() > 0 ? var.getSynonyms().get(0) : null;
            trait.setTrait(getPrioritizedValue(synonym, var.getName()));
            if(var.getTrait() != null) {
                trait.setDetails(var.getTrait().getDescription());
            }
            if(var.getScale() != null) {
                if(var.getScale().getValidValues() != null) {
                    trait.setMinimum(var.getScale().getValidValues().getMin().toString());
                    trait.setMaximum(var.getScale().getValidValues().getMax().toString());
                    trait.setCategories(buildCategoryList(var.getScale().getValidValues().getCategories()));
                }
                if(var.getScale().getDataType() != null) {
                    trait.setFormat(convertBrAPIDataType(var.getScale().getDataType().getValue()));
                }
            }
            trait.setVisible(true);
            trait.setRealPosition("");
            traits.add(trait);
        }
        return traits;
    }

    private String buildCategoryList(List<String> categories) {
        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < categories.size(); ++j) {
            sb.append(categories.get(j));
            if (j != categories.size() - 1) {
                sb.append("/");
            }
        }
        return sb.toString();
    }

    private String convertBrAPIDataType(String dataType) {
        switch (dataType){
            case "Code":
            case "Nominal":
                return "categorical";
            case "Date":
                return "date";
            case "Numerical":
            case "Ordinal":
            case "Duration":
                return "numeric";
            case "Text":
            default:
                return "text";
        }
    }

    public void saveStudyDetails(BrapiStudyDetails studyDetails) {
        FieldObject field = new FieldObject();
        field.setExp_name(studyDetails.getStudyName());
        field.setExp_alias(studyDetails.getStudyName());
        field.setExp_species(studyDetails.getCommonCropName());
        field.setCount(studyDetails.getNumberOfPlots().toString());
        field.setUnique_id("Plot");
        field.setPrimary_id("Row");
        field.setSecondary_id("Column");
        field.setExp_sort("Plot");
        int expId = dataHelper.createField(field, studyDetails.getAttributes());

        for(List<String> dataRow: studyDetails.getValues()) {
            dataHelper.createFieldData(expId, studyDetails.getAttributes(), dataRow);
        }

        for(TraitObject t : studyDetails.getTraits()){
            dataHelper.insertTraits(t);
        }
    }

    public static void authorizeBrAPI(SharedPreferences sharedPreferences, Activity activity) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(PreferencesActivity.BRAPI_TOKEN, null);
        editor.apply();

        try {
            String url = sharedPreferences.getString(PreferencesActivity.BRAPI_BASE_URL, "") + "/brapi/authorize?display_name=Field Book&return_url=fieldbook://";
            try {
                Uri uri = Uri.parse("googlechrome://navigate?url="+ url);
                Intent i = new Intent(Intent.ACTION_VIEW, uri);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(i);
            } catch (ActivityNotFoundException e) {
                Uri uri = Uri.parse(url);
                // Chrome is probably not installed
                // OR not selected as default browser OR if no Browser is selected as default browser
                Intent i = new Intent(Intent.ACTION_VIEW, uri);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(i);
            }
        } catch (Exception ex) {
            Log.e("BrAPI", "Error starting BrAPI auth", ex);
        }
    }
}
