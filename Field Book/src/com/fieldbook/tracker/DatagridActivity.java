package com.fieldbook.tracker;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.SimpleCursorAdapter;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.TableRow.LayoutParams;
import android.widget.Toast;

import com.fieldbook.tracker.Search.SearchAdapter;
import com.fieldbook.tracker.Search.SearchData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

public class DatagridActivity extends Activity {

    private SharedPreferences ep;

    private String local;
    private String region;
    private Button close;
    private String plotId;
    private boolean selMade;
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

        ep = getSharedPreferences("Settings", 0);

        // Enforce internal language change
        local = ep.getString("language", "en");
        region = ep.getString("region", "");
        Locale locale2 = new Locale(local, region);
        Locale.setDefault(locale2);
        Configuration config2 = new Configuration();
        config2.locale = locale2;
        getBaseContext().getResources().updateConfiguration(config2, getBaseContext().getResources()
                .getDisplayMetrics());

        setContentView(R.layout.datagrid);
        setTitle(R.string.datagrid);

        gridView = (GridView) findViewById(R.id.tableLayout1);
        close = (Button) findViewById(R.id.closeBtn);

        gridViewTable();

        close.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent returnIntent = new Intent();
                returnIntent.putExtra("result", plotId);
                setResult(RESULT_OK, returnIntent);
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

        for(int i=0; i<traits.length;i++) {
            ArrayofName.add(traits[i]);
        }


        // outer for loop
        for (int i = 0; i < rows; i++) {

            plotIdData[i] = databaseData.getString( databaseData.getColumnIndex(databaseData.getColumnName(0)));

            for (int j = 0; j < cols; j++) {

                if (!databaseData.isNull(j)) {
                    ArrayofName.add(databaseData.getString(j));
                } else {
                    ArrayofName.add("");
                }
            }
            databaseData.moveToNext();
        }

        if(adapter!=null) {
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
                v.setBackgroundColor(Color.rgb(0,128,0));

                plotId =  plotIdData[(position/cols)-1];
                makeToast(plotId);
            }
        });

    }

    public void makeToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

}