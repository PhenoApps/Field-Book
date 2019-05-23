package com.fieldbook.tracker.brapi;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import androidx.arch.core.util.Function;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.fieldbook.tracker.DataHelper;
import com.fieldbook.tracker.fields.FieldObject;
import com.fieldbook.tracker.traits.TraitObject;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class BrAPIService {
    private String brapiBaseURL;
    private Context context;
    private RequestQueue queue;
    private DataHelper dataHelper;

    public BrAPIService(Context context, String brapiBaseURL) {
        this.context = context;
        this.brapiBaseURL = brapiBaseURL;
        this.queue = Volley.newRequestQueue(context);
        this.dataHelper = new DataHelper(context);
    }

    public void getStudies(final Function<List<StudySummary>, Void> function){
        String url = this.brapiBaseURL + "/studies-search?pageSize=20&page=0"; // BrAPI v1.2, try "/studies" for v1.3
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        List<StudySummary> studies = parseStudiesJson(response);
                        function.apply(studies);
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Toast.makeText(context.getApplicationContext(), "Error loading data", Toast.LENGTH_SHORT).show();

                Log.e("error", error.toString());
            }
        });

        queue.add(stringRequest);
    }

    public void getStudyDetails(String studyDbId, final Function<StudyDetails, Void> function) {

        String url = this.brapiBaseURL + "/studies/" + studyDbId;
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        StudyDetails study = parseStudyDetailsJson(response);
                        function.apply(study);
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Toast.makeText(context.getApplicationContext(), "Error loading data", Toast.LENGTH_SHORT).show();

                Log.e("error", error.toString());
            }
        });

        queue.add(stringRequest);
    }

    public void getPlotDetails(final String studyDbId, final Function<StudyDetails, Void> function) {
        String url = this.brapiBaseURL + "/studies/" + studyDbId + "/observationunits?observationLevel=plot&pageSize=1000&page=0";
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        StudyDetails studyDetails = new StudyDetails();
                        studyDetails.setAttributes(new ArrayList<String>());
                        studyDetails.setValues(new ArrayList<List<String>>());
                        parsePlotJson(response, studyDetails);

                        function.apply(studyDetails);

                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Toast.makeText(context.getApplicationContext(), "Error loading data", Toast.LENGTH_SHORT).show();
                Log.e("error", error.toString());
            }
        });

        queue.add(stringRequest);
    }

    public void getTraits(final String studyDbId, final Function<StudyDetails, Void> function) {
        String url = this.brapiBaseURL + "/studies/" + studyDbId + "/observationvariables?pageSize=200&page=0";
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        StudyDetails studyDetails = new StudyDetails();
                        List<TraitObject> traits = parseTraitsJson(response);
                        studyDetails.setTraits(traits);
                        function.apply(studyDetails);

                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Toast.makeText(context.getApplicationContext(), "Error loading data", Toast.LENGTH_SHORT).show();
                Log.e("error", error.toString());
            }
        });

        queue.add(stringRequest);
    }

    private List<StudySummary> parseStudiesJson(String json) {

        List<StudySummary> studies = new ArrayList<>();
        try {
            JSONObject js = new JSONObject(json);
            JSONObject result = (JSONObject) js.get("result");
            JSONArray data = (JSONArray) result.get("data");
            for (int i = 0; i < data.length(); ++i) {
                JSONObject studyJSON = data.getJSONObject(i);
                StudySummary studySummary = new StudySummary();
                //s.setStudyName(studyJSON.getString("studyName"));//Brapi v1.3
                studySummary.setStudyName(studyJSON.getString("name"));//Brapi v1.2
                studySummary.setStudyDbId(studyJSON.getString("studyDbId"));
                studies.add(studySummary);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return studies;
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
                t.defaultValue = tmp.getString("defaultValue");
                JSONObject traitJson = tmp.getJSONObject("trait");
                t.trait = traitJson.getString("name");
                t.details = traitJson.getString("description");
                JSONObject scale = tmp.getJSONObject("scale");

                JSONObject validValue = scale.getJSONObject("validValues");
                t.minimum = Integer.toString(validValue.getInt("min"));
                t.maximum = Integer.toString(validValue.getInt("max"));
                JSONArray cat = validValue.getJSONArray("categories");
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < cat.length(); ++j) {
                    sb.append(cat.get(j));
                    if (j != cat.length() - 1) {
                        sb.append("/");
                    }
                }
                t.categories = sb.toString();
                t.format = convertBrAPIDataType(scale.getString("dataType"));
                if (t.format.equals("integer")) {
                    t.format = "numeric";
                }
                t.visible = true;
                traits.add(t);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return traits;
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

    private void parsePlotJson(String json, StudyDetails studyDetails) {

        try {
            JSONObject js = new JSONObject(json);
            JSONObject result = (JSONObject) js.get("result");
            JSONArray data = (JSONArray) result.get("data");

            studyDetails.setNumberOfPlots(data.length());


            JSONObject first = (JSONObject) data.get(0);
            Iterator<String> firstIter = first.keys();
            while (firstIter.hasNext()) {
                String key = firstIter.next();
                if (!ignoreBrAPIAttribute(key)) {
                    studyDetails.getAttributes().add(key);
                }
            }
            Collections.sort(studyDetails.getAttributes());

            for (int i = 0; i < data.length(); ++i) {
                JSONObject unit = (JSONObject) data.get(i);
                List<String> dataRow = new ArrayList<>();
                for(String key: studyDetails.getAttributes()){
                    if(unit.has(key)){
                        dataRow.add(unit.getString(key));
                    }else{
                        dataRow.add("");
                    }
                }
                studyDetails.getValues().add(dataRow);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private boolean ignoreBrAPIAttribute(String key) {
        List<String> ignoredBrAPIAttributes = new ArrayList<>();
        ignoredBrAPIAttributes.add("trialDbId");
        ignoredBrAPIAttributes.add("trialName");
        ignoredBrAPIAttributes.add("treatments");
        ignoredBrAPIAttributes.add("studyName");
        ignoredBrAPIAttributes.add("studyDbId");
        ignoredBrAPIAttributes.add("studyLocation");
        ignoredBrAPIAttributes.add("studyLocationDbId");
        ignoredBrAPIAttributes.add("programName");
        ignoredBrAPIAttributes.add("programDbId");
        ignoredBrAPIAttributes.add("observations");
        ignoredBrAPIAttributes.add("observationUnitXref");
        ignoredBrAPIAttributes.add("locationName");
        ignoredBrAPIAttributes.add("locationDbId");
        return ignoredBrAPIAttributes.contains(key);
    }


    private StudyDetails parseStudyDetailsJson(String response) {

        StudyDetails studyDetails = new StudyDetails();
        try {
            JSONObject js = new JSONObject(response);
            JSONObject result = (JSONObject) js.get("result");
            if(result.has("studyDbId"))
                studyDetails.setStudyDbId(result.getString("studyDbId"));
            if(result.has("name"))
                studyDetails.setStudyName(result.getString("name")); // BrAPI v1.2
            if(result.has("studyName"))
                studyDetails.setStudyName(result.getString("studyName")); // BrAPI v1.3
            if(result.has("studyDescription"))
                studyDetails.setStudyDescription(result.getString("studyDescription"));
            if(result.has("locationName"))
                studyDetails.setStudyLocation(result.getString("locationName")); // BrAPI v1.2
            if(result.has("location")){
                JSONObject location = result.getJSONObject("location");
                if(location.has("name"))
                    studyDetails.setStudyLocation(location.getString("name")); // BrAPI v1.2
                if(location.has("locationName"))
                    studyDetails.setStudyLocation(location.getString("locationName")); // BrAPI v1.3
            }


        } catch (Exception e) {
            e.printStackTrace();
        }
        return studyDetails;
    }

    public void saveStudyDetails(StudyDetails studyDetails) {
        FieldObject field = new FieldObject();
        field.setExp_name(studyDetails.getStudyName());
        field.setExp_alias(studyDetails.getStudyName());
        field.setExp_species(studyDetails.getCommonCropName());
        field.setCount(studyDetails.getNumberOfPlots().toString());
        field.setUnique_id("observationUnitDbId");
        field.setPrimary_id("X");
        field.setSecondary_id("Y");
        field.setExp_sort("plotNumber");
        int expId = dataHelper.createField(field, studyDetails.getAttributes());

        for(List<String> dataRow: studyDetails.getValues()) {
            dataHelper.createFieldData(expId, studyDetails.getAttributes(), dataRow);
        }

        for(TraitObject t : studyDetails.getTraits()){
            dataHelper.insertTraits(t);
        }
    }
}
