package com.fieldbook.tracker.traits;

import android.app.Activity;

import java.util.ArrayList;

public class LayoutCollections {
    private final Activity activity;
    private final ArrayList<BaseTraitLayout> traitLayouts;
    private boolean treeLayoutsRegistered = false;

    public LayoutCollections(Activity _activity) {
        this(_activity, defaultLayouts(_activity));
    }

    /**
     * Test / injection entry: seed non-tree layouts without constructing Hilt camera traits.
     * Tree layouts are still lazy-registered on first tree-format resolve.
     */
    LayoutCollections(Activity _activity, ArrayList<BaseTraitLayout> seedLayouts) {
        activity = _activity;
        traitLayouts = seedLayouts;
    }

    private static ArrayList<BaseTraitLayout> defaultLayouts(Activity activity) {
        ArrayList<BaseTraitLayout> layouts = new ArrayList<>();
        layouts.add(new TextTraitLayout(activity));
        layouts.add(new NumericTraitLayout(activity));
        layouts.add(new AngleTraitLayout(activity));
        layouts.add(new AudioTraitLayout(activity));
        layouts.add(new BarcodeTraitLayout(activity));
        layouts.add(new BooleanTraitLayout(activity));
        layouts.add(new CategoricalTraitLayout(activity));
        layouts.add(new CounterTraitLayout(activity));
        layouts.add(new DateTraitLayout(activity));
        layouts.add(new DiseaseRatingTraitLayout(activity));
        layouts.add(new GNSSTraitLayout(activity));
        layouts.add(new LabelPrintTraitLayout(activity));
        layouts.add(new LocationTraitLayout(activity));
        layouts.add(new PercentTraitLayout(activity));
        layouts.add(new PhotoTraitLayout(activity));
        layouts.add(new UsbCameraTraitLayout(activity));
        layouts.add(new GoProTraitLayout(activity));
        layouts.add(new CanonTraitLayout(activity));
        layouts.add(new VideoTraitLayout(activity));
        layouts.add(new SpectralTraitLayout(activity));
        layouts.add(new NixTraitLayout(activity));
        layouts.add(new InnoSpectraTraitLayout(activity));
        layouts.add(new StopWatchTraitLayout(activity));
        layouts.add(new GreenSeekerTraitLayout(activity));
        layouts.add(new ScaleTraitLayout(activity));
        // Tree layouts are registered lazily on first tree-format resolve.
        return layouts;
    }

    /**
     * Constructs and registers tree layouts only when a tree format is requested.
     * Collect cold start with no tree traits never allocates them.
     */
    private void ensureTreeLayoutsRegistered(String traitFormat) {
        if (treeLayoutsRegistered) {
            return;
        }
        if (TreeTraitLayout.type.equals(traitFormat)
                || TreeSummaryTraitLayout.type.equals(traitFormat)) {
            traitLayouts.add(new TreeTraitLayout(activity));
            traitLayouts.add(new TreeSummaryTraitLayout(activity));
            treeLayoutsRegistered = true;
        }
    }

    /**
     * @param traitFormat the trait layout's format
     * @return the trait layout corresponding to the format
     */
    public BaseTraitLayout getTraitLayout(final String traitFormat) {
        ensureTreeLayoutsRegistered(traitFormat);
        for (BaseTraitLayout layout : traitLayouts) {
            if (layout.isTraitType(traitFormat)) {
                return layout;
            }
        }
        android.util.Log.w("LayoutCollections", "No layout for format '" + traitFormat + "', falling back to text");
        for (BaseTraitLayout layout : traitLayouts) {
            if (layout.isTraitType("text")) {
                return layout;
            }
        }
        return traitLayouts.get(0);
    }

    public void deleteTraitListener(String format) {
        getTraitLayout(format).deleteTraitListener();
    }

    public void setNaTraitsText(String format) {
        getTraitLayout(format).setNaTraitsText();
        getTraitLayout(format).setCurrentValueAsEdited();
    }

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
