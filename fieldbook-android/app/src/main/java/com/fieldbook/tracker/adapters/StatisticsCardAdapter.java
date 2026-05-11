package com.fieldbook.tracker.adapters;

import android.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.StatisticsActivity;
import com.fieldbook.tracker.objects.StatisticObject;

import java.util.List;

public class StatisticsCardAdapter extends RecyclerView.Adapter<StatisticsCardAdapter.ViewHolder> {
    StatisticsActivity originActivity;
    List<StatisticObject> statsList;
    StatisticsAdapter adapter;

    public StatisticsCardAdapter(StatisticsActivity context, StatisticsAdapter adapter, List<StatisticObject> statsList) {
        this.originActivity = context;
        this.statsList = statsList;
        this.adapter = adapter;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        LinearLayout individualStatContainer;
        TextView statTitle, statValue;
        ImageView statIcon;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            individualStatContainer = itemView.findViewById(R.id.individual_stat_container);
            statTitle = itemView.findViewById(R.id.stat_title);
            statIcon = itemView.findViewById(R.id.stat_icon);
            statValue = itemView.findViewById(R.id.stat_value);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View individualStatsView = LayoutInflater.from(parent.getContext()).inflate(R.layout.statistics_card_individual_stats, parent, false);
        return new StatisticsCardAdapter.ViewHolder(individualStatsView);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {

        StatisticObject statObject = statsList.get(position);

        holder.statTitle.setText(statObject.getStatTitle());
        holder.statValue.setText(statObject.getStatValue());
        holder.statIcon.setImageResource(statObject.getStatIconId());

        if (statObject.getIsToast() == 1) {
            holder.individualStatContainer.setOnClickListener(view -> displayToast(statObject.getToastMessage()));
        } else {
            holder.individualStatContainer.setOnClickListener(view -> displayDialog(statObject.getDialogTitle(), statObject.getDialogData()));
        }
    }

    @Override
    public int getItemCount() {
        return statsList.size();
    }

    /**
     * Displays a dialog with the list of matching items of a statistic
     * @param titleString title of the dialog
     * @param data list of items to be displayed
     */
    public void displayDialog(String titleString, List<String> data) {

        if (adapter.getToast() != null) {
            adapter.getToast().cancel();
        }

        if (data.size() == 0) {
            displayToast(originActivity.getString(R.string.warning_no_data));
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(originActivity, R.style.AppAlertDialog);

        View layout = originActivity.getLayoutInflater().inflate(R.layout.dialog_individual_statistics, null);
        builder.setTitle(titleString).setView(layout);
        builder.setNegativeButton(R.string.dialog_close, (dialogInterface, id) -> dialogInterface.dismiss());

        final AlertDialog dialog = builder.create();

        ListView statsList = layout.findViewById(R.id.statsList);
        statsList.setAdapter(new StatisticsListAdapter(originActivity, data));

        dialog.show();
    }

    /**
     * Displays a toast with the given message
     * @param toastMessage message to be displayed
     */
    public void displayToast(String toastMessage) {
        if (adapter.getToast() != null) {
            adapter.getToast().cancel();
        }
        adapter.setToast(Toast.makeText(originActivity, toastMessage, Toast.LENGTH_LONG));
        adapter.getToast().show();
    }
}
