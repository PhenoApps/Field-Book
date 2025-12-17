package com.fieldbook.tracker.traits;

import android.app.Activity;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.database.models.ObservationModel;
import com.fieldbook.tracker.utilities.BackgroundUiTask;
import com.fieldbook.tracker.utilities.Utils;

import org.phenoapps.utils.BaseDocumentTreeUtil;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class DiseaseRatingTraitLayout extends BaseTraitLayout {

    private List<String> loadedRustCodes = null;
    Button rustR, rustM, rustS, rustDelim;
    Map<Integer, Button> rustButtons;

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
        rustButtons = new LinkedHashMap<>();
        rustButtons.put(R.id.rust0, act.findViewById(R.id.rust0));
        rustButtons.put(R.id.rust5, act.findViewById(R.id.rust5));
        rustButtons.put(R.id.rust10, act.findViewById(R.id.rust10));
        rustButtons.put(R.id.rust15, act.findViewById(R.id.rust15));
        rustButtons.put(R.id.rust20, act.findViewById(R.id.rust20));
        rustButtons.put(R.id.rust25, act.findViewById(R.id.rust25));
        rustButtons.put(R.id.rust30, act.findViewById(R.id.rust30));
        rustButtons.put(R.id.rust35, act.findViewById(R.id.rust35));
        rustButtons.put(R.id.rust40, act.findViewById(R.id.rust40));
        rustButtons.put(R.id.rust45, act.findViewById(R.id.rust45));
        rustButtons.put(R.id.rust50, act.findViewById(R.id.rust50));
        rustButtons.put(R.id.rust55, act.findViewById(R.id.rust55));
        rustButtons.put(R.id.rust60, act.findViewById(R.id.rust60));
        rustButtons.put(R.id.rust65, act.findViewById(R.id.rust65));
        rustButtons.put(R.id.rust70, act.findViewById(R.id.rust70));
        rustButtons.put(R.id.rust75, act.findViewById(R.id.rust75));
        rustButtons.put(R.id.rust80, act.findViewById(R.id.rust80));
        rustButtons.put(R.id.rust85, act.findViewById(R.id.rust85));
        rustButtons.put(R.id.rust90, act.findViewById(R.id.rust90));
        rustButtons.put(R.id.rust95, act.findViewById(R.id.rust95));
        rustButtons.put(R.id.rust100, act.findViewById(R.id.rust100));
        rustR = act.findViewById(R.id.rustR);
        rustM = act.findViewById(R.id.rustM);
        rustS = act.findViewById(R.id.rustS);
        rustDelim = act.findViewById(R.id.rustDelim);

        loadRustCodes();
    }

    private void loadRustCodes() {
        BackgroundUiTask.Companion.execute(
                continuation -> {
                    loadedRustCodes = getRustCodes();
                    return null;
                },
                continuation -> {
                    if (loadedRustCodes != null) {
                        setupRustButtons(loadedRustCodes);
                    }
                    return null;
                },
                continuation -> {
                    // show nothing if failed
                    return null;
                }
        );
    }


    private void setupRustButtons(List<String> rustCodes) {
        List<Button> rustBtnArray = new ArrayList<>(rustButtons.values());
        for (int i = 0; i < rustCodes.size(); i++) {
            rustBtnArray.get(i).setText(rustCodes.get(i));
            rustBtnArray.get(i).setOnClickListener(new RustButtonOnClickListener());
        }

        rustR.setOnClickListener(new RustButtonOnClickListener());
        rustM.setOnClickListener(new RustButtonOnClickListener());
        rustS.setOnClickListener(new RustButtonOnClickListener());
        rustDelim.setOnClickListener(new RustButtonOnClickListener());

        rustButtons.get(R.id.rust0).requestFocus();
    }

    private List<String> getRustCodes() {
        List<String> rustCodes = new ArrayList<>();
        String token1;
        Scanner inFile1 = null;

        try {
            InputStream severityInput = BaseDocumentTreeUtil.Companion
                    .getFileInputStream(getContext(), R.string.dir_trait, "severity.txt");

            if (severityInput != null) {

                inFile1 = new Scanner(severityInput);

            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        if (inFile1 != null) {
            while (inFile1.hasNext()) {
                token1 = inFile1.next();
                rustCodes.add(token1);
            }
            inFile1.close();

            //Trim list to 21 since only 21 buttons
            int k = rustCodes.size();
            if (k > 21) {
                rustCodes.subList(21, k).clear();
            }
        } else {
            rustCodes = Arrays.asList("0", "5", "10", "15", "20", "25", "30", "35", "40", "45", "50", "55", "60", "65", "70", "75", "80", "85", "90", "95", "100");
        }
        return rustCodes;
    }

    @Override
    public void loadLayout() {
        super.loadLayout();

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
            } else {
                v = rustButtons.get(view.getId()).getText().toString();
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

                if (textValue.matches(".*\\d.*")
                        && v.matches(".*\\d.*")
                        && !textValue.contains("/")) {
                    String error = getContext().getString(R.string.trait_error_disease_severity);
                    Utils.makeToast(getContext(),error);
                    triggerTts(error);
                } else {
                    String value = textValue + v;
                    getCollectInputView().setText(value);
                    updateObservation(getCurrentTrait(), value);
                }
            }
        }
    }
}