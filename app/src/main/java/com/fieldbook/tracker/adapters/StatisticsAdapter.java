package com.fieldbook.tracker.adapters;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.StatisticsActivity;
import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.database.models.ObservationModel;
import com.fieldbook.tracker.objects.FieldObject;
import com.fieldbook.tracker.objects.StatisticObject;
import com.fieldbook.tracker.objects.TraitObject;
import com.fieldbook.tracker.traits.formats.Formats;
import com.fieldbook.tracker.traits.formats.TraitFormat;
import com.fieldbook.tracker.traits.formats.coders.StringCoder;
import com.fieldbook.tracker.traits.formats.presenters.ValuePresenter;
import com.fieldbook.tracker.utilities.CategoryJsonUtil;
import com.fieldbook.tracker.utilities.FileUtil;

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
    private final SimpleDateFormat failTimeStampFormat;
    private static final String TIME_STAMP_PATTERN = "yyyy-MM-dd HH:mm:ss.SSSZZZZZ";
    private static final String FAIL_TIME_STAMP_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final String DATE_FORMAT_PATTERN = "MM-dd-yy";
    private static final String YEAR_MONTH_PATTERN = "yyyy-MM";
    private static final String MONTH_VIEW_CARD_TITLE_PATTERN ="MMMM yyyy";
    private final SimpleDateFormat yearMonthFormat;
    private final SimpleDateFormat monthViewCardTitle;
    private static final int intervalThreshold = 30;
    private Toast toast;
    StatisticsActivity.ToggleVariable cardType;

    public StatisticsAdapter(StatisticsActivity context, List<String> seasons, StatisticsActivity.ToggleVariable cardType) {
        this.originActivity = context;
        this.database = originActivity.getDatabase();
        this.seasons = seasons;
        this.timeStampFormat = new SimpleDateFormat(TIME_STAMP_PATTERN, Locale.getDefault());
        this.failTimeStampFormat = new SimpleDateFormat(FAIL_TIME_STAMP_PATTERN, Locale.getDefault());
        this.yearMonthFormat = new SimpleDateFormat(YEAR_MONTH_PATTERN, Locale.getDefault());
        this.monthViewCardTitle = new SimpleDateFormat(MONTH_VIEW_CARD_TITLE_PATTERN, Locale.getDefault());
        this.cardType = cardType;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        TextView year_text_view;
        ConstraintLayout statisticsCard;
        ImageView exportCard;
        RecyclerView rvStatsContainer;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            statisticsCard = itemView.findViewById(R.id.statistics_card);
            rvStatsContainer = itemView.findViewById(R.id.rv_stats_container);
            exportCard = itemView.findViewById(R.id.export_card);
            year_text_view = itemView.findViewById(R.id.year_text_view);
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
            Date dateObject;
            try {
                dateObject = timeStampFormat.parse(time);
            } catch (ParseException e) {
                try {
                    if (time != null) {
                        String split = time.split("\\.")[0];
                        dateObject = failTimeStampFormat.parse(split);
                    } else {
                        dateObject = new Date();
                    }
                } catch (ParseException pe) {
                    throw new RuntimeException(pe);
                }
            }
            dateObjects.add(dateObject);

            if (observation.getObservation_variable_field_book_format() != null) {
                if (Formats.Companion.isCameraTrait(observation.getObservation_variable_field_book_format())) {
                    imageCount++;
                }
            }

            String date = new SimpleDateFormat(DATE_FORMAT_PATTERN, Locale.getDefault()).format(dateObject);
            dateCount.put(date, dateCount.getOrDefault(date, 0) + 1);

            String observationUnitId = observation.getObservation_unit_id();
            observationCount.put(observationUnitId, observationCount.getOrDefault(observationUnitId, 0) + 1);

        }

        long totalInterval = 0;
        for (int i = 1; i< dateObjects.size(); i++){
            long diff = dateObjects.get(i).getTime() - dateObjects.get(i-1).getTime();
            if (diff <= TimeUnit.MINUTES.toMillis(intervalThreshold)){
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

        String cardTitle = "";
        Date date;

        switch (cardType) {
            case TOTAL: cardTitle = originActivity.getString(R.string.stats_tab_layout_total); break;
            case YEAR: cardTitle = seasons.get(position); break;
            case MONTH:
                try {
                    date = yearMonthFormat.parse(seasons.get(position));
                    cardTitle = monthViewCardTitle.format(date);
                } catch (ParseException e) {
                    throw new RuntimeException(e);
                }
        }

        holder.year_text_view.setText(cardTitle);

        List<String> fieldNames = new ArrayList<>();
        for (String field : fields) {
            FieldObject f = database.getFieldObject(Integer.valueOf(field));
            if (f != null && f.getName() != null) {
                fieldNames.add(f.getName());
            }
        }

        List<String> unitWithMostObservationsList = new ArrayList<>();
        for (ObservationModel observation : observations) {
            if (observation.getObservation_unit_id().equals(unitWithMostObservations)) {
                final String traitFormat = observation.getObservation_variable_field_book_format();
                TraitObject trait = database.getTraitById(String.valueOf(observation.getObservation_variable_db_id()));
                if (traitFormat != null) {

                    TraitFormat formats = Formats.Companion.findTrait(traitFormat);

                    Object valueModel = observation.getValue();

                    if (formats instanceof StringCoder) {

                        valueModel = ((StringCoder) formats).decode(valueModel.toString());

                    }

                    if (formats instanceof ValuePresenter) {

                        unitWithMostObservationsList.add(observation.getObservation_variable_name() + ": " + ((ValuePresenter) formats).represent(originActivity, valueModel, trait));

                    } else {

                        unitWithMostObservationsList.add(observation.getObservation_variable_name() + ": " + observation.getValue());

                    }

                }
            }
        }

        holder.exportCard.setOnClickListener(view -> exportCard(holder));

        List<StatisticObject> statisticObjectList = new ArrayList<>();

        statisticObjectList.add(new StatisticObject(originActivity.getString(R.string.stat_title_fields), String.valueOf(fields.size()), R.drawable.ic_stats_field, 0, originActivity.getString(R.string.stat_fields_dialog_title) + " " + seasons.get(position), fieldNames, ""));
        statisticObjectList.add(new StatisticObject(originActivity.getString(R.string.stat_title_entries), String.valueOf(observationUnits.size()), R.drawable.ic_stats_plot, 1, "", null, observationUnits.size() + " " + originActivity.getString(R.string.stat_entries_toast_message)));
        statisticObjectList.add(new StatisticObject(originActivity.getString(R.string.stat_title_data), String.valueOf(observations.length), R.drawable.ic_stats_observation, 1, "", null, observations.length + " " + originActivity.getString(R.string.stat_data_toast_message)));
        statisticObjectList.add(new StatisticObject(originActivity.getString(R.string.stat_title_hours), timeString, R.drawable.ic_stats_time, 1, "", null, timeString + " " + originActivity.getString(R.string.stat_hours_toast_message)));
        statisticObjectList.add(new StatisticObject(originActivity.getString(R.string.stat_title_people), String.valueOf(collectors.size()), R.drawable.ic_stats_people, 0, originActivity.getString(R.string.stat_people_dialog_title), new ArrayList<>(collectors), ""));
        statisticObjectList.add(new StatisticObject(originActivity.getString(R.string.stat_title_photos), String.valueOf(imageCount), R.drawable.ic_stats_photo, 1, "", null, imageCount + " " + originActivity.getString(R.string.stat_photos_toast_message)));
        statisticObjectList.add(new StatisticObject(originActivity.getString(R.string.stat_title_busiest), dateWithMostObservations, R.drawable.ic_stats_busiest, 1, "", null, maxObservationsInADay + " " + originActivity.getString(R.string.stat_busiest_toast_message) + " " + dateWithMostObservations));
        statisticObjectList.add(new StatisticObject(originActivity.getString(R.string.stat_title_most), String.valueOf(maxObservationsOnSingleUnit), R.drawable.ic_stats_most_obs, 0, unitWithMostObservations, unitWithMostObservationsList, ""));

        GridLayoutManager layoutManager = new GridLayoutManager(originActivity, 4);

        holder.rvStatsContainer.setLayoutManager(layoutManager);
        holder.rvStatsContainer.setAdapter(new StatisticsCardAdapter(originActivity, this, statisticObjectList));

    }

    @Override
    public int getItemCount() {
        return seasons.size();
    }

    /**
     * Exports a statistics card as an image
     */
    public void exportCard(ViewHolder holder) {

        CharSequence originalText = holder.year_text_view.getText();
        if (cardType == StatisticsActivity.ToggleVariable.TOTAL)
            holder.year_text_view.setText(originActivity.getString(R.string.stat_card_export_title_total));
        else
            holder.year_text_view.setText(String.format("%s %s", originActivity.getString(R.string.stat_card_export_title), originalText));

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
        cardCanvas.drawBitmap(logoBitmap, cardBitmap.getWidth() - logoBitmap.getWidth() - 12, 12, null); // setting the co-ordinates for the logo with a padding of 10dp

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
        } finally {
            holder.year_text_view.setText(originalText);
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

    public Toast getToast() {
        return toast;
    }

    public void setToast(Toast toast) {
        this.toast = toast;
    }
}
