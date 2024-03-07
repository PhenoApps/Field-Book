package com.fieldbook.tracker.dialogs;

import android.app.Dialog;
import android.content.SharedPreferences;
import android.database.DatabaseUtils;
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
import com.fieldbook.tracker.objects.TraitObject;
import com.fieldbook.tracker.preferences.GeneralKeys;
import com.fieldbook.tracker.utilities.CategoryJsonUtil;
import com.fieldbook.tracker.utilities.Utils;
import com.fieldbook.tracker.views.RangeBoxView;

import org.brapi.v2.model.pheno.BrAPIScaleValidValuesCategories;

import java.util.ArrayList;
import java.util.List;

public class SearchDialog extends DialogFragment implements SearchAttributeChooserDialog.OnAttributeClickedListener, OperatorDialog.OnOperatorClickedListener, SearchAdapter.onEditTextChangedListener, SearchAdapter.onDeleteClickedListener {

    private static final String TAG = "SearchDialog";
    public static String TICK = "\"";
    private static CollectActivity originActivity;
    SearchAdapter searchAdapter;
    private SharedPreferences ep;
    private static List<SearchDialogDataModel> dataSet;
    public static boolean openResults;
    private final onSearchResultsClickedListener onSearchResultsClickedListener;

    public SearchDialog(CollectActivity activity, onSearchResultsClickedListener onSearchResultsClickedListener) {
        originActivity = activity;
        this.onSearchResultsClickedListener = onSearchResultsClickedListener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {

        ep = requireContext().getSharedPreferences(GeneralKeys.SHARED_PREF_FILE_NAME, 0);

        AlertDialog.Builder builder = new AlertDialog.Builder(originActivity, R.style.AppAlertDialog);

        View customView = getLayoutInflater().inflate(R.layout.dialog_search, null);
        builder.setTitle(originActivity.getString(R.string.main_toolbar_search));
        builder.setView(customView);

        if (dataSet == null) {
            dataSet = new ArrayList<>();
        }

        // Opening the search results dialog if flag is true
        if (openResults) {
            openResults = false;
            createSearchResultsDialog();
        }

        searchAdapter = new SearchAdapter(dataSet, this, this, this, getContext());
        RecyclerView recyclerView = customView.findViewById(R.id.dialog_search_rv);
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        recyclerView.setAdapter(searchAdapter);

        ImageButton add = customView.findViewById(R.id.dialog_search_add_btn);

        add.setOnClickListener(arg0 -> {
            createSearchAttributeChooserDialog();
        });

        builder.setPositiveButton(R.string.search_dialog_search, null);

        builder.setNegativeButton(R.string.dialog_close, (dialogInterface, id) -> dismiss());

        builder.setNeutralButton(R.string.search_dialog_clear, null);

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM));
        return dialog;
    }

    @Override
    public void onResume() {
        super.onResume();

        final AlertDialog d = (AlertDialog) getDialog();
        if (d != null) {
            Button neutralButton = (Button) d.getButton(Dialog.BUTTON_NEUTRAL);
            neutralButton.setOnClickListener(v -> {
                dataSet.clear();
                searchAdapter.notifyDataSetChanged();
            });

            Button positiveButton = (Button) d.getButton(Dialog.BUTTON_POSITIVE);
            positiveButton.setOnClickListener(v -> {
                try {
                    createSearchResultsDialog();
                } catch (Exception z) {
                    Log.e(TAG, "" + z.getMessage());
                }
            });
        }
    }

    private static String decodeCategorical(String value) {
        ArrayList<BrAPIScaleValidValuesCategories> cats = CategoryJsonUtil.Companion.decode(value);
        String v = cats.get(0).getValue();
        if (cats.size() > 1) {
            for (int i = 1; i < cats.size(); i++)
                v += (", " + cats.get(i).getValue());
        }
        return v;
    }

    public void createSearchAttributeChooserDialog() {
        SearchAttributeChooserDialog sacd = new SearchAttributeChooserDialog(originActivity, this);
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
        dataSet.get(pos).setText(editText);
    }

    @Override
    public void onDeleteClicked(int pos) {
        if (pos >= 0 && pos < dataSet.size()) {
            dataSet.remove(pos);
            searchAdapter.notifyItemRemoved(pos);
        }
    }

    public String encodeCategorical(String t) {
        return CategoryJsonUtil.Companion.encode(new ArrayList<BrAPIScaleValidValuesCategories>() {{
            add(new BrAPIScaleValidValuesCategories().label(t).value(t));
        }});
    }

    public String buildSearchQuery(){

        // Create the sql query based on user selection
        String sql = "";
        String sql1 = "select ObservationUnitProperty.id, ObservationUnitProperty." + TICK + ep.getString(GeneralKeys.UNIQUE_NAME, "") + TICK + ", " + " ObservationUnitProperty." + TICK + ep.getString(GeneralKeys.PRIMARY_NAME, "") + TICK + "," + " ObservationUnitProperty." + TICK + ep.getString(GeneralKeys.SECONDARY_NAME, "") + TICK + " from ObservationUnitProperty where ObservationUnitProperty.id is not null";
        String sql2 = "select ObservationUnitProperty.id, ObservationUnitProperty." + TICK + ep.getString(GeneralKeys.UNIQUE_NAME, "") + TICK + ", " + " ObservationUnitProperty." + TICK + ep.getString(GeneralKeys.PRIMARY_NAME, "") + TICK + "," + " ObservationUnitProperty." + TICK + ep.getString(GeneralKeys.SECONDARY_NAME, "") + TICK + " from observation_variables, ObservationUnitProperty, observations where observations.observation_unit_id = ObservationUnitProperty." + TICK + ep.getString(GeneralKeys.UNIQUE_NAME, "") + TICK + " and observations.observation_variable_name = observation_variables.observation_variable_name and observations.observation_variable_field_book_format = observation_variables.observation_variable_field_book_format";

        for (int i = 0; i < dataSet.size(); i++) {

            String c = dataSet.get(i).getAttribute();
            int s = dataSet.get(i).getImageResourceId();
            String t = dataSet.get(i).getText();

            TraitObject traitObject = originActivity.getDatabase().getTraitByName(c);

            String prefix;
            boolean isAttribute; //Checks if 'c' is an attribute or a trait
            boolean isCategorical = false; //Checks if 'c' is a categorical trait

            if (traitObject == null) {
                isAttribute = true;
                prefix = "ObservationUnitProperty." + TICK + c + TICK;
            } else {
                isAttribute = false;
                prefix = "observation_variables.observation_variable_name = " + TICK + c + TICK;

                if (traitObject.getFormat().equals("categorical") || traitObject.getFormat().equals("multicat") || traitObject.getFormat().equals("qualitative")) {
                    isCategorical = true;
                    t = encodeCategorical(t);
                }
            }

            // This is to prevent crashes when the user uses special characters
            String trunc = DatabaseUtils.sqlEscapeString(t);

            switch (s) {

                // 0: Equals to
                case R.drawable.ic_tb_equal:
                    if (isAttribute) prefix = prefix + " = " + trunc;
                    else prefix = prefix + " and value = " + trunc;
                    break;

                // 1: Not equals to
                case R.drawable.ic_tb_not_equal:
                    if (isAttribute) prefix = prefix + " != " + trunc;
                    else prefix = prefix + " and value != " + trunc;
                    break;

                // 2: Contains
                case R.drawable.ic_tb_contains:
                    if (isAttribute)
                        prefix = prefix + " like " + DatabaseUtils.sqlEscapeString("%" + t + "%");
                    else {
                        if (!isCategorical)
                            prefix = prefix + " and observations.value like " + DatabaseUtils.sqlEscapeString("%" + t + "%");
                        else {
                            String decodedT = CategoryJsonUtil.Companion.decode(t).get(0).getValue();
                            prefix = prefix + " and observations.value like " + DatabaseUtils.sqlEscapeString("%[{\"label\":\"%" + decodedT + "%\",\"value\":\"%" + decodedT + "%\"}]%");
                        }
                    }
                    break;

                // 3: More than
                case R.drawable.ic_tb_greater_than:
                    if (isAttribute) prefix = prefix + " > " + trunc;
                    else prefix = prefix + " and CAST(value AS INTEGER) > " + trunc;
                    break;

                // 4: less than
                case R.drawable.ic_tb_less_than:
                    if (isAttribute) prefix = prefix + " < " + trunc;
                    else prefix = prefix + " and CAST(value AS INTEGER) < " + trunc;
                    break;

            }

            if (isAttribute) sql += sql1 + " and " + prefix;
            else sql += sql2 + " and " + prefix;

            if (i != (dataSet.size() - 1)) sql += " INTERSECT ";

        }
        return sql;
    }

    public void createSearchResultsDialog () {
        String sql = buildSearchQuery();
        final SearchData[] data = originActivity.getDatabase().getRangeBySql(sql);

        if (data != null) {

            //Array to store all the columns to be displayed in the search results dialog
            ArrayList<String> columnsList = new ArrayList<>();
            columnsList.add(ep.getString(GeneralKeys.PRIMARY_NAME, getString(R.string.search_results_dialog_range)));
            columnsList.add(ep.getString(GeneralKeys.SECONDARY_NAME, getString(R.string.search_results_dialog_plot)));

            for (int i = 0; i < dataSet.size(); i++) {

                String c = dataSet.get(i).getAttribute();

                if (!columnsList.contains(c)) {
                    columnsList.add(c);
                }
            }

            ObservationModel[] observations = originActivity.getDatabase().getAllObservations();

            // For each item in the search result and find the values for the selected traits/attributes
            ArrayList<ArrayList<String>> traitData = new ArrayList<>();

            for (SearchData searchdata : data) {
                ArrayList<String> temp = new ArrayList<>();

                // Skipping the first two columns (row and plot)
                for (int j = 2; j < columnsList.size(); j++) {

                    String column = columnsList.get(j);
                    TraitObject traitObject = originActivity.getDatabase().getTraitByName(column);

                    // If column is a trait
                    if (traitObject != null) {
                        for (ObservationModel observation : observations) {
                            if (observation.getObservation_variable_name().equals(column) && observation.getObservation_unit_id().equals(searchdata.unique)) {
                                // If trait is categorical, format the data before adding it to the array
                                if (traitObject.getFormat().equals("categorical") || traitObject.getFormat().equals("multicat") || traitObject.getFormat().equals("qualitative")) {
                                    temp.add(decodeCategorical(observation.getValue()));
                                } else {
                                    temp.add(observation.getValue());
                                }
                                break;
                            }
                        }
                    }
                    // If column is an attribute
                    else {
                        temp.add(originActivity.getDatabase().getObservationUnitPropertyByPlotId(column, searchdata.unique));
                    }
                }
                traitData.add(temp);
            }

            AlertDialog.Builder builder1 = new AlertDialog.Builder(originActivity, R.style.AppAlertDialog);

            View layout = getLayoutInflater().inflate(R.layout.dialog_search_results, null);
            builder1.setTitle(R.string.search_results_dialog_title).setCancelable(true).setView(layout);

            builder1.setNeutralButton(R.string.dialog_back, (dialogInterface1, id1) -> {
                dialogInterface1.dismiss();
                SearchDialog searchdialog = new SearchDialog(originActivity, onSearchResultsClickedListener);
                searchdialog.show(originActivity.getSupportFragmentManager(), TAG);
            });

            builder1.setPositiveButton(R.string.dialog_close, (dialogInterface1, id1) -> {
                dialogInterface1.dismiss();
            });

            final AlertDialog dialog = builder1.create();

            WindowManager.LayoutParams params2 = dialog.getWindow().getAttributes();
            params2.height = WindowManager.LayoutParams.WRAP_CONTENT;
            params2.width = WindowManager.LayoutParams.MATCH_PARENT;
            dialog.getWindow().setAttributes(params2);

            LinearLayout results_parent = layout.findViewById(R.id.search_results_parent);
            for (String column : columnsList) {
                View v = getLayoutInflater().inflate(R.layout.dialog_search_results_trait_headers, null);
                TextView textView = v.findViewById(R.id.trait_header);
                textView.setText(column);
                LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, // width
                        LinearLayout.LayoutParams.WRAP_CONTENT, // height
                        1f // weight
                );
                textView.setLayoutParams(layoutParams);
                results_parent.addView(textView);
            }

            ListView myList = layout.findViewById(R.id.myList);

            myList.setOnItemClickListener((arg012, arg1, position, arg3) -> {

                // Save flag so the search results dialog will be opened instead of search dialog
                openResults = true;

                // When you click on an item, send the data back to the main screen
                onSearchResultsClickedListener.onSearchResultsClicked(data[position].unique, data[position].range, data[position].plot, true);
                dialog.dismiss();

                //Reloading collect activity screen to move to the selected plot
                RangeBoxView rangeBox = originActivity.findViewById(R.id.act_collect_range_box);
                int[] rangeID = rangeBox.getRangeID();
                originActivity.moveToSearch("search", rangeID, CollectActivity.searchRange, CollectActivity.searchPlot, null, -1);
            });

            // If search has results, show them, otherwise display error message

            myList.setAdapter(new SearchResultsAdapter(getActivity(), data, traitData));
            //Dismiss the search dialog
            dismiss();
            //Show the results dialog
            dialog.show();
        } else {
            Utils.makeToast(getActivity(), getString(R.string.search_results_missing));
        }
    }

    public interface onSearchResultsClickedListener {
        /**
         * Used to navigate to the plot selected in search results dialog
         */
        void onSearchResultsClicked(String unique, String range, String plot, boolean reload);
    }
}
