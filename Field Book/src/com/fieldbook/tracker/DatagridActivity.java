package com.fieldbook.tracker;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

public class DatagridActivity extends Activity {

    private String plotId;
    private int previousView = 0;

    Cursor databaseData;
    public GridView gridView;
    ArrayAdapter<String> adapter;
    public ArrayList<String> ArrayofName = new ArrayList<String>();

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences ep = getSharedPreferences("Settings", 0);

        // Enforce internal language change
        String local = ep.getString("language", "en");
        String region = ep.getString("region", "");
        Locale locale2 = new Locale(local, region);
        Locale.setDefault(locale2);
        Configuration config2 = new Configuration();
        config2.locale = locale2;
        getBaseContext().getResources().updateConfiguration(config2, getBaseContext().getResources()
                .getDisplayMetrics());

        setContentView(R.layout.datagrid);
        setTitle(R.string.datagrid);

        gridView = (GridView) findViewById(R.id.tableLayout1);
        Button close = (Button) findViewById(R.id.closeBtn);

        gridViewTable();

        close.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

    }

    private void gridViewTable() {
        String[] columns = {"plot_id"};
        String[] traits = MainActivity.dt.getVisibleTrait();

        databaseData = MainActivity.dt.convertDatabaseToTable(columns, traits);
        databaseData.moveToPosition(-1);

        int rows = databaseData.getCount();
        final int cols = databaseData.getColumnCount();

        databaseData.moveToFirst();

        final String[] plotIdData = new String[rows];

        ArrayofName.add(columns[0]);

        Collections.addAll(ArrayofName, traits);

        // outer for loop
        for (int i = 0; i < rows; i++) {

            plotIdData[i] = databaseData.getString(databaseData.getColumnIndex(databaseData.getColumnName(0)));

            for (int j = 0; j < cols; j++) {

                if (!databaseData.isNull(j)) {
                    ArrayofName.add(databaseData.getString(j));
                } else {
                    ArrayofName.add("");
                }
            }
            databaseData.moveToNext();
        }

        if (adapter != null) {
            System.out.println("here");
            adapter.clear();
        }
        gridView.setNumColumns(cols);
        gridView.setVerticalSpacing(1);
        gridView.setHorizontalSpacing(1);
        //gridView.setVerticalScrollbarPosition(GridView.SCROLLBAR_POSITION_LEFT);
        gridView.setFastScrollEnabled(true);

        adapter = new ArrayAdapter<String>(this,
                R.layout.simple_list_item, ArrayofName);

        gridView.setAdapter(adapter);

        gridView.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v,
                                    int position, long id) {
                gridView.getChildAt(previousView).setBackgroundColor(Color.WHITE);
                previousView = position;
                v.setBackgroundColor(Color.rgb(0, 128, 0));
                try {
                    plotId = plotIdData[(position / cols) - 1];
                } catch (ArrayIndexOutOfBoundsException e) {
                    Log.e("Field Book", "" + e.getMessage());
                }
                makeToast(plotId);
            }
        });

        gridView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                //TODO go to trait as well as plotId

                plotId = plotIdData[(position / cols) - 1];
                makeToast(plotId);
                Intent returnIntent = new Intent();
                returnIntent.putExtra("result", plotId);
                setResult(RESULT_OK, returnIntent);
                finish();
                return false;
            }
        });

    }

    public void makeToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}