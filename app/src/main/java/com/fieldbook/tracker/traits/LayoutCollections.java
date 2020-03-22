package com.fieldbook.tracker.traits;

import android.app.Activity;
import android.view.View;
import android.widget.LinearLayout;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.CollectActivity;

import java.util.ArrayList;

public class LayoutCollections {
    private ArrayList<BaseTraitLayout> traitLayouts;

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
            BaseTraitLayout layout = _activity.findViewById(traitID);
            layout.init();
            traitLayouts.add(layout);
        }
    }

    public BaseTraitLayout getTraitLayout(final String trait) {
        for (BaseTraitLayout layout : traitLayouts) {
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
        for (BaseTraitLayout layout : traitLayouts) {
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
            CollectActivity.enableViews(traitLayout);
        }
    }

    public void disableViews() {
        for (LinearLayout traitLayout : traitLayouts) {
            CollectActivity.disableViews(traitLayout);
        }
    }
}