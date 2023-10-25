package com.fieldbook.tracker.traits;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.AttributeSet;
import android.widget.ImageButton;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.CollectActivity;
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
    public int layoutId() {
        return R.layout.trait_location;
    }

    @Override
    public void init(Activity act) {
        ImageButton getLocation = act.findViewById(R.id.getLocationBtn);

        String locationSavedTts = getContext().getString(R.string.trait_location_saved_tts);

        // Get Location
        getLocation.setOnClickListener(arg0 -> {
            GPSTracker gps = new GPSTracker(getContext());
            String fullLocation = "";
            double lat;
            double lng;

            if (gps.canGetLocation()) { //GPS enabled
                lat = gps.getLatitude(); // returns latitude
                lng = gps.getLongitude(); // returns longitude
                fullLocation = Utils.truncateDecimalString(String.valueOf(lng), 8) + "; " + Utils.truncateDecimalString(String.valueOf(lat), 8);
            } else {
                Intent intent = new Intent(
                        Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                getContext().startActivity(intent);
            }
            getCollectInputView().setText(fullLocation);
            updateObservation(getCurrentTrait().getTrait(), "location", fullLocation);
            triggerTts(locationSavedTts);
        });

        getLocation.requestFocus();
    }

    @Override
    public void deleteTraitListener() {
        ((CollectActivity) getContext()).removeTrait();
        super.deleteTraitListener();
    }
}