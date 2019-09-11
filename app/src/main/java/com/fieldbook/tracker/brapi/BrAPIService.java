package com.fieldbook.tracker.brapi;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import androidx.arch.core.util.Function;

import com.android.volley.AuthFailureError;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.Volley;
import com.fieldbook.tracker.DataHelper;
import com.fieldbook.tracker.fields.FieldObject;
import com.fieldbook.tracker.preferences.PreferencesActivity;
import com.fieldbook.tracker.traits.TraitObject;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.StudiesApi;
import io.swagger.client.api.PhenotypesApi;
import io.swagger.client.api.ObservationVariablesApi;
import io.swagger.client.model.Observation;
import io.swagger.client.model.ObservationUnit;
import io.swagger.client.model.ObservationUnitsResponse1;
import io.swagger.client.model.ObservationVariable;
import io.swagger.client.model.ObservationVariableResponse;
import io.swagger.client.model.ObservationVariablesResponse;
import io.swagger.client.model.PhenotypesRequest;
import io.swagger.client.model.PhenotypesRequestData;
import io.swagger.client.model.PhenotypesRequestObservation;
import io.swagger.client.model.StudiesResponse;
import io.swagger.client.model.NewObservationDbIdsResponse;
import io.swagger.client.model.Study;
import io.swagger.client.model.StudyObservationVariablesResponse;
import io.swagger.client.model.StudyResponse;
import io.swagger.client.model.StudySummary;
import io.swagger.client.model.WSMIMEDataTypes;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import org.threeten.bp.OffsetDateTime;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

public class BrAPIService {
    private Context context;
    private DataHelper dataHelper;
    private StudiesApi studiesApi;
    private PhenotypesApi phenotypesApi;
    private ObservationVariablesApi traitsApi;
    private String brapiBaseURL;
    private RequestQueue queue;


    public BrAPIService(Context context, String brapiBaseURL) {
        this.context = context;
        this.dataHelper = new DataHelper(context);
        this.brapiBaseURL = brapiBaseURL;
        this.queue = Volley.newRequestQueue(context);

        ApiClient apiClient = new ApiClient().setBasePath(brapiBaseURL);
        this.studiesApi = new StudiesApi(apiClient);
        this.traitsApi = new ObservationVariablesApi(apiClient);
        this.phenotypesApi = new PhenotypesApi(apiClient);

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

        // We always add the observationUnitDbId and observationName
        attributes.add("observationUnitDbId");
        attributes.add("observationUnitName");

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

            // ObservationUnitDbId and observationUnitName will always go in the attributes
            addAttributeDataItem(dataRow, unit.getObservationUnitDbId());
            addAttributeDataItem(dataRow, unit.getObservationUnitName());

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

    // Get the ontology from breedbase so the users can select the ontology
    /*public void getOntology(final Function< List<TraitObject>, Void > function) {

        //TODO: Need to add the ability to change the page
        String url = this.brapiBaseURL + "/variables?pageSize=50&page=0";
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        // Parse the response

                        //TODO: Replace this class and parse function when Pete releases
                        // his brapi java library
                        List<TraitObject> traits = parseTraitsJson(response);
                        function.apply(traits);
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Toast.makeText(context.getApplicationContext(), "Error loading data", Toast.LENGTH_SHORT).show();
                        Log.e("error", error.toString());
                    }
                });
        queue.add(stringRequest);
    }*/

    public void getOntology(final Function<List<TraitObject>, Void> function) {
        try {

            BrapiApiCallBack<ObservationVariablesResponse> callback = new BrapiApiCallBack<ObservationVariablesResponse>() {
                @Override
                public void onSuccess(ObservationVariablesResponse response, int i, Map<String, List<String>> map) {

                    // Result contains a list of observation variables
                    List<ObservationVariable> brapiTraitList = response.getResult().getData();
                    final List<TraitObject> traitsList = mapTraits(brapiTraitList);

                    ((Activity)context).runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            function.apply(traitsList);
                        }
                    });
                }
            };

            traitsApi.variablesGetAsync(0, 50, null, null,
                    null, callback);

        } catch (ApiException e) {
            Toast.makeText(context.getApplicationContext(), "Error loading data", Toast.LENGTH_SHORT).show();
            Log.e("error", e.toString());
        }
    }

    private List<TraitObject> parseTraitsJson(String json) {
        List<TraitObject> traits = new ArrayList<>();

        try {
            JSONObject js = new JSONObject(json);
            JSONObject result = (JSONObject) js.get("result");
            JSONArray data = (JSONArray) result.get("data");
            for (int i = 0; i < data.length(); ++i) {
                JSONObject tmp = data.getJSONObject(i);
                TraitObject t = new TraitObject();
                t.setDefaultValue(tmp.getString("defaultValue"));
                JSONObject traitJson = tmp.getJSONObject("trait");
                t.setTrait(traitJson.getString("name"));
                t.setDetails(traitJson.getString("description"));
                JSONObject scale = tmp.getJSONObject("scale");

                JSONObject validValue = scale.getJSONObject("validValues");
                //TODO: Add integer parsing to get min and max as integers
                // Requires changes to breedbase as well
                t.setMinimum(validValue.getString("min"));
                t.setMaximum(validValue.getString("max"));
                JSONArray cat = validValue.getJSONArray("categories");
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < cat.length(); ++j) {
                    sb.append(cat.get(j));
                    if (j != cat.length() - 1) {
                        sb.append("/");
                    }
                }
                t.setCategories(sb.toString());
                //TODO: datatype field should be dataType. Breedbase needs to be fixed.
                t.setFormat(convertBrAPIDataType(scale.getString("datatype")));
                if (t.getFormat().equals("integer")) {
                    t.setFormat("numeric");
                }

                // Get database id of external system to sync to enabled pushing through brAPI
                t.setExternalDbId(tmp.getString("observationVariableDbId"));

                // Need to set where we are getting the data from so we don't push to a different
                // external link than where the trait was retrieved from.
                Integer url_path_start = this.brapiBaseURL.indexOf("/brapi", 0);
                t.setTraitDataSource(this.brapiBaseURL.substring(0, url_path_start));

                t.setVisible(true);
                traits.add(t);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return traits;
    }

    public void postPhenotypes() {
        try {

            BrapiApiCallBack<NewObservationDbIdsResponse> callback = new BrapiApiCallBack<NewObservationDbIdsResponse>() {
                @Override
                public void onSuccess(NewObservationDbIdsResponse phenotypesResponse, int i, Map<String, List<String>> map) {
                    // TODO: reponse processing
                }
            };

            // TODO: get from db once working
            PhenotypesRequestObservation observation = new PhenotypesRequestObservation();
            observation.setCollector("Nick Field Book");
            observation.setObservationDbId(""); // new entry only for now
            OffsetDateTime time = OffsetDateTime.now();
            observation.setObservationTimeStamp(time);
            observation.setObservationVariableDbId("MO_123:100002");
            observation.setObservationVariableName("Plant Height");
            observation.season("Spring 2018");
            observation.setValue("1");

            PhenotypesRequestData phenotype = new PhenotypesRequestData();
            phenotype.addObservationsItem(observation);
            phenotype.setObservatioUnitDbId("1");
            phenotype.setStudyDbId("1001");

            PhenotypesRequest request = new PhenotypesRequest();
            request.addDataItem(phenotype);

            phenotypesApi.phenotypesPostAsync(request, null,"Bearer YYYY", callback);

        } catch (ApiException e) {
            e.printStackTrace();
        }

    }

    /*
    public void postPhenotypes() {
        String url = this.brapiBaseURL + "/phenotypes";

        List<Map<String, String>> data = dataHelper.getDataBrapiExport();
        // TODO: group by studyid and group observations in json

        JSONObject request = new JSONObject();
        JSONArray jsonData = new JSONArray();

        try {
            for (Map<String, String> observation : data) {

                JSONObject observationJson = new JSONObject();
                observationJson.put("collector", "NickFieldBook"); //TODO: get user profile name
                observationJson.put("observationDbId", ""); // TODO: handle updates, not just new


                SimpleDateFormat timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ",
                        Locale.getDefault());
                Date time = timeStamp.parse(observation.get("timeTaken"));
                String iso8601Time = TimestampUtils.getISO8601StringForDate(time);


                observationJson.put("observationTimeStamp", iso8601Time);
                observationJson.put("observationUnitDbId", observation.get("observationUnitDbId"));
                observationJson.put("observationVariableDbId", "MO_123:100002"); // TODO: get this from somewhere
                observationJson.put("observationVariableName", "Plant Height"); // TODO: get this from somewhere
                observationJson.put("season", "Spring 2018"); // Needs to be two words to work around BrAPI test server bug
                observationJson.put("value", observation.get("userValue"));
                JSONArray observations = new JSONArray();
                observations.put(observationJson);
                JSONObject observationStudy = new JSONObject();
                observationStudy.put("studyDbId", observation.get("exp_alias"));
                observationStudy.put("observatioUnitDbId", "1"); // TODO: get this from somewhere
                observationStudy.put("observations", observations);
                jsonData.put(observationStudy);
            }

            request.put("data", jsonData);
            Log.d("json", request.toString());


        }catch (JSONException e){
            e.printStackTrace();
        }catch (ParseException e) {
            e.printStackTrace();
        }

        JsonObjectRequest putObservationsRequest = new JsonObjectRequest(Request.Method.POST, url, request,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        //TODO: verify that response indicates everything was written
                        //TODO: update observationDId for observations in database
                        Toast.makeText(context.getApplicationContext(), "BrAPI Export Successful", Toast.LENGTH_SHORT).show();
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Toast.makeText(context.getApplicationContext(), "BrAPI Export Failed", Toast.LENGTH_SHORT).show();
                        Log.e("error", error.toString());
                    }
                })
        {
            @Override
            public Map<String, String> getHeaders () throws AuthFailureError {
                HashMap<String, String> headers = new HashMap<String, String>();
                headers.put("Content-Type", "application/json");
                headers.put("Accept", "application/json");
                headers.put("Authorization", "Bearer YYYY");
                return headers;
            }
        };
        queue.add(putObservationsRequest);
    }
    */

    // dummy data test for now
    public void putStudyObservations() {
        final String studyDbId = "1001";
        String url = this.brapiBaseURL + "/studies/" + studyDbId + "/observations";

        // Send dummy data to test server creating new observations
        // TODO: Populate with actual collected data from database
        JSONObject request = new JSONObject();
        JSONArray observations = new JSONArray();
        JSONObject observation0 = new JSONObject();
        JSONObject observation1 = new JSONObject();

        try{
            observation0.put("collector", "NickFieldBook");
            observation0.put("observationDbId", "");
            observation0.put("observationTimeStamp", "2019-08-21T21:37:08.888Z");
            observation0.put("observationUnitDbId", "1");
            observation0.put("observationVariableDbId", "MO_123:100002");
            observation0.put("value", "5");

            observation1.put("collector", "NickFieldBook");
            observation1.put("observationDbId", "");
            observation1.put("observationTimeStamp", "2019-08-21T21:37:08.888Z");
            observation1.put("observationUnitDbId", "1");
            observation1.put("observationVariableDbId", "MO_123:100002");
            observation1.put("value", "666");

            observations.put(observation0);
            observations.put(observation1);
            request.put("observations", observations);

            Log.d("json", observations.toString());
        }catch (JSONException e){
            e.printStackTrace();
        }

        JsonObjectRequest putObservationsRequest = new JsonObjectRequest(Request.Method.PUT, url, request,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    //TODO: verify that response indicates everything was written
                    //TODO: update observationDId for observations in database
                    Toast.makeText(context.getApplicationContext(), "BrAPI Export Successful", Toast.LENGTH_SHORT).show();
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Toast.makeText(context.getApplicationContext(), "BrAPI Export Failed", Toast.LENGTH_SHORT).show();
                    Log.e("error", error.toString());
                }
            })
            {
                @Override
                public Map<String, String> getHeaders () throws AuthFailureError {
                    HashMap<String, String> headers = new HashMap<String, String>();
                    headers.put("Content-Type", "application/json");
                    headers.put("Accept", "application/json");
                    headers.put("Authorization", "Bearer YYYY");
                    return headers;
                }
            };
        queue.add(putObservationsRequest);
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

            // Get the synonyms for easier reading. Set it as the trait name.
            String synonym = var.getSynonyms().size() > 0 ? var.getSynonyms().get(0) : null;
            trait.setTrait(getPrioritizedValue(synonym, var.getName()));

            trait.setDetails(var.getTrait().getDescription());
            // Get database id of external system to sync to enabled pushing through brAPI
            trait.setExternalDbId(var.getObservationVariableDbId());

            // Need to set where we are getting the data from so we don't push to a different
            // external link than where the trait was retrieved from.
            if (getHostUrl() != null) {
                trait.setTraitDataSource(getHostUrl());
            }
            else {
                //TODO: Don't let this keep going
                trait.setTraitDataSource(null);
            }

            // Parse out the scale of the variable
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

            // Set some config variables in fieldbook
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
        field.setExp_alias(studyDetails.getStudyDbId()); //hack for now to get in table alias not used for anything
        field.setExp_species(studyDetails.getCommonCropName());
        field.setCount(studyDetails.getNumberOfPlots().toString());
        if (getHostUrl() != null) {
            field.setExp_source(getHostUrl());
        }
        else {
            //TODO: Don't let this go through
            field.setExp_source(null);
        }

        field.setUnique_id("Plot");
        field.setPrimary_id("Row");
        field.setSecondary_id("Column");
        field.setExp_sort("Plot");

        // Get our host url

        int expId = dataHelper.createField(field, studyDetails.getAttributes());

        for(List<String> dataRow: studyDetails.getValues()) {
            dataHelper.createFieldData(expId, studyDetails.getAttributes(), dataRow);
        }

        // Get the traits already associated with this study
        //TODO: Traits likely need to be made more field specific if we are to use this.
        // Or give them the ability to delete the existing traits when we import these ones.
        for(TraitObject t : studyDetails.getTraits()) {
            dataHelper.insertTraits(t);
        }
    }

    public String getHostUrl() {

        try {
            URL externalUrl = new URL(this.brapiBaseURL);
            return externalUrl.getHost();
        }
        catch (MalformedURLException e) {
            Toast.makeText(context.getApplicationContext(), "Cannot get host of data endpoint.", Toast.LENGTH_SHORT).show();
            Log.e("error", e.toString());
            return null;
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
