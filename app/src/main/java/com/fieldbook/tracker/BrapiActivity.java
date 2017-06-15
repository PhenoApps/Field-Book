package com.fieldbook.tracker;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.Configuration;
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * API test Screen
 */
public class BrapiActivity extends AppCompatActivity {

    ListView settingsList;
    ListView resultsList;

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

        // Enforce internal language change
        String local = ep.getString("language", Locale.getDefault().getCountry());
        String region = ep.getString("region",Locale.getDefault().getLanguage());

        Locale locale2 = new Locale(local, region);
        Locale.setDefault(locale2);
        Configuration config2 = new Configuration();
        config2.locale = locale2;
        getBaseContext().getResources().updateConfiguration(config2,
                getBaseContext().getResources().getDisplayMetrics());

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        getSupportActionBar().setTitle(null);
        getSupportActionBar().getThemedContext();
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);


        loadScreen();
    }

    private void loadScreen() {
        settingsList = (ListView) findViewById(R.id.myList);

        String[] items2 = new String[]{ "Get crops","Get studies","Get all traits"};

        settingsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
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
        settingsList.setAdapter(adapter);
    }

    private void createList(String[] items3) {
        resultsList = (ListView) findViewById(R.id.myList2);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.listitem, items3);
        resultsList.setAdapter(adapter);
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
}