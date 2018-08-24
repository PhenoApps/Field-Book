package com.fieldbook.tracker;

import android.app.Activity;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.fieldbook.tracker.fields.FieldObject;
import com.fieldbook.tracker.traits.TraitObject;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * API test Screen
 */
public class BrapiActivity extends AppCompatActivity {

    ListView listSetting, listResults, listStudies;

    private List<String> studies;
    private List<String> attributes;
    private List<String> values;
    private FieldObject field = null;
    private BrapiUrl urls;
    private DataHelper dataHelper;

    public static Activity thisActivity;

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_brapi);

        SharedPreferences ep = getSharedPreferences("Settings", 0);

        thisActivity = this;

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        getSupportActionBar().setTitle(null);
        getSupportActionBar().getThemedContext();
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        studies = new ArrayList<>();
        attributes = new ArrayList<>();
        values = new ArrayList<>();
        urls = new BrapiUrl();
        getData(urls.getStudiesURL(), "studies");
        dataHelper = new DataHelper(this);

        loadScreen();
    }

    private void loadScreen() {
        listSetting = findViewById(R.id.myList);

        String[] items2 = new String[]{ "Get crops","Get studies","Get all traits"};

        listSetting.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> av, View arg1, int position, long arg3) {
                switch (position) {
                    case 0:
                        new HttpAsyncTask().execute("https://private-anon-cf3f621d16-brapi.apiary-mock.com/brapi/v1/crops");
                        break;
                    case 1:
                        new HttpAsyncTask().execute("https://private-anon-cf3f621d16-brapi.apiary-mock.com/brapi/v1/studies-search");
                        break;
                    case 2:
                        new HttpAsyncTask().execute("https://private-anon-cf3f621d16-brapi.apiary-mock.com/brapi/v1/studies/ST012/observationVariables");
                        break;
                }
            }
        });

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.listitem, items2);
        listSetting.setAdapter(adapter);


        listStudies = findViewById(R.id.brapiStudies);
        listStudies.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                String selected = studies.get(position);
                urls.setStudies(selected);
                getData(urls.getTraitURL(), "trait");
                getData(urls.getExperimentURL(), "exp");
                getData(urls.getPlotsURL(), "plot");
            }
        });

    }

    public void buttonClicked(View view) {

        switch(view.getId()) {
            case R.id.loadStudies:
                ArrayAdapter<String> itemsAdapter = new ArrayAdapter<String>(thisActivity, android.R.layout.simple_list_item_1, studies);
                listStudies.setAdapter(itemsAdapter);
                break;
            case R.id.save:
                if (field != null && attributes.size() > 0) {
                    int expId = dataHelper.createField(field, attributes);
                    dataHelper.createFieldData(expId, attributes, values);
                }
                break;
        }
    }

    private void getData(String url, final String name) {

        RequestQueue queue = Volley.newRequestQueue(this);
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {

                        if (name.equals("exp")) {
                            field = parseExpJson(response);

                        } else if (name.equals("plot")) {
                            parsePlotJson(response, attributes, values);
                        } else if (name.equals("trait")) {
                            List<TraitObject> list = parseTraitsJson(response);
                            for (int i = 0; i < list.size(); ++i) {
                                dataHelper.insertTraits(list.get(i));
                            }
                        } else if (name.equals("studies")) {
                            parseStudiesJson(response);
                        }

                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e("error", error.toString());
            }
        });

        queue.add(stringRequest);
    }

    private void createList(String[] items3) {
        listResults = findViewById(R.id.myList2);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.listitem, items3);
        listResults.setAdapter(adapter);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    public static String GET(String url){
        InputStream inputStream;
        String result = "";
        try {

            URL urlObj = new URL(url);
            HttpURLConnection urlConnection = (HttpURLConnection) urlObj.openConnection();
            inputStream = urlConnection.getInputStream();

            // convert inputstream to string
            if(inputStream != null)
                result = convertInputStreamToString(inputStream);
            else
                result = "Did not work!";

        } catch (Exception e) {
            Log.d("InputStream", e.getLocalizedMessage());
        }

        return result;
    }

    private static String convertInputStreamToString(InputStream inputStream) throws IOException {
        BufferedReader bufferedReader = new BufferedReader( new InputStreamReader(inputStream));
        String line;
        String result = "";
        while((line = bufferedReader.readLine()) != null)
            result += line;

        inputStream.close();
        return result;

    }

    public boolean isConnected(){
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Activity.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected())
            return true;
        else
            return false;
    }

    private class HttpAsyncTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... urls) {

            return GET(urls[0]);
        }

        @Override
        protected void onPostExecute(String result) {
            try {
                JSONObject jsonObj = new JSONObject(result);
                JSONObject results = jsonObj.getJSONObject("result");
                JSONArray data = results.getJSONArray("data");

                String[] crops=new String[data.length()];
                for(int i=0; i<crops.length; i++) {
                    crops[i]=formatString(data.optString(i));
                }

                createList(crops);
            } catch (JSONException e) {
                e.printStackTrace();
            }

        }
    }

    public static String formatString(String text){
        StringBuilder json = new StringBuilder();
        String indentString = "";

        for (int i = 0; i < text.length(); i++) {
            char letter = text.charAt(i);
            switch (letter) {
                case '{':
                case '[':
                    json.append("\n" + indentString + letter + "\n");
                    indentString = indentString + "\t";
                    json.append(indentString);
                    break;
                case '}':
                case ']':
                    indentString = indentString.replaceFirst("\t", "");
                    json.append("\n" + indentString + letter);
                    break;
                case ',':
                    json.append(letter + "\n" + indentString);
                    break;

                default:
                    json.append(letter);
                    break;
            }
        }

        return json.toString();
    }

    private void parseStudiesJson(String json) {

        try {
            JSONObject js = new JSONObject(json);
            JSONObject result = (JSONObject) js.get("result");
            JSONArray data = (JSONArray) result.get("data");
            for (int i = 0; i < data.length(); ++i) {
                JSONObject tmp = data.getJSONObject(i);
                String id = tmp.getString("studyDbId");
                studies.add(id);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //when save this ExpId object to field book database,
    // it will return an exp_id, and it used for all plots records.
    private FieldObject parseExpJson(String json) {

        try {
            JSONObject js = new JSONObject(json);
            JSONObject result = (JSONObject)js.get("result");
            String studyName = result.getString("studyName");
            return new FieldObject(studyName);
        } catch(Exception e) {
            e.printStackTrace();
        }
        return null;
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
                t.format = scale.getString("dataType");
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

    private void parsePlotJson(String json, List<String> attributes, List<String> values) {

        try {
            JSONObject js = new JSONObject(json);
            JSONObject result = (JSONObject) js.get("result");
            JSONArray data = (JSONArray) result.get("data");

            for (int i = 0; i < data.length(); ++i) {
                JSONObject tmp = (JSONObject) data.get(i);
                //Plots plot = new Plots();

                Iterator<String> it = tmp.keys();
                while (it.hasNext()) {
                    String key = it.next();
                    String value = tmp.getString(key);
                    if (!key.equals("observationUnitXref") && !key.equals("observations")) {

                        if (i == 0) { //We only need to store attributes name one time
                            attributes.add(key);
                        }
                        values.add(value);
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}