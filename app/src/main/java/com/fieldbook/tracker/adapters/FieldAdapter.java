package com.fieldbook.tracker.adapters;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.FieldEditorActivity;
import com.fieldbook.tracker.interfaces.FieldSwitcher;
import com.fieldbook.tracker.objects.FieldObject;
import com.fieldbook.tracker.objects.ImportFormat;
import com.fieldbook.tracker.preferences.GeneralKeys;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;


/**
 * Loads data on field manager screen
 */

public class FieldAdapter extends ListAdapter<FieldObject, FieldAdapter.ViewHolder> {
    private Set<Integer> selectedIds = new LinkedHashSet<>();
    private boolean isInSelectionMode = false;
    private static final String TAG = "FieldAdapter";
    private final LayoutInflater mLayoutInflater;
    private final Context context;
    private final FieldSwitcher fieldSwitcher;
    private AdapterCallback callback;
    private OnFieldSelectedListener listener;
    private String filterText = "";
    private final List<FieldObject> fullFieldList = new ArrayList<>();

    public interface OnFieldSelectedListener {
        void onFieldSelected(int itemId);
    }

    public interface AdapterCallback {
        void onItemSelected(int count);
        void onItemClear();
    }

    public FieldAdapter(Context context, FieldSwitcher switcher, AdapterCallback callback) {
        super(new DiffUtil.ItemCallback<FieldObject>() {
            @Override
            public boolean areItemsTheSame(@NonNull FieldObject oldItem, @NonNull FieldObject newItem) {
                return oldItem.getStudyId() == newItem.getStudyId();
            }
            @Override
            public boolean areContentsTheSame(@NonNull FieldObject oldItem, @NonNull FieldObject newItem) {
                return oldItem.getAlias().equals(newItem.getAlias());
            }
        });
        this.context = context;
        mLayoutInflater = LayoutInflater.from(context);
        this.fieldSwitcher = switcher;
        this.callback = callback;
    }

    public void setOnFieldSelectedListener(OnFieldSelectedListener listener) {
        this.listener = listener;
    }

    public List<Integer> getSelectedItems() {
        return new ArrayList<>(selectedIds);
    }

    public int getSelectedItemCount() {
        return selectedIds.size();
    }

    public void toggleSelection(int itemId) {
        if (selectedIds.contains(itemId)) {
            selectedIds.remove(itemId);
        } else {
            selectedIds.add(itemId);
        }
        notifyDataSetChanged();
        if (callback != null) {
            callback.onItemSelected(selectedIds.size());
        }
    }

    public void selectItem(int itemId) {
        listener.onFieldSelected(itemId);
    }

    public void selectAll() {
        List<FieldObject> currentList = getCurrentList();
        for (FieldObject item : currentList) {
            selectedIds.add(item.getStudyId());
        }
        notifyDataSetChanged();
        isInSelectionMode = true;
        if (callback != null) {
            callback.onItemSelected(selectedIds.size());
        }
    }

    public void exitSelectionMode() {
        selectedIds.clear();
        isInSelectionMode = false;
        notifyDataSetChanged();
        if (callback != null) {
            callback.onItemClear();
        }
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        ImageView sourceIcon;
        TextView name, count;

        ViewHolder(View itemView) {
            super(itemView);
            sourceIcon = itemView.findViewById(R.id.fieldSourceIcon);
            name = itemView.findViewById(R.id.fieldName);
            count = itemView.findViewById(R.id.fieldCount);

            // Short click on source icon sets active field (unless in selectionMode)
            sourceIcon.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    FieldObject field = getItem(position);
                    if (field != null && isInSelectionMode) {
                        toggleSelection(field.getStudyId());
                    } else if (field != null && context instanceof FieldEditorActivity) {
                        ((FieldEditorActivity) context).setActiveField(field.getStudyId());
                    }
                }
            });

            // Short click elsewhere opens detail fragment (unless in selectionMode)
            itemView.setOnClickListener(v -> {
                if (v != sourceIcon) { // Check if the click is not on the icon
                    int position = getBindingAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        FieldObject field = getItem(position);
                        if (field != null && isInSelectionMode) {
                            toggleSelection(field.getStudyId());
                        } else if (field != null && listener != null) {
                            listener.onFieldSelected(field.getStudyId());
                        }
                    }
                }
            });

            // Long click enters and toggles selections in selection mode
            itemView.setOnLongClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    FieldObject field = getItem(position);
                    if (field != null) {
                        toggleSelection(field.getStudyId());
                        isInSelectionMode = true;
                        return true;
                    }
                }
                return false;
            });
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_field_recycler, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        FieldObject field = getItem(position);
        holder.itemView.setActivated(selectedIds.contains(field.getStudyId()));
        String name = field.getAlias();
        holder.name.setText(name);
        String count = field.getEntryCount();
        String genericLevel = context.getString(R.string.field_generic_observation_level);
        String specificLevel = field.getObservationLevel();

        // Include the specific observation level if defined, otherwise, fallback to just the generic level
        String level = !TextUtils.isEmpty(specificLevel) ? specificLevel + " " + genericLevel : genericLevel;

        String formattedCount = String.format(context.getString(R.string.field_observation_count_format), count, level);
        holder.count.setText(formattedCount);

        // Set source icon
        ImportFormat importFormat = field.getDataSourceFormat();
        Log.d("FieldAdapter", "Import format for field " + name + ": " + importFormat);
        switch (importFormat) {
            case CSV:
                holder.sourceIcon.setImageResource(R.drawable.ic_file_csv);
                break;
            case BRAPI:
                holder.sourceIcon.setImageResource(R.drawable.ic_adv_brapi);
                break;
            case XLS:
            case XLSX:
                holder.sourceIcon.setImageResource(R.drawable.ic_file_xls);
                break;
            case INTERNAL:
                holder.sourceIcon.setImageResource(R.drawable.ic_field);
                break;
            default:
                holder.sourceIcon.setImageResource(R.drawable.ic_file_csv);
                break;
        }

        // Determine if this field is active
        int activeStudyId = ((FieldEditorActivity) context).getPreferences().getInt(GeneralKeys.SELECTED_FIELD_ID, -1);
        Log.d("FieldAdapter", "Field is is " + field.getStudyId() + " and active field is is "+activeStudyId);
        if (field.getStudyId() == activeStudyId) {
            // Indicate active state
            Log.d("FieldAdapter", "Setting icon background for active field " + name);
//            holder.sourceIcon.setBackgroundResource(R.drawable.custom_round_button);
            holder.sourceIcon.setBackgroundResource(R.drawable.round_outline_button);

        } else {
            // Clear any modifications for non-active fields
            holder.sourceIcon.setBackground(null);
        }
    }

    @Override
    public void submitList(@Nullable List<FieldObject> list, @Nullable Runnable commitCallback) {
        super.submitList(list, commitCallback);
        fullFieldList.clear();
        if (list != null) {
            fullFieldList.addAll(list);
        }
    }

    public void setTextFilter(String filter) {
        this.filterText = filter;
        List<FieldObject> filterFields = new ArrayList<>(fullFieldList);
        for (FieldObject field : fullFieldList) {
            if (!filter.isEmpty() && !field.getName().toLowerCase().contains(filter.toLowerCase())) {
                filterFields.remove(field);
            }
        }
        submitList(filterFields);
    }
}