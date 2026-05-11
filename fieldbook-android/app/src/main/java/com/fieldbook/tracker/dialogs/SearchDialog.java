package com.fieldbook.tracker.dialogs;

import android.app.Dialog;
import android.content.SharedPreferences;
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
import com.fieldbook.tracker.adapters.AttributeAdapter;
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

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class SearchDialog extends DialogFragment implements AttributeChooserDialog.OnAttributeSelectedListener, OperatorDialog.OnOperatorClickedListener, SearchAdapter.onEditTextChangedListener, SearchAdapter.onDeleteClickedListener {

    @Inject
    SharedPreferences ep;

    private static final String TAG = "SearchDialog";
    private static CollectActivity originActivity;
    private SearchAdapter searchAdapter;
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

        add.setOnClickListener(arg0 -> createAttributeChooserDialog());

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
        StringBuilder v = new StringBuilder(cats.get(0).getValue());
        if (cats.size() > 1) {
            for (int i = 1; i < cats.size(); i++)
                v.append(", ").append(cats.get(i).getValue());
        }
        return v.toString();
    }

    public void createAttributeChooserDialog() {
        AttributeChooserDialog attributeChooserDialog = new AttributeChooserDialog(
                true, true, false
        );
        attributeChooserDialog.setOnAttributeSelectedListener(this);
        attributeChooserDialog.show(originActivity.getSupportFragmentManager(), "attributeChooserDialog");
    }

    @Override
    public void onAttributeSelected(@NonNull AttributeAdapter.AttributeModel model) {
        dataSet.add(new SearchDialogDataModel(model, R.drawable.ic_tb_equal, ""));
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

    public void createSearchResultsDialog () {
        String searchQuery = originActivity.getDatabase().getSearchQuery(dataSet);
        final SearchData[] data = originActivity.getDatabase().getRangeBySql(searchQuery);

        if (data != null) {

            String uniqueName = ep.getString(GeneralKeys.UNIQUE_NAME, "");

            //Array to store all the columns to be displayed in the search results dialog
            ArrayList<String> columnsList = new ArrayList<>();
            columnsList.add(ep.getString(GeneralKeys.PRIMARY_NAME, getString(R.string.search_results_dialog_range)));
            columnsList.add(ep.getString(GeneralKeys.SECONDARY_NAME, getString(R.string.search_results_dialog_plot)));

            for (int i = 0; i < dataSet.size(); i++) {

                String c = dataSet.get(i).getAttribute().getLabel();

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
                            if (String.valueOf(observation.getObservation_variable_db_id()).equals(traitObject.getId()) && observation.getObservation_unit_id().equals(searchdata.unique)) {
                                // If trait is categorical, format the data before adding it to the array
                                if (traitObject.getFormat().equals("categorical") || traitObject.getFormat().equals("qualitative")) {
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
                        temp.add(originActivity.getDatabase().getObservationUnitPropertyByPlotId(uniqueName, column, searchdata.unique));
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

            builder1.setPositiveButton(R.string.dialog_close, (dialogInterface1, id1) -> dialogInterface1.dismiss());

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
