package com.fieldbook.tracker.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.CollectActivity;
import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.dialogs.CollectAttributeChooserDialog;
import com.fieldbook.tracker.objects.TraitObject;
import com.fieldbook.tracker.preferences.GeneralKeys;

import java.util.ArrayList;
import java.util.Arrays;

public class InfoBarAdapter extends RecyclerView.Adapter<InfoBarAdapter.ViewHolder> {

    private Context context;
    private int maxSelectors;
    private DataHelper dataHelper;
    private RecyclerView selectorsView;

    // Provide a suitable constructor (depends on the kind of dataset)
    public InfoBarAdapter(Context context, int maxSelectors, String plotId) {
        this.context = context;
        this.maxSelectors = maxSelectors;
        //this.selectorsView = selectorsView;
        this.dataHelper = new DataHelper(context);
    }

    public void notifyPlotChanged(String plotId) {

    }

    // Create new views (invoked by the layout manager)
    @NonNull
    @Override
    public InfoBarAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // create a new view
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_infobar, parent, false);
        return new ViewHolder((ConstraintLayout) v);
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(InfoBarAdapter.ViewHolder holder, int position) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element

        configureSpinner(holder, position);
    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return maxSelectors;
    }

    private SharedPreferences getSharedPref() {
        return this.context.getSharedPreferences(GeneralKeys.SHARED_PREF_FILE_NAME, 0);
    }

    private void setViewHolderText(ViewHolder holder, String label, String value) {
        holder.prefixTextView.setText(label + ":");
        holder.valueTextView.setText(value);
    }

    private void configureSpinner(final ViewHolder holder, final int position) {

        //initialize the dialog that will pop-up when the user clicks on oen of the infobars
        CollectAttributeChooserDialog ad = new CollectAttributeChooserDialog((CollectActivity) context, (selectedString, valueString) -> {
            holder.prefixTextView.setText(selectedString + ":");
            holder.valueTextView.setText(valueString);
            return null;
        }, position);

        //ensure that the initialLabel is actually a plot attribute //TODO check that initial label is trait
        String[] attributes = dataHelper.getRangeColumnNames();

        //if not a plot attribute, it may be a trait name
        ArrayList<TraitObject> traits = dataHelper.getAllTraitObjects();
        ArrayList<String> traitNames = new ArrayList<String>();
        for (TraitObject t : traits) {
            traitNames.add(t.getTrait());
        }

        String defaultLabel = "Select";
        if (attributes.length > 0) {
            defaultLabel = attributes[0];
        }

        //adapter preferred values are saved as DROP1, DROP2, DROP3, DROP4, DROP5 in preferences, intiailize the label with it
        String initialLabel = ((CollectActivity) context).getPreferences().getString("DROP" + position, defaultLabel);

        boolean isAttribute = false;
        if (!Arrays.asList(attributes).contains(initialLabel)) {
            if (!traitNames.contains(initialLabel)) {
                if (attributes.length > 0) {
                    initialLabel = attributes[0];
                }
            }
        } else isAttribute = true;

        //get the database value for the initial label
        String initialValue = ad.queryForLabelValue(((CollectActivity) context).getRangeBox().getPlotID(), initialLabel, isAttribute);

        setViewHolderText(holder, initialLabel, initialValue);

        holder.prefixTextView.setOnClickListener(v -> {
            ad.show();
        });

        //TODO set the preference selection, int spinnerPosition = prefixArrayAdapter.getPosition(getSharedPref().getString("DROP" + position, allTraits[0]));

//        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
//            @Override
//            public void onItemSelected(AdapterView<?> arg0, View arg1, int pos, long arg3) {
//                try {
//
//                    //when an item is selected, check if its a prefix trait or a variable and populate the info bar values
//                    String infoTrait = allTraits[pos];
//
//                    ArrayList<String> infoBarValues = null;
//                    if (Arrays.asList(prefixTraits).contains(infoTrait)) { //get data from obs. properties (range)
//
//                        infoBarValues = new ArrayList<>(Arrays.asList(dataHelper.getDropDownRange(infoTrait, plotId)));
//
//                    } else if (Arrays.asList(obsTraits).contains(infoTrait)) { //get data from observations
//
//                        infoBarValues = new ArrayList<>(Collections.singletonList(
//                                ObservationDao.Companion.getUserDetail(expId, plotId).get(infoTrait)));
//                    }
//
//                    if (infoBarValues == null || infoBarValues.size() == 0) {
//
//                        text.setText(context.getString(R.string.main_infobar_data_missing));
//
//                    } else {
//
//                        text.setText(infoBarValues.get(0));
//
//                    }
//
//                    getSharedPref().edit().putString("DROP" + position, allTraits[pos]).apply();
//
//                } catch (Exception e) {
//
//                    e.printStackTrace();
//
//                }
//
//                spinner.requestFocus();
//            }
//
//            @Override
//            public void onNothingSelected(AdapterView<?> arg0) {
//
//            }
//        });
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
        TextView prefixTextView;
        TextView valueTextView;

        ViewHolder(ConstraintLayout v) {
            super(v);
            prefixTextView = v.findViewById(R.id.list_item_infobar_prefix);
            valueTextView = v.findViewById(R.id.list_item_infobar_value);
        }
    }
}