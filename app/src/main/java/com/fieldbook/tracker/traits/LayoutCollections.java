package com.fieldbook.tracker.traits;

import android.app.Activity;

import java.util.ArrayList;

public class LayoutCollections {
    private final ArrayList<BaseTraitLayout> traitLayouts;

    public LayoutCollections(Activity _activity) {
        traitLayouts = new ArrayList<>();
        traitLayouts.add(new TextTraitLayout(_activity));
        traitLayouts.add(new NumericTraitLayout(_activity));
        traitLayouts.add(new AngleTraitLayout(_activity));
        traitLayouts.add(new AudioTraitLayout(_activity));
        traitLayouts.add(new BarcodeTraitLayout(_activity));
        traitLayouts.add(new BooleanTraitLayout(_activity));
        traitLayouts.add(new CategoricalTraitLayout(_activity));
        traitLayouts.add(new CounterTraitLayout(_activity));
        traitLayouts.add(new DateTraitLayout(_activity));
        traitLayouts.add(new DiseaseRatingTraitLayout(_activity));
        traitLayouts.add(new GNSSTraitLayout(_activity));
        traitLayouts.add(new LabelPrintTraitLayout(_activity));
        traitLayouts.add(new LocationTraitLayout(_activity));
        traitLayouts.add(new PercentTraitLayout(_activity));
        traitLayouts.add(new PhotoTraitLayout(_activity));
        traitLayouts.add(new UsbCameraTraitLayout(_activity));
        traitLayouts.add(new GoProTraitLayout(_activity));
        traitLayouts.add(new CanonTraitLayout(_activity));
        traitLayouts.add(new SpectralTraitLayout(_activity));
        traitLayouts.add(new NixTraitLayout(_activity));
        traitLayouts.add(new InnoSpectraNanoTraitLayout(_activity));
        traitLayouts.add(new StopWatchTraitLayout(_activity));
        traitLayouts.add(new GreenSeekerTraitLayout(_activity));
        traitLayouts.add(new ScaleTraitLayout(_activity));
    }

    /**
     * @param traitFormat the trait layout's format
     * @return the trait layout corresponding to the format
     */
    public BaseTraitLayout getTraitLayout(final String traitFormat) {
        for (BaseTraitLayout layout : traitLayouts) {
            if (layout.isTraitType(traitFormat)) {
                return layout;
            }
        }
        return getTraitLayout("text");
    }

    public AbstractCameraTrait getPhotoTrait(String format) {
        switch (format) {
            case "photo":
                return (PhotoTraitLayout) getTraitLayout("photo");
        }
        return (PhotoTraitLayout) getTraitLayout("photo");
    }

    public void deleteTraitListener(String format) {
        getTraitLayout(format).deleteTraitListener();
    }

    public void setNaTraitsText(String format) {
        getTraitLayout(format).setNaTraitsText();
        getTraitLayout(format).setCurrentValueAsEdited();
    }

//    Deprecated - simpler to disable/enable data collection using lockOverlay instead
//
//    public void enableViews() {
//        for (LinearLayout traitLayout : traitLayouts) {
//            enableViews(true, traitLayout);
//        }
//    }
//
//    public void disableViews() {
//        for (BaseTraitLayout traitLayout : traitLayouts) {
//            String type = traitLayout.type();
//            if (!type.equals("photo") && !type.equals("audio") && !type.equals("percent"))
//                enableViews(false, traitLayout);
//        }
//    }
//
//    public void enableViews(Boolean toggle, ViewGroup layout) {
//        layout.setEnabled(false);
//        for (int i = 0; i < layout.getChildCount(); i++) {
//            View child = layout.getChildAt(i);
//            if (child instanceof ViewGroup) {
//                enableViews(toggle, (ViewGroup) child);
//            } else {
//                child.setEnabled(toggle);
//            }
//        }
//    }

    public void registerAllReceivers() {
        for (BaseTraitLayout layout : this.traitLayouts) {
            if (layout instanceof LabelPrintTraitLayout) {
                ((LabelPrintTraitLayout) layout).registerReceiver();
            }
        }
    }

    public void unregisterAllReceivers() {
        for (BaseTraitLayout layout : this.traitLayouts) {
            if (layout instanceof LabelPrintTraitLayout) {
                ((LabelPrintTraitLayout) layout).unregisterReceiver();
            }
        }
    }

    /**
     * Triggers trait specific code for refreshing lock status.
     * Some traits may need to refresh UI.
     * Called when range box or trait box moves.
     * @param trait the trait name s.a height
     */
    public void refreshLock(String trait) {
        for (BaseTraitLayout traitLayout : traitLayouts) {
            if (traitLayout.type().equals(trait)) {
                traitLayout.refreshLock();
            }
        }
    }
}