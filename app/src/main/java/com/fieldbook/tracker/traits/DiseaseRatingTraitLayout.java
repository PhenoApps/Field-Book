package com.fieldbook.tracker.traits;

import android.app.Activity;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;

import androidx.recyclerview.widget.RecyclerView;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.adapters.DiseaseRatingAdapter;
import com.fieldbook.tracker.database.models.ObservationModel;
import com.fieldbook.tracker.traits.formats.parameters.SeveritiesParameter;
import com.fieldbook.tracker.utilities.CategoryJsonUtil;
import com.fieldbook.tracker.utilities.Utils;
import org.brapi.v2.model.pheno.BrAPIScaleValidValuesCategories;

import java.util.ArrayList;
import java.util.List;

import kotlin.Unit;

public class DiseaseRatingTraitLayout extends BaseTraitLayout {

    Button rustR, rustM, rustS, rustDelim;
    RecyclerView severityGrid;
    DiseaseRatingAdapter severityAdapter;

    public DiseaseRatingTraitLayout(Context context) {
        super(context);
    }

    public DiseaseRatingTraitLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DiseaseRatingTraitLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setNaTraitsText() {
    }

    @Override
    public String type() {
        return "disease rating";
    }

    @Override
    public int layoutId() {
        return R.layout.trait_disease_rating;
    }

    public boolean isTraitType(String trait) {
        return trait.equals("rust rating") || trait.equals("disease rating");
    }

    @Override
    public void init(Activity act) {
        severityGrid = act.findViewById(R.id.severityGrid);
        rustR = act.findViewById(R.id.rustR);
        rustM = act.findViewById(R.id.rustM);
        rustS = act.findViewById(R.id.rustS);
        rustDelim = act.findViewById(R.id.rustDelim);

        severityAdapter = new DiseaseRatingAdapter(value -> {
            handleSeverityClick(value);
            return Unit.INSTANCE;
        });

        androidx.recyclerview.widget.GridLayoutManager layoutManager =
                new androidx.recyclerview.widget.GridLayoutManager(getContext(), 6);
        severityGrid.setLayoutManager(layoutManager);
        severityGrid.setAdapter(severityAdapter);

        rustR.setOnClickListener(new RustButtonOnClickListener());
        rustM.setOnClickListener(new RustButtonOnClickListener());
        rustS.setOnClickListener(new RustButtonOnClickListener());
        rustDelim.setOnClickListener(new RustButtonOnClickListener());
    }

    private List<String> getSeverityCodes() {
        List<String> codes = new ArrayList<>();

        if (getCurrentTrait() != null) {
            String categories = getCurrentTrait().getCategories();
            if (categories != null && !categories.isEmpty()) {
                try {
                    ArrayList<BrAPIScaleValidValuesCategories> cats =
                            CategoryJsonUtil.Companion.decodeCategories(categories);
                    if (!cats.isEmpty()) {
                        for (BrAPIScaleValidValuesCategories cat : cats) {
                            codes.add(cat.getValue());
                        }
                        return codes;
                    }
                } catch (Exception e) {
                    // fall through to defaults
                }
            }
        }

        // fallback to defaults
        codes.addAll(SeveritiesParameter.Companion.getDEFAULT_SEVERITIES());
        return codes;
    }

    @Override
    public void loadLayout() {
        super.loadLayout();

        // refresh severity buttons from trait categories
        severityAdapter.submitList(getSeverityCodes());

        ObservationModel model = getCurrentObservation();
        if (model != null) {
            getCollectInputView().setText(model.getValue());
        }
    }

    @Override
    public void deleteTraitListener() {
        removeTrait(getCurrentTrait());
        super.deleteTraitListener();

        ObservationModel model = getCurrentObservation();
        if (model != null) {
            getCollectActivity().setTitle(model.getValue());
        } else {
            getCollectInputView().setText("");
        }
    }

    private void handleSeverityClick(String v) {
        triggerTts(v);

        if (getVisibility() == View.VISIBLE) {
            String textValue = getCollectInputView().getText();
            if (textValue.length() > 0
                    && !textValue.endsWith("/")) {

                String lastChar = textValue.substring(textValue.length() - 1);
                if (!lastChar.matches("^[a-zA-Z]*$")) {
                    v = ":" + v;
                }
            }

            if (textValue.matches(".*\\d.*")
                    && v.matches(".*\\d.*")
                    && !textValue.contains("/")) {
                String error = getContext().getString(R.string.trait_error_disease_severity);
                Utils.makeToast(getContext(), error);
                triggerTts(error);
            } else {
                String value = textValue + v;
                getCollectInputView().setText(value);
                updateObservation(getCurrentTrait(), value);
            }
        }
    }

    private class RustButtonOnClickListener implements OnClickListener {

        @Override
        public void onClick(View view) {
            String v = "";
            if (view.getId() == R.id.rustR) {
                v = "R";
            } else if (view.getId() == R.id.rustM) {
                v = "M";
            } else if (view.getId() == R.id.rustS) {
                v = "S";
            } else if (view.getId() == R.id.rustDelim) {
                v = "/";
            }

            triggerTts(v);

            if (getVisibility() == View.VISIBLE) {
                String textValue = getCollectInputView().getText();
                if (textValue.length() > 0
                        && !v.equals("/")
                        && !textValue.endsWith("/")) {

                    String lastChar = textValue.substring(textValue.length() - 1);
                    if (!lastChar.matches("^[a-zA-Z]*$")) {
                        v = ":" + v;
                    }
                }

                String value = textValue + v;
                getCollectInputView().setText(value);
                updateObservation(getCurrentTrait(), value);
            }
        }
    }
}
