package com.fieldbook.tracker.traits;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.CollectActivity;
import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.database.models.ObservationModel;
import com.fieldbook.tracker.interfaces.CollectController;
import com.fieldbook.tracker.objects.RangeObject;
import com.fieldbook.tracker.objects.TraitObject;
import com.fieldbook.tracker.preferences.PreferenceKeys;
import com.fieldbook.tracker.traits.formats.Formats;
import com.fieldbook.tracker.traits.formats.feature.DisplayValue;
import com.fieldbook.tracker.views.CollectInputView;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public abstract class BaseTraitLayout extends LinearLayout {

    //tracks if data can be entered or not
    //references the collect activity locked state (locked, unlocked or frozen)
    protected boolean isLocked = false;

    protected CollectController controller;

    /** Inflated trait XML root for [findTraitView]; set by [init(Activity, View)]. */
    @Nullable
    private View traitBindRoot;

    /** When set (tree nodes), value I/O bypasses Collect's plot observation pipeline. */
    @Nullable
    private TraitValueSession valueSession;

    public BaseTraitLayout(Context context) {
        super(context);
        initController(context);
    }

    public BaseTraitLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        initController(context);
    }

    public BaseTraitLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initController(context);
    }

    private void initController(Context context) {
        if (context instanceof CollectController) {
            this.controller = (CollectController) context;
        }
    }

    /**
     * Attach a value session before [init] / [loadNodeValue]. Collect path leaves this null
     * and uses CollectActivity helpers directly. Nodes attach [NodeTraitValueSession].
     */
    public void attachSession(@NonNull TraitValueSession session) {
        this.valueSession = session;
        isLocked = session.isLocked();
    }

    public void setLockedState(boolean locked) {
        isLocked = locked;
    }

    @Nullable
    public TraitValueSession getValueSession() {
        return valueSession;
    }

    public boolean hasNodeSession() {
        return valueSession instanceof NodeTraitValueSession;
    }

    public abstract int layoutId();

    public abstract String type();  // return trait type

    public String decodeValue(String value) { return value; }

    public boolean isTraitType(String trait) {
        return trait.equals(type());
    }

    /**
     * Bind controls. Prefer [init(Activity, View)] so views resolve under the inflated root
     * (required when multiple node fields share an activity).
     */
    public abstract void init(Activity act);

    /**
     * Bind using [root] for findViewById. Collect and tree nodes must call this with the
     * inflated trait layout view.
     */
    public void init(@NonNull Activity act, @NonNull View root) {
        this.traitBindRoot = root;
        // Collect leaves valueSession null (direct CollectActivity helpers).
        // Nodes call attachSession(NodeTraitValueSession) before init.
        init(act);
    }

    /**
     * Resolve a child under the bound trait root (not activity-wide). Falls back to activity
     * for legacy callers that only invoked [init(Activity)].
     */
    @NonNull
    @SuppressWarnings("unchecked")
    protected final <T extends View> T findTraitView(int id) {
        T found = findTraitViewOrNull(id);
        if (found != null) return found;
        throw new IllegalStateException("Missing trait view id=" + id);
    }

    /** Optional binding for IDs that exist only in some format XMLs. */
    @Nullable
    @SuppressWarnings("unchecked")
    protected final <T extends View> T findTraitViewOrNull(int id) {
        if (traitBindRoot != null) {
            // Node hosts: never fall back to activity-wide IDs (duplicate chrome).
            return traitBindRoot.findViewById(id);
        }
        if (getContext() instanceof Activity) {
            T found = ((Activity) getContext()).findViewById(id);
            if (found != null) return found;
        }
        return findViewById(id);
    }

    /**
     * Load a sidecar / node value without touching Collect's plot observation queries.
     */
    public void loadNodeValue(@Nullable String value) {
        boolean sessionLocked = valueSession != null && valueSession.isLocked();
        isLocked = sessionLocked;
        CollectActivity act = getContext() instanceof CollectActivity
                ? (CollectActivity) getContext() : null;
        if (value != null && !value.isEmpty()) {
            getCollectInputView().setText(value);
            if (act != null) {
                afterLoadExists(act, value);
            }
        } else if (act != null) {
            afterLoadNotExists(act);
        }
        // afterLoad* may overwrite from Collect freeze/lock — restore node session lock.
        if (hasNodeSession()) {
            isLocked = sessionLocked;
        }
    }

    /**
     * validate is used in collect activity to check if the collected data is within the
     * specification of the trait, such as min/max.
     */
    @NonNull
    public Boolean validate(String data) {
        return true;
    }

    /**
     * Override to block multi-measure navigation with specific condition
     */
    public boolean block() { return false; }

    /**
     * Useful function when traits have listeners.
     * @param onNew
     */
    public void refreshLayout(Boolean onNew) {

        // Node hosts (StopWatch, Scale, …) may not be CollectActivity — never cast first.
        if (hasNodeSession()) {
            isLocked = valueSession != null && valueSession.isLocked();
            return;
        }

        // When frozen with repeated measures, update isLocked per-observation so
        // existing rep values stay read-only while new empty reps remain editable.
        CollectActivity act = (CollectActivity) getContext();
        if (act.isFrozen() && getCollectInputView().isRepeatEnabled()) {
            isLocked = !getCollectInputView().getText().isEmpty();
        }

        if (getCollectInputView().isRepeatEnabled()) {
            getCollectInputView().getRepeatView().refresh();
        }

    }

    /**
     * Called when navigating to a new plot or trait with the same format.
     * Skips view inflation and only reloads data to prevent flickering.
     * Override in subclasses to customize refresh behavior.
     */
    public void onRefresh() {
        loadLayout();
    }

    public void loadLayout() {

        // Node hosts run outside CollectActivity (Constructor preview / tree Collect Compose).
        // Never cast getContext() to CollectActivity on that path — it aborts chrome setup
        // (StopWatch CircularTimer, Percent seekbar, Scale BLE UI, …) via ClassCastException.
        if (!hasNodeSession()) {
            ((CollectActivity) getContext()).refreshRepeatedValuesToolbarIndicator();
        }

        //right now text entry is disabled in the camera and photo traits
        //uris are too long to be nicely displayed in the current editTexts
        if (Formats.Companion.findTrait(type()) instanceof DisplayValue) {
            toggleVisibility(View.VISIBLE);
        } else toggleVisibility(View.GONE);

        //hide soft input if it is not the text format
        if (!type().equals(TextTraitLayout.type)) {

            InputMethodManager imm =
                    (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);

            try {
                imm.hideSoftInputFromWindow(getCollectInputView().getWindowToken(), 0);
            } catch (Exception e) {
                // Handle exception
            }
        }

        // Node-hosted controllers must not mutate Collect's carousel CollectInputView (H1).
        if (hasNodeSession()) {
            isLocked = valueSession != null && valueSession.isLocked();
            String sessionValue = getCollectInputView().getText();
            CollectActivity act = getContext() instanceof CollectActivity
                    ? (CollectActivity) getContext() : null;
            if (sessionValue != null && !sessionValue.isEmpty()) {
                if (act != null) {
                    afterLoadExists(act, sessionValue);
                } else {
                    loadNodeValue(sessionValue);
                }
            } else if (act != null) {
                afterLoadNotExists(act);
            } else {
                loadNodeValue("");
            }
            isLocked = valueSession != null && valueSession.isLocked();
            return;
        }

        CollectActivity act = (CollectActivity) getContext();

        isLocked = act.isFrozen() || act.isLocked();

        ObservationModel[] observations = getDatabase().getRepeatedValues(
                act.getStudyId(),
                act.getObservationUnit(),
                act.getTraitDbId()
        );

        //clear old list of repeated values each time a new trait is loaded
        act.getInputView().getRepeatView().clear();

        act.getInputView().getRepeatView().setDisplayColor(Color.parseColor(getDisplayColor()));

        if (observations.length > 0) {

            int repIndex = act.getInputView().getInitialIndex();

            String value = observations[observations.length - 1].getValue();

            if (repIndex != -1) {

                value = observations[repIndex - 1].getValue();

            }

            if (!value.isEmpty()) {

                for (ObservationModel m : observations) {
                    if (!m.getValue().isEmpty()) {
                        m.setValue(decodeValue(m.getValue()));
                    }
                }

                act.getInputView().setTextColor(Color.parseColor(getDisplayColor()));
            }

            act.getInputView().prepareObservationsExistMode(Arrays.asList(observations));


            afterLoadExists(act, value);

        } else {

            act.getInputView().prepareEmptyObservationsMode();

            getCollectInputView().setTextColor(Color.BLACK);

            checkDefaultValue();
        }
    }

    private void checkDefaultValue() {

        TraitObject trait = getCurrentTrait();
        CollectActivity act = (CollectActivity) getContext();

        if (trait.getDefaultValue() != null && !trait.getDefaultValue().isEmpty()) {

            String defaultValue = trait.getDefaultValue();
            getCollectInputView().setText(defaultValue);
            updateObservation(trait, defaultValue);
            afterLoadDefault(act);

        } else {
            act.getInputView().setHasData(false);
            afterLoadNotExists(act);
        }
    }

    public void afterLoadExists(CollectActivity act, @Nullable String value) {
        getCollectInputView().markObservationSaved();
        getCollectInputView().setTextColor(Color.parseColor(getDisplayColor()));
        //lock data if frozen or locked state
        isLocked = act.isFrozen() || act.isLocked();
    }

    public void afterLoadDefault(CollectActivity act) {
        //unlock data only if frozen
        isLocked = act.isLocked();
    }

    public void afterLoadNotExists(CollectActivity act) {
        //unlock data only if frozen
        isLocked = act.isLocked();
    }

    public void onExit() {}

    /**
     * Handles the repeated value view list state.
     * If this feature is enabled, the list will be modified and updated.
     */
    public void deleteTraitListener() {
        if (hasNodeSession()) {
            if (!isLocked) {
                removeTrait(getCurrentTrait());
                if (getPrefs().getBoolean(PreferenceKeys.DELETE_OBSERVATION_SOUND, false)) {
                    controller.getSoundHelper().playDelete();
                }
            }
            return;
        }
        if (!isLocked) {
            CollectInputView inputView = getCollectInputView();
            if (inputView.isRepeatEnabled()) {
                inputView.getRepeatView().userDeleteCurrentRep();
            }
            //check if sound on delete is enabled in preferences and play sound
            if (getPrefs().getBoolean(PreferenceKeys.DELETE_OBSERVATION_SOUND, false)) {
                controller.getSoundHelper().playDelete();
            }
        }
    }

    public abstract void setNaTraitsText();

    public void refreshLock() {

        if (getCurrentObservation() != null) {
            // If there is an existing observation for the current rep, lock if frozen or locked
            isLocked = getCollectActivity().isFrozen() || getCollectActivity().isLocked();
        } else {
            // If there is no existing observation, only lock if locked (not frozen)
            isLocked = getCollectActivity().isLocked();
        }
    }

    public TraitObject getCurrentTrait() {
        if (valueSession != null) {
            return valueSession.currentTrait();
        }
        return ((CollectActivity) getContext()).getCurrentTrait();
    }

    public SharedPreferences getPrefs() {
        return PreferenceManager.getDefaultSharedPreferences(getContext());
    }

    public CollectActivity getCollectActivity() {
        return (CollectActivity) getContext();
    }

    public RangeObject getCurrentRange() {
        return ((CollectActivity) getContext()).getCRange();
    }

    public CollectInputView getCollectInputView() {
        if (valueSession != null) {
            return valueSession.inputView();
        }
        return ((CollectActivity) getContext()).getCollectInputView();
    }

    public String getDisplayColor() {
        return String.format("#%06X", (0xFFFFFF & PreferenceManager.getDefaultSharedPreferences(getContext())
                .getInt(PreferenceKeys.SAVED_DATA_COLOR, resolveThemeColor(R.attr.fb_value_saved_color))));
    }

    public int getButtonTextColor() {
        return resolveThemeColor(R.attr.fb_button_text_color);
    }

    public int getButtonBackgroundColor() {
        return resolveThemeColor(R.attr.fb_button_color_normal);
    }

    public int getButtonPressedColor() {
        return resolveThemeColor(R.attr.fb_trait_categorical_button_press_color);
    }

    public int getTextColor() {
        return resolveThemeColor(R.attr.fb_color_text_dark);
    }

    public int getValueAlteredColor() {
        return resolveThemeColor(R.attr.fb_value_altered_color);
    }

    private int resolveThemeColor(int resid) {
        TypedValue value = new TypedValue();
        getContext().getTheme().resolveAttribute(resid, value, true);
        return value.data;
    }

    /**
     * Function that toggles visibility of the edit text or repeated values view based on preferences
     */
    protected void toggleVisibility(int visibility) {

        CollectInputView inputView = getCollectInputView();

        inputView.setVisibility(visibility);

        inputView.updateInputViewVisibility(visibility);

        // Clear hint for NA since a focus change doesn't happen for the numeric trait layout
        if (!inputView.isRepeatEnabled()) {
            EditText editText = inputView.getEditText();
            if (isTraitType(TextTraitLayout.type)
                    || isTraitType(AudioTraitLayout.type)
                    || isTraitType(PhotoTraitLayout.type)) {
                editText.setVisibility(View.GONE);
            } else {
                editText.setVisibility(View.VISIBLE);
            }
        }
    }

    /**
     * Calls the collect activities db function to insert an observation row.
     *
     * @param trait the trait object to update
     * @param value the Text value to be saved in the row
     */
    public void updateObservation(TraitObject trait, String value) {
        if (valueSession != null) {
            valueSession.commit(trait, value);
            setCurrentValueAsEdited();
            if (!(valueSession instanceof NodeTraitValueSession)) {
                handleAutoSwitchToNextPlot(trait);
            }
            return;
        }
        ((CollectActivity) getContext()).updateObservation(trait, value, null);

        setCurrentValueAsEdited();
        handleAutoSwitchToNextPlot(trait);
    }

    protected void handleAutoSwitchToNextPlot(TraitObject trait) {
        if (trait.getAutoSwitchPlot() && controller != null) {
            controller.getRangeBox().moveEntryRight();
        }
    }

    public void removeTrait(TraitObject trait) {
        if (valueSession != null) {
            valueSession.clear(trait);
            return;
        }
        ((CollectActivity) getContext()).removeTrait(trait);
    }

    /** Clear node sidecar value, or remove the Collect plot observation. */
    protected void clearObservationOrRemoveTrait() {
        if (hasNodeSession()) {
            removeTrait(getCurrentTrait());
        } else {
            ((CollectActivity) getContext()).removeTrait();
        }
    }

    public void triggerTts(String text) {
        if (valueSession instanceof NodeTraitValueSession) {
            return;
        }
        if (getContext() instanceof CollectActivity) {
            ((CollectActivity) getContext()).triggerTts(text);
        }
    }

    protected List<ObservationModel> getObservations() {
        if (hasNodeSession()) {
            // Node values live in the session buffer, not Collect repeated-measure rows.
            return Collections.emptyList();
        }
        CollectActivity act = getCollectActivity();
        return Arrays.asList(getDatabase().getRepeatedValues(act.getStudyId(), act.getObservationUnit(), act.getTraitDbId()));
    }

    protected ObservationModel getCurrentObservation() {
        if (hasNodeSession()) {
            return null;
        }
        String rep = getCollectInputView().getRep();
        List<ObservationModel> models = getObservations();
        for (ObservationModel m : models) {
            if (rep.equals(m.getRep())) {
                return m;
            }
        }
        return null;
    }

    protected DataHelper getDatabase() { return controller.getDatabase(); }

    protected void setCurrentValueAsEdited() {
        getCollectInputView().markObservationEdited();
        getCollectInputView().setTextColor(getTextColor());
    }
}