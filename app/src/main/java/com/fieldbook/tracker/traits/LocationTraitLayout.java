package com.fieldbook.tracker.traits;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;

import com.fieldbook.tracker.activities.CollectActivity;
import com.fieldbook.tracker.R;
import com.fieldbook.tracker.location.GPSTracker;
import com.fieldbook.tracker.utilities.Utils;

public class LocationTraitLayout extends BaseTraitLayout {

    public LocationTraitLayout(Context context) {
        super(context);
    }

    public LocationTraitLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LocationTraitLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setNaTraitsText() {
    }

    @Override
    public String type() {
        return "location";
    }

    @Override
    public void init() {
        ImageButton getLocation = findViewById(R.id.getLocationBtn);
        // Get Location
        getLocation.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                GPSTracker gps = new GPSTracker(getContext());
                String fullLocation = "";
                double lat;
                double lng;

                if (gps.canGetLocation()) { //GPS enabled
                    lat = gps.getLatitude(); // returns latitude
                    lng = gps.getLongitude(); // returns longitude
                    fullLocation = Utils.truncateDecimalString(String.valueOf(lat), 8) + "; " + Utils.truncateDecimalString(String.valueOf(lng), 8);
                } else {
                    Intent intent = new Intent(
                            Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    getContext().startActivity(intent);
                }
                getEtCurVal().setText(fullLocation);
                updateTrait(getCurrentTrait().getTrait(), "location", fullLocation);
            }
        });
    }

    @Override
    public void loadLayout() {

        getEtCurVal().setVisibility(EditText.VISIBLE);

        if (getNewTraits().containsKey(getCurrentTrait().getTrait())) {
            getEtCurVal().setText(getNewTraits().get(getCurrentTrait().getTrait()).toString());
            getEtCurVal().setTextColor(Color.parseColor(getDisplayColor()));
        } else {
            getEtCurVal().setText("");
            getEtCurVal().setTextColor(Color.BLACK);

            if (getCurrentTrait().getDefaultValue() != null
                    && getCurrentTrait().getDefaultValue().length() > 0)
                getEtCurVal().setText(getCurrentTrait().getDefaultValue());
        }
    }

    @Override
    public void deleteTraitListener() {
        ((CollectActivity) getContext()).removeTrait();
    }
}