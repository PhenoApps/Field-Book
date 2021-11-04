package com.fieldbook.tracker.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.R;
import com.fieldbook.tracker.database.dao.ObservationDao;
import com.fieldbook.tracker.objects.TraitObject;
import com.fieldbook.tracker.utilities.PrefsConstants;
import com.fieldbook.tracker.views.DynamicWidthSpinner;

import org.apache.commons.lang3.ArrayUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class InfoBarAdapter extends RecyclerView.Adapter<InfoBarAdapter.ViewHolder> {

    private Context context;
    private int maxSelectors;
    private DataHelper dataHelper;
    private String plotId;
    private RecyclerView selectorsView;

    // Provide a suitable constructor (depends on the kind of dataset)
    public InfoBarAdapter(Context context, int maxSelectors, RecyclerView selectorsView) {
        this.context = context;
        this.maxSelectors = maxSelectors;
        this.selectorsView = selectorsView;

        this.dataHelper = new DataHelper(context);
    }

    public void configureDropdownArray(String plotId) {
        this.plotId = plotId;
        selectorsView.setHasFixedSize(false);

        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this.context);
        selectorsView.setLayoutManager(layoutManager);

        selectorsView.setAdapter(this);
    }

    // Create new views (invoked by the layout manager)
    @Override
    public InfoBarAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // create a new view
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.selector_dropdown, parent, false);
        return new ViewHolder((ConstraintLayout) v);
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(InfoBarAdapter.ViewHolder holder, int position) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element

        DynamicWidthSpinner spinner = holder.mTextView.findViewById(R.id.selectorSpinner);
        TextView text = holder.mTextView.findViewById(R.id.selectorText);
        configureSpinner(spinner, text, position);
        configureText(text);
    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return maxSelectors;
    }

    private SharedPreferences getSharedPref() {
        return this.context.getSharedPreferences("Settings", 0);
    }

    private void configureSpinner(final DynamicWidthSpinner spinner, final TextView text, final int position) {

        String expId = Integer.toString(getSharedPref().getInt(PrefsConstants.SELECTED_FIELD_ID, 0));

        //the prefix obs. unit. traits s.a plot_id, row, column, defined by the user
        String[] prefixTraits = dataHelper.getRangeColumnNames();

        //the observation variable traits
        String[] obsTraits = dataHelper.getAllTraitObjects().stream().map(TraitObject::getTrait).toArray(String[]::new);

        //combine the traits to be viewed within info bars
        final String[] allTraits = ArrayUtils.addAll(prefixTraits, obsTraits);

        ArrayAdapter<String> prefixArrayAdapter = new ArrayAdapter<>(this.context, R.layout.custom_spinnerlayout, allTraits);

        spinner.setAdapter(prefixArrayAdapter);

        int spinnerPosition = prefixArrayAdapter.getPosition(getSharedPref().getString("DROP" + position, allTraits[0]));
        spinner.setSelection(spinnerPosition);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int pos, long arg3) {
                try {

                    //when an item is selected, check if its a prefix trait or a variable and populate the info bar values
                    String infoTrait = allTraits[pos];

                    ArrayList<String> infoBarValues = null;
                    if (Arrays.asList(prefixTraits).contains(infoTrait)) { //get data from obs. properties (range)

                        infoBarValues = new ArrayList<>(Arrays.asList(dataHelper.getDropDownRange(infoTrait, plotId)));

                    } else if (Arrays.asList(obsTraits).contains(infoTrait)) { //get data from observations

                        infoBarValues = new ArrayList<>(Collections.singletonList(
                                ObservationDao.Companion.getUserDetail(expId, plotId).get(infoTrait)));
                    }

                    if (infoBarValues == null || infoBarValues.size() == 0) {

                        text.setText(context.getString(R.string.main_infobar_data_missing));

                    } else {

                        text.setText(infoBarValues.get(0));

                    }

                    getSharedPref().edit().putString("DROP" + position, allTraits[pos]).apply();

                } catch (Exception e) {

                    e.printStackTrace();

                }

                spinner.requestFocus();
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {

            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void configureText(final TextView text) {
        text.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        text.setMaxLines(5);
                        break;
                    case MotionEvent.ACTION_UP:
                        text.setMaxLines(1);
                        break;
                }
                return true;
            }
        });
    }

    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder
    static class ViewHolder extends RecyclerView.ViewHolder {
        // each data item is just a string in this case
        ConstraintLayout mTextView;

        ViewHolder(ConstraintLayout v) {
            super(v);
            mTextView = v;
        }
    }
}