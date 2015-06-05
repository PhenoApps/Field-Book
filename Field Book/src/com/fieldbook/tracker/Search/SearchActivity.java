package com.fieldbook.tracker.Search;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.DatabaseUtils;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.fieldbook.tracker.MainActivity;
import com.fieldbook.tracker.R;

import java.util.Arrays;
import java.util.Locale;

public class SearchActivity extends Activity {
    private SharedPreferences ep;

    private LinearLayout parent;
    private static String TAG = "Field Book";
    private int rangeUntil;

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ep = getSharedPreferences("Settings", 0);

        // Enforce internal language change
        String local = ep.getString("language", "en");
        String region = ep.getString("region", "");
        Locale locale2 = new Locale(local, region);
        Locale.setDefault(locale2);
        Configuration config2 = new Configuration();
        config2.locale = locale2;
        getBaseContext().getResources().updateConfiguration(config2, getBaseContext().getResources()
                .getDisplayMetrics());

        setContentView(R.layout.search);

        setTitle(R.string.search);

        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.width = WindowManager.LayoutParams.MATCH_PARENT;

        this.getWindow().setAttributes(params);

        parent = (LinearLayout) findViewById(R.id.rowParent);

        Button start = (Button) findViewById(R.id.startBtn);
        Button close = (Button) findViewById(R.id.closeBtn);
        Button clear = (Button) findViewById(R.id.clearBtn);
        Button add = (Button) findViewById(R.id.addBtn);

        start.setOnClickListener(new OnClickListener() {

            public void onClick(View arg0) {

                try {
                    // Create the sql query based on user selection

                    String sql1 = "select range.id, range." + ep.getString("ImportFirstName", "") + ", range." + ep.getString("ImportSecondName", "") + " from range where range.id is not null ";
                    String sql2 = "select range.id, range." + ep.getString("ImportFirstName", "") + ", range." + ep.getString("ImportSecondName", "") + " from traits, range, user_traits where user_traits.rid = range." + ep.getString("ImportUniqueName", "") + " and user_traits.parent = traits.trait and user_traits.trait = traits.format ";

                    String sql = "";

                    boolean threeTables = false;

                    for (int i = 0; i < parent.getChildCount(); i++) {
                        LinearLayout child = (LinearLayout) parent.getChildAt(i);

                        EditText t = (EditText) child.findViewById(R.id.searchText);

                        Spinner c = (Spinner) child.findViewById(R.id.columns);
                        Spinner s = (Spinner) child.findViewById(R.id.like);

                        String value = "";
                        String prefix;

                        boolean before;

                        if (c.getSelectedItemPosition() < rangeUntil) {
                            before = true;
                            prefix = "range.";
                        } else {
                            before = false;
                            threeTables = true;
                            prefix = "user_traits.parent='";
                        }

                        // This is to prevent crashes when the user uses special characters
                        String trunc = DatabaseUtils.sqlEscapeString(t.getText().toString());

                        // We only want to escape the string, but not the encapsulating "'"
                        // For example 'plot\'s', we only want plot\'s
                        if (trunc.length() > 3)
                            trunc = trunc.substring(1, trunc.length() - 2);

                        switch (s.getSelectedItemPosition()) {

                            // 0: Equals to
                            case 0:
                                if (before)
                                    value = prefix + c.getSelectedItem().toString() + " = " + DatabaseUtils.sqlEscapeString(t.getText().toString()) + "";
                                else
                                    value = prefix + c.getSelectedItem().toString() + "' and userValue = " + DatabaseUtils.sqlEscapeString(t.getText().toString()) + "";
                                break;

                            // 1: Not equals to
                            case 1:
                                if (before)
                                    value = prefix + c.getSelectedItem().toString() + " != " + DatabaseUtils.sqlEscapeString(t.getText().toString()) + "";
                                else
                                    value = prefix + c.getSelectedItem().toString() + "' and userValue != " + DatabaseUtils.sqlEscapeString(t.getText().toString()) + "";
                                break;

                            // 2: Is Like
                            case 2:
                                if (before)
                                    value = prefix + c.getSelectedItem().toString() + " like " + DatabaseUtils.sqlEscapeString("%" + t.getText().toString() + "%") + "";
                                else
                                    value = prefix + c.getSelectedItem().toString() + "' and user_traits.userValue like " + DatabaseUtils.sqlEscapeString("%" + t.getText().toString() + "%") + "";
                                break;

                            // 3: Not is like
                            case 3:
                                if (before)
                                    value = prefix + c.getSelectedItem().toString() + " not like " + DatabaseUtils.sqlEscapeString("%" + t.getText().toString() + "%") + "";
                                else
                                    value = prefix + c.getSelectedItem().toString() + "' and user_traits.userValue not like " + DatabaseUtils.sqlEscapeString("%" + t.getText().toString() + "%") + "";
                                break;

                            // 4: More than
                            case 4:
                                if (before)
                                    value = prefix + c.getSelectedItem().toString() + " > " + trunc;
                                else
                                    value = prefix + c.getSelectedItem().toString() + "' and userValue > " + trunc;
                                break;

                            // 5: less than
                            case 5:
                                if (before)
                                    value = prefix + c.getSelectedItem().toString() + " < " + trunc;
                                else
                                    value = prefix + c.getSelectedItem().toString() + "' and userValue < " + trunc;
                                break;

                        }

                        if (i == parent.getChildCount() - 1)
                            sql += "and " + value + " ";
                        else
                            sql += "and " + value + " ";

                    }

                    if (threeTables)
                        sql = sql2 + sql;
                    else
                        sql = sql1 + sql;

                    final SearchData[] data = MainActivity.dt.getRangeBySql(sql);

                    final Dialog resultDialog = new Dialog(SearchActivity.this, android.R.style.Theme_Holo_Light_Dialog);
                    resultDialog.setContentView(R.layout.searchlist);
                    resultDialog.setTitle(getString(R.string.results));
                    resultDialog.setCancelable(true);
                    resultDialog.setCanceledOnTouchOutside(true);

                    TextView primaryTitle = (TextView) resultDialog.findViewById(R.id.range);
                    TextView secondaryTitle = (TextView) resultDialog.findViewById(R.id.plot);

                    primaryTitle.setText(ep.getString("ImportFirstName", getString(R.string.range)));
                    secondaryTitle.setText(ep.getString("ImportSecondName", getString(R.string.plot)));

                    WindowManager.LayoutParams params = resultDialog.getWindow().getAttributes();
                    params.height = WindowManager.LayoutParams.WRAP_CONTENT;
                    params.width = WindowManager.LayoutParams.MATCH_PARENT;

                    resultDialog.getWindow().setAttributes(params);

                    ListView myList = (ListView) resultDialog.findViewById(R.id.myList);
                    Button closeBtn = (Button) resultDialog.findViewById(R.id.closeBtn);

                    myList.setOnItemClickListener(new OnItemClickListener() {

                        public void onItemClick(AdapterView<?> arg0, View arg1,
                                                int position, long arg3) {

                            // When you click on an item, send the data back to the main screen
                            MainActivity.searchRange = data[position].range;
                            MainActivity.searchPlot = data[position].plot;
                            MainActivity.searchReload = true;

                            resultDialog.dismiss();
                            finish();
                        }
                    });

                    resultDialog.setOnCancelListener(new OnCancelListener() {

                        public void onCancel(DialogInterface arg0) {
                            finish();
                        }
                    });

                    closeBtn.setOnClickListener(new OnClickListener() {

                        public void onClick(View arg0) {
                            resultDialog.dismiss();
                            finish();
                        }
                    });

                    // If search has results, show them, otherwise display error message
                    if (data != null) {
                        myList.setAdapter(new SearchAdapter(SearchActivity.this, data));

                        resultDialog.show();
                    } else {
                        Toast.makeText(SearchActivity.this, getString(R.string.searchempty), Toast.LENGTH_LONG).show();
                    }
                } catch (Exception z) {
                    Log.e(TAG, "" +z.getMessage());
                }
            }
        });

        close.setOnClickListener(new OnClickListener() {

            public void onClick(View arg0) {
                finish();
            }
        });

        // Reset search to default
        clear.setOnClickListener(new OnClickListener() {

            public void onClick(View arg0) {
                parent.removeAllViews();

                addRow("", ep.getString("ImportFirstName", ""));

            }
        });

        // Add new search row
        add.setOnClickListener(new OnClickListener() {

            public void onClick(View arg0) {
                addRow("", "");
            }
        });

        addRow("", ep.getString("ImportFirstName", ""));
    }

    // Helper function to add row
    public void addRow(String text, String colName) {
        LayoutInflater vi = (LayoutInflater) getApplicationContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View v = vi.inflate(R.layout.searchrow, null);

        Spinner c = (Spinner) v.findViewById(R.id.columns);
        Spinner s = (Spinner) v.findViewById(R.id.like);

        EditText e = (EditText) v.findViewById(R.id.searchText);

        String[] likes = new String[6];

        likes[0] = getString(R.string.equalsto);
        likes[1] = getString(R.string.notequalsto);
        likes[2] = getString(R.string.islike);
        likes[3] = getString(R.string.notislike);
        likes[4] = getString(R.string.morethan);
        likes[5] = getString(R.string.lessthan);

        ArrayAdapter adapter = new ArrayAdapter(SearchActivity.this, R.layout.spinnerlayout, likes);
        s.setAdapter(adapter);

        String[] col = MainActivity.dt.getRangeColumns();

        if (col != null) {
            rangeUntil = col.length;

            ArrayAdapter adapter2 = new ArrayAdapter(SearchActivity.this, R.layout.spinnerlayout,
                    concat(col, MainActivity.dt.getVisibleTrait()));

            c.setAdapter(adapter2);

            if (text.length() > 0)
                e.setText(text);

            parent.addView(v);
        }
    }

    // Helper function to merge arrays
    public static <T> T[] concat(T[] first, T[] second) {
        T[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (MotionEvent.ACTION_OUTSIDE == event.getAction()) {
            finish();
            return true;
        }

        // Delegate everything else to Activity.
        return super.onTouchEvent(event);
    }

}
