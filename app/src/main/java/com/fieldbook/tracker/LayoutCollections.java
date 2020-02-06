package com.fieldbook.tracker;

import android.app.Activity;
import android.view.View;
import android.widget.LinearLayout;

import com.fieldbook.tracker.traitLayouts.PhotoTraitLayout;
import com.fieldbook.tracker.traitLayouts.TraitLayout;

import java.util.ArrayList;

public class LayoutCollections {
    ArrayList<TraitLayout> traitLayouts;

    public LayoutCollections(Activity _activity) {
        int[] traitIDs = {
                R.id.angleLayout, R.id.audioLayout, R.id.barcodeLayout,
                R.id.booleanLayout, R.id.categoricalLayout, R.id.counterLayout,
                R.id.dateLayout, R.id.diseaseLayout, R.id.locationLayout,
                R.id.multicatLayout, R.id.numericLayout, R.id.percentLayout,
                R.id.photoLayout, R.id.textLayout, R.id.labelprintLayout
        };

        traitLayouts = new ArrayList<>();
        for (int traitID : traitIDs) {
            TraitLayout layout = _activity.findViewById(traitID);
            layout.init();
            traitLayouts.add(layout);
        }
    }

    public TraitLayout getTraitLayout(final String trait) {
        for (TraitLayout layout : traitLayouts) {
            if (layout.isTraitType(trait)) {
                return layout;
            }
        }
        return getTraitLayout("text");
    }

    public PhotoTraitLayout getPhotoTrait() {
        return (PhotoTraitLayout) getTraitLayout("photo");
    }

    public void hideLayouts() {
        for (TraitLayout layout : traitLayouts) {
            layout.setVisibility(View.GONE);
        }
    }

    public void deleteTraitListener(String format) {
        getTraitLayout(format).deleteTraitListener();
    }

    public void setNaTraitsText(String format) {
        getTraitLayout(format).setNaTraitsText();
    }

    public void enableViews() {
        for (LinearLayout traitLayout : traitLayouts) {
            MainActivity.enableViews(traitLayout);
        }
    }

    public void disableViews() {
        for (LinearLayout traitLayout : traitLayouts) {
            MainActivity.disableViews(traitLayout);
        }
    }
}