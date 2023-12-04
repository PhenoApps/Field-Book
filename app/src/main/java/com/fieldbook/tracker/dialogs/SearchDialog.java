package com.fieldbook.tracker.dialogs;

import android.app.Dialog;
import android.content.SharedPreferences;
import android.database.DatabaseUtils;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.CollectActivity;
import com.fieldbook.tracker.adapters.SearchAdapter;
import com.fieldbook.tracker.adapters.SearchResultsAdapter;
import com.fieldbook.tracker.database.models.ObservationModel;
import com.fieldbook.tracker.objects.SearchData;
import com.fieldbook.tracker.objects.SearchDialogDataModel;
import com.fieldbook.tracker.preferences.GeneralKeys;
import com.fieldbook.tracker.utilities.Utils;
import com.fieldbook.tracker.views.RangeBoxView;

import java.util.ArrayList;
import java.util.List;

public class SearchDialog extends DialogFragment implements SearchAtrributeChooserDialog.OnAttributeClickedListener, OperatorDialog.OnOperatorClickedListener, SearchAdapter.onEditTextChangedListener, SearchAdapter.onDeleteClickedListener {

    private static final String TAG = "Field Book";
    public static String TICK = "\"";
    private static CollectActivity originActivity;
    SearchAdapter searchAdapter;
    private SharedPreferences ep;
    private int rangeUntil;
    private List<SearchDialogDataModel> dataSet;
    public static List<SearchDialogDataModel> savedDataSet;
    public static boolean isSaved;

    public SearchDialog(CollectActivity activity) {
        originActivity = activity;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {

        ep = requireContext().getSharedPreferences(GeneralKeys.SHARED_PREF_FILE_NAME, 0);

        AlertDialog.Builder builder = new AlertDialog.Builder(originActivity, R.style.AppAlertDialog);

        View customView = getLayoutInflater().inflate(R.layout.dialog_search, null);
        builder.setTitle("Search");
        builder.setView(customView);

        // Loading the search query if saved
        if (isSaved) {
            dataSet = getSavedDataSet();
        } else {
            dataSet = new ArrayList<>();
        }
        searchAdapter = new SearchAdapter(dataSet, this, this, this, getContext());
        RecyclerView recyclerView = customView.findViewById(R.id.dialog_search_rv);
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        recyclerView.setAdapter(searchAdapter);

        ImageButton add = customView.findViewById(R.id.dialog_search_add_btn);
        ImageButton clear = customView.findViewById(R.id.dialog_search_delete_all_btn);
        Button start = customView.findViewById(R.id.dialog_search_ok_btn);
        Button close = customView.findViewById(R.id.dialog_search_close_btn);

        start.setTransformationMethod(null);
        close.setTransformationMethod(null);

        add.setOnClickListener(arg0 -> {
            createSearchAttributeChooserDialog();
        });

        start.setOnClickListener(arg0 -> {
//            Spinner c = parent.getChildAt(0).findViewById(R.id.columns);
//            Spinner s = parent.getChildAt(0).findViewById(R.id.like);
//            SharedPreferences.Editor ed = ep.edit();
//            ed.putInt(GeneralKeys.SEARCH_COLUMN_DEFAULT, c.getSelectedItemPosition());
//            ed.putInt(GeneralKeys.SEARCH_LIKE_DEFAULT, s.getSelectedItemPosition());
//            ed.apply();

            try {
                // Create the sql query based on user selection
                String sql1 = "select ObservationUnitProperty.id, ObservationUnitProperty." + TICK + ep.getString(GeneralKeys.UNIQUE_NAME, "") + TICK + ", " + " ObservationUnitProperty." + TICK + ep.getString(GeneralKeys.PRIMARY_NAME, "") + TICK + "," + " ObservationUnitProperty." + TICK + ep.getString(GeneralKeys.SECONDARY_NAME, "") + TICK + " from ObservationUnitProperty where ObservationUnitProperty.id is not null ";
                String sql2 = "select ObservationUnitProperty.id, ObservationUnitProperty." + TICK + ep.getString(GeneralKeys.UNIQUE_NAME, "") + TICK + ", " + " ObservationUnitProperty." + TICK + ep.getString(GeneralKeys.PRIMARY_NAME, "") + TICK + "," + " ObservationUnitProperty." + TICK + ep.getString(GeneralKeys.SECONDARY_NAME, "") + TICK + " from observation_variables, ObservationUnitProperty, observations where observations.observation_unit_id = ObservationUnitProperty." + TICK + ep.getString(GeneralKeys.UNIQUE_NAME, "") + TICK + " and observations.observation_variable_name = observation_variables.observation_variable_name and observations.observation_variable_field_book_format = observation_variables.observation_variable_field_book_format ";

                String sql = "";

                boolean threeTables = false;

                ArrayList<String> columnsList = new ArrayList<>();
                for (int i = 0; i < dataSet.size(); i++) {

                    String c = dataSet.get(i).getAttribute();
                    int s = dataSet.get(i).getImageResourceId();
                    String t = dataSet.get(i).getEditText();


                    String value = "";
                    String prefix;

                    boolean before;

                    if (i < rangeUntil) {
                        before = true;
                        prefix = "ObservationUnitProperty.";
                    } else {
                        before = false;
                        threeTables = true;
                        prefix = "observation_variables.observation_variable_name=";
                    }

                    // This is to prevent crashes when the user uses special characters
                    String trunc = DatabaseUtils.sqlEscapeString(t);

                    // We only want to escape the string, but not the encapsulating "'"
                    // For example 'plot\'s', we only want plot\'s
                    if (trunc.length() > 3) trunc = trunc.substring(1, trunc.length() - 2);

                    columnsList.add(c);

                    switch (s) {

                        // 0: Equals to
                        case R.drawable.ic_tb_equal:
                            if (before)
                                value = prefix + TICK + c + TICK + " = " + DatabaseUtils.sqlEscapeString(t) + "";
                            else
                                value = prefix + TICK + c + TICK + " and value = " + DatabaseUtils.sqlEscapeString(t) + "";
                            break;

                        // 1: Not equals to
                        case R.drawable.ic_tb_not_equal:
                            if (before)
                                value = prefix + TICK + c + TICK + " != " + DatabaseUtils.sqlEscapeString(t) + "";
                            else
                                value = prefix + TICK + c + TICK + " and value != " + DatabaseUtils.sqlEscapeString(t) + "";
                            break;

                        // 2: Contains
                        case R.drawable.ic_tb_contains:
                            if (before)
                                value = prefix + TICK + c + TICK + " like " + DatabaseUtils.sqlEscapeString("%" + t + "%") + "";
                            else
                                value = prefix + TICK + c + TICK + " and observations.value like " + DatabaseUtils.sqlEscapeString("%" + t + "%") + "";
                            break;


                        // 3: More than
                        case R.drawable.ic_tb_greater_than:
                            if (before) value = prefix + TICK + c + TICK + " > " + trunc;
                            else value = prefix + TICK + c + TICK + " and value > " + trunc;
                            break;

                        // 4: less than
                        case R.drawable.ic_tb_less_than:
                            if (before) value = prefix + TICK + c + TICK + " < " + trunc;
                            else value = prefix + TICK + c + TICK + " and value < " + trunc;
                            break;

                    }

                    if (i == dataSet.size() - 1) sql += "and " + value + " ";
                    else sql += "and " + value + " ";

                }

                if (threeTables) sql = sql2 + sql;
                else sql = sql1 + sql;

                final SearchData[] data = originActivity.getDatabase().getRangeBySql(sql);
                ObservationModel[] observations = originActivity.getDatabase().getAllObservations();

                ArrayList<ArrayList<String>> traitData = new ArrayList<>();

                for (SearchData searchdata : data) {
                    ArrayList<String> temp = new ArrayList<>();
                    for (String column : columnsList) {
                        for (ObservationModel observation : observations) {
                            if (observation.getObservation_variable_name().equals(column) && observation.getObservation_unit_id().equals(searchdata.unique)) {
                                temp.add(observation.getValue());
                            }
                        }
                    }
                    traitData.add(temp);
                }

                AlertDialog.Builder builder1 = new AlertDialog.Builder(getActivity(), R.style.AppAlertDialog);

                View layout = getLayoutInflater().inflate(R.layout.dialog_search_results, null);
                builder1.setTitle(R.string.search_results_dialog_title).setCancelable(true).setView(layout);

                final AlertDialog dialog = builder1.create();

                WindowManager.LayoutParams params2 = dialog.getWindow().getAttributes();
                params2.height = WindowManager.LayoutParams.WRAP_CONTENT;
                params2.width = WindowManager.LayoutParams.MATCH_PARENT;
                dialog.getWindow().setAttributes(params2);

                TextView primaryTitle = layout.findViewById(R.id.range);
                TextView secondaryTitle = layout.findViewById(R.id.plot);

                primaryTitle.setText(ep.getString(GeneralKeys.PRIMARY_NAME, getString(R.string.search_results_dialog_range)));
                secondaryTitle.setText(ep.getString(GeneralKeys.SECONDARY_NAME, getString(R.string.search_results_dialog_plot)));

                LinearLayout results_parent = layout.findViewById(R.id.search_results_parent);
                for (String column : columnsList) {
                    View v = getLayoutInflater().inflate(R.layout.dialog_search_results_trait_headers, null);
                    TextView textView = v.findViewById(R.id.trait_header);
                    textView.setText(column);
                    results_parent.addView(textView);
                }

                Button closeBtn = layout.findViewById(R.id.closeBtn);
                ListView myList = layout.findViewById(R.id.myList);

                myList.setDivider(new ColorDrawable(Color.BLACK));
                myList.setDividerHeight(5);

                closeBtn.setTransformationMethod(null);

                myList.setOnItemClickListener((arg012, arg1, position, arg3) -> {

                    // Save the dataset so that it will be loaded when the search dialog is opened again
                    setSavedDataSet(dataSet);

                    // When you click on an item, send the data back to the main screen
                    CollectActivity.searchUnique = data[position].unique;
                    CollectActivity.searchRange = data[position].range;
                    CollectActivity.searchPlot = data[position].plot;
                    CollectActivity.searchReload = true;
                    dialog.dismiss();

                    //Reloading collect activity screen to move to the selected plot
                    RangeBoxView rangeBox = originActivity.findViewById(R.id.act_collect_range_box);
                    int[] rangeID = rangeBox.getRangeID();
                    originActivity.moveToSearch("search", rangeID, CollectActivity.searchRange, CollectActivity.searchPlot, null, -1);
                });

                closeBtn.setOnClickListener(arg01 -> dialog.dismiss());

                // If search has results, show them, otherwise display error message
                if (data != null) {
                    myList.setAdapter(new SearchResultsAdapter(getActivity(), data, traitData));
                    //Dismiss the search dialog
                    dismiss();
                    //Show the results dialog
                    dialog.show();
                } else {
                    Utils.makeToast(getActivity(), getString(R.string.search_results_missing));
                }
            } catch (Exception z) {
                Log.e(TAG, "" + z.getMessage());
            }
        });

        close.setOnClickListener(arg0 -> dismiss());

        clear.setOnClickListener(arg0 -> {
            dataSet.clear();
            searchAdapter.notifyDataSetChanged();
        });


        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM));
        return dialog;
    }

    public static List<SearchDialogDataModel> getSavedDataSet() {
        isSaved = false;
        return savedDataSet;
    }

    public static void setSavedDataSet(List<SearchDialogDataModel> dataSet) {
        isSaved = true;
        savedDataSet = dataSet;
    }

    public void createSearchAttributeChooserDialog() {
        SearchAtrributeChooserDialog sacd = new SearchAtrributeChooserDialog(originActivity, this);
        sacd.show();
    }

    @Override
    public void onAttributeSelected(String selectedAttribute) {
        dataSet.add(new SearchDialogDataModel(selectedAttribute, R.drawable.ic_tb_equal, ""));
        searchAdapter.notifyItemInserted(dataSet.size() - 1);
    }

    @Override
    public void onOperatorSelected(int pos, int imageId) {
        dataSet.get(pos).setImageResourceId(imageId);
        searchAdapter.notifyItemChanged(pos);
    }

    @Override
    public void onEditTextChanged(int pos, String editText) {
        dataSet.get(pos).setEditText(editText);
    }

    @Override
    public void onDeleteClicked(int pos) {
        if (pos >= 0 && pos < dataSet.size()) {
            dataSet.remove(pos);
            searchAdapter.notifyItemRemoved(pos);
        }
    }
}
