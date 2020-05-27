package com.fieldbook.tracker.activities;

import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.SharedPreferences;
import android.database.DatabaseUtils;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;

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

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.adapters.SearchAdapter;
import com.fieldbook.tracker.objects.SearchData;
import com.fieldbook.tracker.utilities.Utils;

import java.util.Arrays;

public class SearchActivity extends AppCompatActivity {
    public static String TICK = "`";
    private static String TAG = "Field Book";
    private SharedPreferences ep;
    private LinearLayout parent;
    private int rangeUntil;

    // Helper function to merge arrays
    public static <T> T[] concat(T[] first, T[] second) {
        //TODO NullPointerException
        T[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ep = getSharedPreferences("Settings", 0);

        setContentView(R.layout.activity_search);

        setTitle(R.string.main_toolbar_search);

        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.width = WindowManager.LayoutParams.MATCH_PARENT;

        this.getWindow().setAttributes(params);

        parent = findViewById(R.id.rowParent);

        Button start = findViewById(R.id.startBtn);
        Button close = findViewById(R.id.closeBtn);
        Button clear = findViewById(R.id.clearBtn);
        Button add = findViewById(R.id.addBtn);

        start.setTransformationMethod(null);
        close.setTransformationMethod(null);
        clear.setTransformationMethod(null);
        add.setTransformationMethod(null);

        start.setOnClickListener(new OnClickListener() {

            public void onClick(View arg0) {

                try {
                    // Create the sql query based on user selection
                    String sql1 = "select range.id, range." + TICK + ep.getString("ImportFirstName", "") + TICK + "," + " range." + TICK + ep.getString("ImportSecondName", "") + TICK + " from range where range.id is not null ";
                    String sql2 = "select range.id, range." + TICK + ep.getString("ImportFirstName", "") + TICK + "," + "range." + TICK + ep.getString("ImportSecondName", "") + TICK + " from traits, range, user_traits where user_traits.rid = range." + TICK + ep.getString("ImportUniqueName", "") + TICK + " and user_traits.parent = traits.trait and user_traits.trait = traits.format ";

                    String sql = "";

                    boolean threeTables = false;

                    for (int i = 0; i < parent.getChildCount(); i++) {
                        LinearLayout child = (LinearLayout) parent.getChildAt(i);

                        EditText t = child.findViewById(R.id.searchText);

                        Spinner c = child.findViewById(R.id.columns);
                        Spinner s = child.findViewById(R.id.like);

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
                                    value = prefix + TICK + c.getSelectedItem().toString() + TICK + " = " + DatabaseUtils.sqlEscapeString(t.getText().toString()) + "";
                                else
                                    value = prefix + TICK + c.getSelectedItem().toString() + TICK + " and userValue = " + DatabaseUtils.sqlEscapeString(t.getText().toString()) + "";
                                break;

                            // 1: Not equals to
                            case 1:
                                if (before)
                                    value = prefix + TICK + c.getSelectedItem().toString() + TICK + " != " + DatabaseUtils.sqlEscapeString(t.getText().toString()) + "";
                                else
                                    value = prefix + TICK + c.getSelectedItem().toString() + TICK + " and userValue != " + DatabaseUtils.sqlEscapeString(t.getText().toString()) + "";
                                break;

                            // 2: Is Like
                            case 2:
                                if (before)
                                    value = prefix + TICK + c.getSelectedItem().toString() + TICK + " like " + DatabaseUtils.sqlEscapeString("%" + t.getText().toString() + "%") + "";
                                else
                                    value = prefix + TICK + c.getSelectedItem().toString() + TICK + " and user_traits.userValue like " + DatabaseUtils.sqlEscapeString("%" + t.getText().toString() + "%") + "";
                                break;

                            // 3: Not is like
                            case 3:
                                if (before)
                                    value = prefix + TICK + c.getSelectedItem().toString() + TICK + " not like " + DatabaseUtils.sqlEscapeString("%" + t.getText().toString() + "%") + "";
                                else
                                    value = prefix + TICK + c.getSelectedItem().toString() + TICK + " and user_traits.userValue not like " + DatabaseUtils.sqlEscapeString("%" + t.getText().toString() + "%") + "";
                                break;

                            // 4: More than
                            case 4:
                                if (before)
                                    value = prefix + TICK + c.getSelectedItem().toString() + TICK + " > " + trunc;
                                else
                                    value = prefix + TICK + c.getSelectedItem().toString() + TICK + " and userValue > " + trunc;
                                break;

                            // 5: less than
                            case 5:
                                if (before)
                                    value = prefix + c.getSelectedItem().toString() + " < " + trunc;
                                else
                                    value = prefix + c.getSelectedItem().toString() + " and userValue < " + trunc;
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

                    final SearchData[] data = ConfigActivity.dt.getRangeBySql(sql);

                    AlertDialog.Builder builder = new AlertDialog.Builder(SearchActivity.this, R.style.AppAlertDialog);

                    LayoutInflater inflater = SearchActivity.this.getLayoutInflater();
                    View layout = inflater.inflate(R.layout.dialog_search_results, null);

                    builder.setTitle(R.string.search_results_dialog_title)
                            .setCancelable(true)
                            .setView(layout);

                    final AlertDialog dialog = builder.create();

                    android.view.WindowManager.LayoutParams params2 = dialog.getWindow().getAttributes();
                    params2.height = WindowManager.LayoutParams.WRAP_CONTENT;
                    params2.width = WindowManager.LayoutParams.MATCH_PARENT;
                    dialog.getWindow().setAttributes(params2);

                    TextView primaryTitle = layout.findViewById(R.id.range);
                    TextView secondaryTitle = layout.findViewById(R.id.plot);

                    primaryTitle.setText(ep.getString("ImportFirstName", getString(R.string.search_results_dialog_range)));
                    secondaryTitle.setText(ep.getString("ImportSecondName", getString(R.string.search_results_dialog_plot)));

                    Button closeBtn = layout.findViewById(R.id.closeBtn);
                    ListView myList = layout.findViewById(R.id.myList);

                    closeBtn.setTransformationMethod(null);

                    myList.setOnItemClickListener(new OnItemClickListener() {

                        public void onItemClick(AdapterView<?> arg0, View arg1,
                                                int position, long arg3) {
                            // When you click on an item, send the data back to the main screen
                            CollectActivity.searchRange = data[position].range;
                            CollectActivity.searchPlot = data[position].plot;
                            CollectActivity.searchReload = true;

                            dialog.dismiss();
                            finish();
                        }
                    });

                    dialog.setOnCancelListener(new OnCancelListener() {

                        public void onCancel(DialogInterface arg0) {
                            finish();
                        }
                    });

                    closeBtn.setOnClickListener(new OnClickListener() {

                        public void onClick(View arg0) {
                            dialog.dismiss();
                            finish();
                        }
                    });

                    // If search has results, show them, otherwise display error message
                    if (data != null) {
                        myList.setAdapter(new SearchAdapter(SearchActivity.this, data));

                        dialog.show();
                    } else {
                        Utils.makeToast(getApplicationContext(),getString(R.string.search_results_missing));
                    }
                } catch (Exception z) {
                    Log.e(TAG, "" + z.getMessage());
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

                addRow("");

            }
        });

        // Add new search row
        add.setOnClickListener(new OnClickListener() {

            public void onClick(View arg0) {
                addRow("");
            }
        });

        addRow("");
    }

    // Helper function to add row
    public void addRow(String text) {
        LayoutInflater vi = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View v = vi.inflate(R.layout.listitem_search_constructor, null);

        Spinner c = v.findViewById(R.id.columns);
        Spinner s = v.findViewById(R.id.like);
        EditText e = v.findViewById(R.id.searchText);

        String[] likes = new String[6];

        likes[0] = getString(R.string.search_dialog_query_is_equal_to);
        likes[1] = getString(R.string.search_dialog_query_is_not_equal_to);
        likes[2] = getString(R.string.search_dialog_query_contains);
        likes[3] = getString(R.string.search_dialog_query_does_not_contain);
        likes[4] = getString(R.string.search_dialog_query_is_more_than);
        likes[5] = getString(R.string.search_dialog_query_is_less_than);

        ArrayAdapter adapter = new ArrayAdapter(SearchActivity.this, R.layout.custom_spinnerlayout, likes);
        //adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(adapter);

        String[] col = ConfigActivity.dt.getRangeColumns();

        if (col != null) {
            rangeUntil = col.length;

            ArrayAdapter adapter2 = new ArrayAdapter(SearchActivity.this, R.layout.custom_spinnerlayout,
                    concat(col, ConfigActivity.dt.getVisibleTrait()));
            adapter2.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            c.setAdapter(adapter2);

            if (text.length() > 0)
                e.setText(text);

            parent.addView(v);
        }
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