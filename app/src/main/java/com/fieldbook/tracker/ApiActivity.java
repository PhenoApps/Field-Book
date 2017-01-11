package com.fieldbook.tracker;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Locale;

/**
 * API test Screen
 */
public class ApiActivity extends Activity {

    ListView settingsList;
    private SharedPreferences ep;

    TextView etResponse;

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
        setContentView(R.layout.api_test);

        ep = getSharedPreferences("Settings", 0);
        etResponse = (TextView) findViewById(R.id.etResponse);

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

        loadScreen();
    }

    private void loadScreen() {
        if(getActionBar()!=null) {
            getActionBar().setHomeButtonEnabled(true);
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }

        settingsList = (ListView) findViewById(R.id.myList);

        String[] items2 = new String[]{ "Get crops","Get programs","Get studies","Get all traits","Get observation units"}; //TODO cleanup

        settingsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> av, View arg1, int position, long arg3) {
                switch (position) {
                    case 0:
                        new HttpAsyncTask().execute("http://private-anon-33b7a6b43-brapi.apiary-mock.com/brapi/v1/crops");
                        break;
                    case 1:
                        new HttpAsyncTask().execute("http://private-anon-33b7a6b43-brapi.apiary-mock.com/brapi/v1/programs");
                        break;
                    case 2:
                        new HttpAsyncTask().execute("http://private-anon-33b7a6b43-brapi.apiary-mock.com/brapi/v1/studies?programId=1");
                        break;
                    case 3:
                        new HttpAsyncTask().execute("http://private-anon-33b7a6b43-brapi.apiary-mock.com/brapi/v1/traits");
                        break;
                    case 4:
                        new HttpAsyncTask().execute("http://private-anon-33b7a6b43-brapi.apiary-mock.com/brapi/v1/study/1/observationunits?observationLevel=plot&studyInstance=1");
                        break;
                }
            }
        });

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.listitem, items2);
        settingsList.setAdapter(adapter);

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
        InputStream inputStream = null;
        String result = "";
        try {

            // create HttpClient
            HttpClient httpclient = new DefaultHttpClient();

            // make GET request to the given URL
            HttpResponse httpResponse = httpclient.execute(new HttpGet(url));

            // receive response as inputStream
            inputStream = httpResponse.getEntity().getContent();

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
        String line = "";
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
        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(String result) {
            Toast.makeText(getBaseContext(), "Received!", Toast.LENGTH_LONG).show();
            etResponse.setText(formatString(result));
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