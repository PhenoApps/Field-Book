package com.fieldbook.tracker.adapters;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.RecyclerView;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.StatisticsActivity;
import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.database.models.ObservationModel;
import com.fieldbook.tracker.utilities.CategoryJsonUtil;
import com.fieldbook.tracker.utilities.FileUtil;
import com.fieldbook.tracker.utilities.Utils;

import org.brapi.v2.model.pheno.BrAPIScaleValidValuesCategories;
import org.phenoapps.utils.BaseDocumentTreeUtil;

import java.io.OutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class StatisticsAdapter extends RecyclerView.Adapter<StatisticsAdapter.ViewHolder> {

    StatisticsActivity originActivity;
    DataHelper database;
    List<String> seasons;
    private final SimpleDateFormat timeStampFormat;
    private static final String TIME_FORMAT_PATTERN = "yyyy-MM-dd HH:mm:ss.SSSZZZZZ";
    private final SimpleDateFormat dateFormat;
    private static final String DATE_FORMAT_PATTERN = "yyyy-MM-dd";
    private int toggleVariable;


    public StatisticsAdapter(StatisticsActivity context, List<String> seasons, int toggleVariable) {
        this.originActivity = context;
        this.database = originActivity.getDatabase();
        this.seasons = seasons;
        this.timeStampFormat = new SimpleDateFormat(TIME_FORMAT_PATTERN, Locale.getDefault());
        this.dateFormat = new SimpleDateFormat(DATE_FORMAT_PATTERN, Locale.getDefault());
        this.toggleVariable = toggleVariable;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        TextView statValue1, statValue2, statValue3, statValue4, statValue5, statValue6, statValue7, statValue8, year_text_view;
        LinearLayout stat1, stat2, stat3, stat4, stat5, stat6, stat7, stat8;
        ConstraintLayout statisticsCard;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            statisticsCard = itemView.findViewById(R.id.statistics_card);

            statValue1 = itemView.findViewById(R.id.stat_value_1);
            statValue2 = itemView.findViewById(R.id.stat_value_2);
            statValue3 = itemView.findViewById(R.id.stat_value_3);
            statValue4 = itemView.findViewById(R.id.stat_value_4);
            statValue5 = itemView.findViewById(R.id.stat_value_5);
            statValue6 = itemView.findViewById(R.id.stat_value_6);
            statValue7 = itemView.findViewById(R.id.stat_value_7);
            statValue8 = itemView.findViewById(R.id.stat_value_8);
            year_text_view = itemView.findViewById(R.id.year_text_view);

            stat1 = itemView.findViewById(R.id.stat_1);
            stat2 = itemView.findViewById(R.id.stat_2);
            stat3 = itemView.findViewById(R.id.stat_3);
            stat4 = itemView.findViewById(R.id.stat_4);
            stat5 = itemView.findViewById(R.id.stat_5);
            stat6 = itemView.findViewById(R.id.stat_6);
            stat7 = itemView.findViewById(R.id.stat_7);
            stat8 = itemView.findViewById(R.id.stat_8);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View statsCardView = LayoutInflater.from(parent.getContext()).inflate(R.layout.statistics_card, parent, false);
        return new ViewHolder(statsCardView);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {

        ObservationModel[] observations = database.getAllObservationsFromAYear(seasons.get(position));

        Set<String> fields = new HashSet<>();
        Set<String> observationUnits = new HashSet<>();
        Set<String> collectors = new HashSet<>();
        ArrayList<Date> dateObjects = new ArrayList<>();
        Map<String, Integer> dateCount = new HashMap<>();
        Map<String, Integer> observationCount = new HashMap<>();
        int imageCount = 0;

        for (ObservationModel observation : observations) {

            fields.add(observation.getStudy_id());

            observationUnits.add(observation.getObservation_unit_id());

            String collector = observation.getCollector();
            if (collector != null && !collector.trim().isEmpty()) {
                collectors.add(collector);
            }

            String time = observation.getObservation_time_stamp();
            Date dateObject = null;
            try {
                dateObject = timeStampFormat.parse(time);
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
            dateObjects.add(dateObject);

            if (observation.getObservation_variable_field_book_format().equals("photo")) {
                imageCount++;
            }

            String date = new SimpleDateFormat("MM-dd-yy").format(dateObject);
            dateCount.put(date, dateCount.getOrDefault(date, 0) + 1);

            String observationUnitId = observation.getObservation_unit_id();
            observationCount.put(observationUnitId, observationCount.getOrDefault(observationUnitId, 0) + 1);

        }

        long totalInterval = 0;
        for (int i = 1; i< dateObjects.size(); i++){
            long diff = dateObjects.get(i).getTime() - dateObjects.get(i-1).getTime();
            if (diff <= TimeUnit.MINUTES.toMillis(30)){
                totalInterval += TimeUnit.MILLISECONDS.toSeconds(diff);
            }
        }
        String timeString = String.format("%.2f", totalInterval / 3600.0);

        int maxObservationsInADay = 0;
        String dateWithMostObservations = null;
        for (Map.Entry<String, Integer> entry : dateCount.entrySet()) {
            if (entry.getValue() > maxObservationsInADay) {
                maxObservationsInADay = entry.getValue();
                dateWithMostObservations = entry.getKey();
            }
        }

        int maxObservationsOnSingleUnit = 0;
        String unitWithMostObservations = null;
        for (Map.Entry<String, Integer> entry : observationCount.entrySet()) {
            if (entry.getValue() > maxObservationsOnSingleUnit) {
                maxObservationsOnSingleUnit = entry.getValue();
                unitWithMostObservations = entry.getKey();
            }
        }

        holder.year_text_view.setText(seasons.get(position));
        holder.statValue1.setText(String.valueOf(fields.size()));
        holder.statValue2.setText(String.valueOf(observationUnits.size()));
        holder.statValue3.setText(String.valueOf(observations.length));
        holder.statValue4.setText(timeString);
        holder.statValue5.setText(String.valueOf(collectors.size()));
        holder.statValue6.setText(String.valueOf(imageCount));
        holder.statValue7.setText(dateWithMostObservations);
        holder.statValue8.setText(String.valueOf(maxObservationsOnSingleUnit));

        holder.stat1.setOnClickListener(view -> {
            List<String> fieldNames = new ArrayList<>();
            for (String field: fields) {
                fieldNames.add(database.getFieldObject(Integer.valueOf(field)).getExp_name());
            }
            displayDialog(R.string.stat1_title, fieldNames);
        });

        holder.stat2.setOnClickListener(view -> Utils.makeToast(originActivity, observationUnits.size() + " plots have had data collected"));
        holder.stat3.setOnClickListener(view -> Utils.makeToast(originActivity, observations.length + " total observations have been collected"));
        holder.stat4.setOnClickListener(view -> Utils.makeToast(originActivity, timeString + " hours have been spent in collecting the data"));
        holder.stat5.setOnClickListener(view -> displayDialog(R.string.stat5_title, new ArrayList<>(collectors)));

        int finalImageCount = imageCount;
        holder.stat6.setOnClickListener(view -> Utils.makeToast(originActivity, finalImageCount + " photos have been clicked"));

        int finalMaxObservationsInADay = maxObservationsInADay;
        String finalDateWithMostObservations = dateWithMostObservations;
        holder.stat7.setOnClickListener(view -> Utils.makeToast(originActivity, finalMaxObservationsInADay + " observations were collected on " + finalDateWithMostObservations));

        String finalUnitWithMostObservations = unitWithMostObservations;
        holder.stat8.setOnClickListener(view -> {
            List<String> data = new ArrayList<>();
            for (ObservationModel observation : observations) {
                if (observation.getObservation_unit_id().equals(finalUnitWithMostObservations)) {
                    final String traitFormat = observation.getObservation_variable_field_book_format();
                    if (traitFormat.equals("categorical") || traitFormat.equals("multicat") || traitFormat.equals("qualitative"))
                        data.add(observation.getObservation_variable_name() + ": " + decodeCategorical(observation.getValue()));
                    else if (traitFormat.equals("photo"))
                        data.add(observation.getObservation_variable_name() + ": " + "<image>");
                    else
                        data.add(observation.getObservation_variable_name() + ": " + observation.getValue());
                }
            }
            displayDialog(R.string.stat8_title, data);
        });

        holder.statisticsCard.setOnLongClickListener(view -> {
            exportCard(holder);
            return true;
        });

    }

    @Override
    public int getItemCount() {
        return seasons.size();
    }

    /**
     * Displays a dialog with the list of matching items of a statistic
     * @param titleStringId: title of the dialog
     * @param data list of items to be displayed
     */
    public void displayDialog(int titleStringId, List<String> data) {

        if (data.size() == 0) {
            Utils.makeToast(originActivity, originActivity.getString(R.string.warning_no_data));
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(originActivity, R.style.AppAlertDialog);

        View layout = originActivity.getLayoutInflater().inflate(R.layout.dialog_individual_statistics, null);
        builder.setTitle(titleStringId).setView(layout);
        builder.setNegativeButton(R.string.dialog_close, (dialogInterface, id) -> dialogInterface.dismiss());

        final AlertDialog dialog = builder.create();

        ListView statsList = layout.findViewById(R.id.statsList);
        statsList.setAdapter(new StatisticsListAdapter(originActivity, data));

        dialog.show();
    }

    /**
     * Exports a statistics card as an image
     * @param holder
     */
    public void exportCard(ViewHolder holder) {
        Bitmap cardBitmap = Bitmap.createBitmap(holder.statisticsCard.getWidth(), holder.statisticsCard.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas cardCanvas = new Canvas(cardBitmap);
        cardCanvas.drawColor(Color.WHITE);
        holder.statisticsCard.draw(cardCanvas);

        // Adding the field book logo
        Drawable drawable = ContextCompat.getDrawable(originActivity, R.mipmap.ic_launcher);
        Bitmap logoBitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth() / 2, drawable.getIntrinsicHeight() / 2, Bitmap.Config.ARGB_8888);
        Canvas logoCanvas = new Canvas(logoBitmap);
        drawable.setBounds(0, 0, logoCanvas.getWidth(), logoCanvas.getHeight());
        drawable.draw(logoCanvas);
        cardCanvas.drawBitmap(logoBitmap, cardBitmap.getWidth() - logoBitmap.getWidth() - 10, 10, null); // setting the co-ordinates for the logo with a padding of 10dp

        try {
            DocumentFile imagesDir = BaseDocumentTreeUtil.Companion.getDirectory(originActivity, R.string.dir_media_photos);
            if (imagesDir != null && imagesDir.exists()) {
                DocumentFile exportImage = imagesDir.createFile("image/jpg", holder.year_text_view.getText() + "_" + System.currentTimeMillis() + ".jpg");
                if (exportImage != null && exportImage.exists()) {
                    OutputStream outputStream = originActivity.getContentResolver().openOutputStream(exportImage.getUri());
                    if (outputStream != null) {
                        cardBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
                        outputStream.close();
                        FileUtil.shareFile(originActivity, originActivity.getPrefs(), exportImage);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String decodeCategorical(String value) {
        ArrayList<BrAPIScaleValidValuesCategories> cats = CategoryJsonUtil.Companion.decode(value);
        StringBuilder v = new StringBuilder(cats.get(0).getValue());
        if (cats.size() > 1) {
            for (int i = 1; i < cats.size(); i++)
                v.append(", ").append(cats.get(i).getValue());
        }
        return v.toString();
    }

}
