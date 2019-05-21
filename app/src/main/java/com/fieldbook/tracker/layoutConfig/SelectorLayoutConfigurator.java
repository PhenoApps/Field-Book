package com.fieldbook.tracker.layoutConfig;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.fieldbook.tracker.DataHelper;
import com.fieldbook.tracker.R;

public class SelectorLayoutConfigurator  extends RecyclerView.Adapter<SelectorLayoutConfigurator.ViewHolder>{

    private Context context;
    private int maxSelectors;
    private DataHelper dataHelper;
    private String plotId;
    private RecyclerView selectorsView;

    // Provide a suitable constructor (depends on the kind of dataset)
    public SelectorLayoutConfigurator(Context context, int maxSelectors, RecyclerView selectorsView) {
        this.context = context;
        this.maxSelectors = maxSelectors;
        this.selectorsView = selectorsView;

        this.dataHelper = new DataHelper(context);
    }

    public void configureDropdownArray(String plotId){
        this.plotId = plotId;
        selectorsView.setHasFixedSize(true);

        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this.context);
        selectorsView.setLayoutManager(layoutManager);

        selectorsView.setAdapter(this);
    }

    // Create new views (invoked by the layout manager)
    @Override
    public SelectorLayoutConfigurator.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // create a new view
        LinearLayout v = (LinearLayout) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.selector_dropdown, parent, false);
        SelectorLayoutConfigurator.ViewHolder vh = new SelectorLayoutConfigurator.ViewHolder(v);
        return vh;
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(SelectorLayoutConfigurator.ViewHolder holder, int position) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element

        Spinner spinner = holder.mTextView.findViewById(R.id.selectorSpinner);
        TextView text = holder.mTextView.findViewById(R.id.selectorText);
        configureSpinner(spinner, text, position);
        configureText(text);
    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return maxSelectors;
    }

    private SharedPreferences getSharedPref(){
        return this.context.getSharedPreferences("Settings", 0);
    }

    private void configureSpinner(final Spinner spinner, final TextView text, final int position) {

        final String[] prefixTraits = dataHelper.getRangeColumnNames();

        ArrayAdapter<String> prefixArrayAdapter = new ArrayAdapter<>(this.context, R.layout.custom_spinnerlayout, prefixTraits);

        spinner.setAdapter(prefixArrayAdapter);

        int spinnerPosition = prefixArrayAdapter.getPosition(getSharedPref().getString("DROP" + position, prefixTraits[0]));
        spinner.setSelection(spinnerPosition);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int pos, long arg3) {
                try {
                    String[] traitTextArray = dataHelper.getDropDownRange(prefixTraits[pos], plotId);

                    if (traitTextArray == null) {
                        text.setText(context.getString(R.string.main_infobar_data_missing));
                    } else {
                        text.setText(traitTextArray[0]);
                    }

                    getSharedPref().edit().putString("DROP" + position, prefixTraits[pos]).apply();
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
    private void configureText(final TextView text){
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
        LinearLayout mTextView;
        ViewHolder(LinearLayout v) {
            super(v);
            mTextView = v;
        }
    }
}
