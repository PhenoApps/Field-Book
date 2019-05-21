package com.fieldbook.tracker;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
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

public class DatagridActivity extends AppCompatActivity {

    private String plotId;
    private int previousView = 0;
    private SharedPreferences ep;

    Cursor databaseData;
    public GridView gridView;
    ArrayAdapter<String> adapter;
    public ArrayList<String> ArrayofName = new ArrayList<>();

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ep = getSharedPreferences("Settings", 0);

        setContentView(R.layout.activity_datagrid);
        setTitle(R.string.preferences_general_feature_datagrid);

        gridView = findViewById(R.id.tableLayout1);
        Button close = findViewById(R.id.closeBtn);
        close.setTransformationMethod(null);
        gridViewTable();

        close.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

    }

    private void gridViewTable() {
        String[] columns = {ep.getString("ImportUniqueName", "")};
        String[] traits = ConfigActivity.dt.getVisibleTrait();

        databaseData = ConfigActivity.dt.convertDatabaseToTable(columns, traits);
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

        adapter = new ArrayAdapter<>(this,
                R.layout.listitem_datagrid, ArrayofName);

        gridView.setAdapter(adapter);

        gridView.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v,
                                    int position, long id) {
                //TODO NullPointerException
                gridView.getChildAt(previousView).setBackgroundColor(Color.WHITE);
                previousView = position;
                v.setBackgroundColor(getResources().getColor(R.color.main_primary));
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
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }
}