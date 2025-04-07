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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Loads data on field manager screen
 */

public class FieldAdapter extends ListAdapter<FieldAdapter.FieldViewItem, RecyclerView.ViewHolder> {
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
    public enum FieldViewType {
        TYPE_GROUP_HEADER, TYPE_FIELD, TYPE_ARCHIVE_HEADER
    }

    public interface OnFieldSelectedListener {
        void onFieldSelected(int itemId);
    }

    public interface AdapterCallback {
        void onItemSelected(int count);
        void onItemClear();
    }

    public FieldAdapter(Context context, FieldSwitcher switcher, AdapterCallback callback) {
        super(new DiffUtil.ItemCallback<FieldViewItem>() {
            @Override
            public boolean areItemsTheSame(@NonNull FieldViewItem oldItem, @NonNull FieldViewItem newItem) {
                if (oldItem.viewType != newItem.viewType) {
                    return false;
                }

                if (oldItem.isGroupHeader() || oldItem.isArchiveHeader()) { // group or archive header
                    return areNamesEqual(oldItem.groupName, newItem.groupName);
                } else { // field
                    return oldItem.field.getExp_id() == newItem.field.getExp_id();
                }
            }

            @Override
            public boolean areContentsTheSame(@NonNull FieldViewItem oldItem, @NonNull FieldViewItem newItem) {
                if (oldItem.viewType != newItem.viewType) {
                    return false;
                }

                if (oldItem.isGroupHeader() || oldItem.isArchiveHeader()) { // group or archive header
                    return areNamesEqual(oldItem.groupName, newItem.groupName) && (oldItem.isExpanded == newItem.isExpanded);
                } else { // field
                    return oldItem.field.getExp_alias().equals(newItem.field.getExp_alias());
                }
            }

            private boolean areNamesEqual(String name1, String name2) {
                return (name1 == null && name2 == null) || (name1 != null && name1.equals(name2));
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
        List<FieldViewItem> currentList = getCurrentList();
        for (FieldViewItem fieldViewItem : currentList) {
            if (!fieldViewItem.isGroupHeader()) {
                selectedIds.add(fieldViewItem.field.getExp_id());
            }
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

    class GroupViewHolder extends RecyclerView.ViewHolder {
        final TextView groupName;
        final ImageView expandIcon;

        GroupViewHolder(View itemView) {
            super(itemView);
            groupName = itemView.findViewById(R.id.groupName);
            expandIcon = itemView.findViewById(R.id.expandIcon);

            itemView.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    toggleGroupExpansion(position);
                }
            });
        }
    }

    static class ArchiveViewHolder extends RecyclerView.ViewHolder {
        final TextView groupName;
        final ImageView folderIcon;

        ArchiveViewHolder(View itemView) {
            super(itemView);
            groupName = itemView.findViewById(R.id.archiveName);
            folderIcon = itemView.findViewById(R.id.archiveIcon);
        }
    }

    class FieldViewHolder extends RecyclerView.ViewHolder {
        final ImageView sourceIcon;
        final TextView name, count;

        FieldViewHolder(View itemView) {
            super(itemView);
            sourceIcon = itemView.findViewById(R.id.fieldSourceIcon);
            name = itemView.findViewById(R.id.fieldName);
            count = itemView.findViewById(R.id.fieldCount);

            // Short click on source icon sets active field (unless in selectionMode)
            sourceIcon.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    FieldViewItem fieldViewItem = getItem(position);
                    if (fieldViewItem.isFieldItem()) {
                        FieldObject field = fieldViewItem.field;
                        if (field != null && isInSelectionMode) {
                            toggleSelection(field.getExp_id());
                        } else if (field != null && context instanceof FieldEditorActivity) {
                            ((FieldEditorActivity) context).setActiveField(field.getExp_id());
                        }
                    }
                }
            });

            // Short click elsewhere opens detail fragment (unless in selectionMode)
            itemView.setOnClickListener(v -> {
                if (v != sourceIcon) { // Check if the click is not on the icon
                    int position = getBindingAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        FieldViewItem fieldViewItem = getItem(position);
                        if (fieldViewItem.isFieldItem()) {
                            FieldObject field = fieldViewItem.field;
                            if (field != null && isInSelectionMode) {
                                toggleSelection(field.getExp_id());
                            } else if (field != null && listener != null) {
                                listener.onFieldSelected(field.getExp_id());
                            }
                        }
                    }
                }
            });

            // Long click enters and toggles selections in selection mode
            itemView.setOnLongClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    FieldViewItem fieldViewItem = getItem(position);
                    if (fieldViewItem.isFieldItem()) {
                        FieldObject field = fieldViewItem.field;
                        if (field != null) {
                            toggleSelection(field.getExp_id());
                            isInSelectionMode = true;
                            return true;
                        }
                    }
                }
                return false;
            });
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == FieldViewType.TYPE_GROUP_HEADER.ordinal()) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.list_item_field_group_header, parent, false);
            return new GroupViewHolder(view);
        } else if (viewType == FieldViewType.TYPE_ARCHIVE_HEADER.ordinal()) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.list_item_archived_fields_header, parent, false);
            return new ArchiveViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.list_item_field_recycler, parent, false);
            return new FieldViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        FieldViewItem fieldViewItem = getItem(position);

        if (fieldViewItem.isGroupHeader()) {
            GroupViewHolder groupHolder = (GroupViewHolder) holder;
            groupHolder.groupName.setText(
                    (fieldViewItem.groupName == null || fieldViewItem.groupName.isEmpty())
                            ? context.getString(R.string.fields_ungrouped) : fieldViewItem.groupName);
            groupHolder.expandIcon.setImageResource(fieldViewItem.isExpanded ? R.drawable.ic_chevron_up : R.drawable.ic_chevron_down);
        } else if (fieldViewItem.isArchiveHeader()) {
            ArchiveViewHolder archiveHolder = (ArchiveViewHolder) holder;
            archiveHolder.groupName.setText(context.getString(R.string.group_archived_value));
            archiveHolder.folderIcon.setImageResource(R.drawable.ic_archive);
        } else {
            FieldViewHolder fieldHolder = (FieldViewHolder) holder;
            bindFieldViewHolder(fieldHolder, fieldViewItem);
        }
    }

    private void bindFieldViewHolder(FieldViewHolder holder, FieldViewItem fieldViewItem) {
        FieldObject field = fieldViewItem.field;
        holder.itemView.setActivated(selectedIds.contains(field.getExp_id()));
        String name = field.getExp_alias();
        holder.name.setText(name);
        String count = field.getCount();
        String genericLevel = context.getString(R.string.field_generic_observation_level);
        String specificLevel = field.getObservation_level();

        // Include the specific observation level if defined, otherwise, fallback to just the generic level
        String level = !TextUtils.isEmpty(specificLevel) ? specificLevel + " " + genericLevel : genericLevel;

        String formattedCount = String.format(context.getString(R.string.field_observation_count_format), count, level);
        holder.count.setText(formattedCount);

        // Set source icon
        ImportFormat importFormat = field.getImport_format();
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
        Log.d("FieldAdapter", "Field is is " + field.getExp_id() + " and active field is is "+activeStudyId);
        if (field.getExp_id() == activeStudyId) {
            // Indicate active state
            Log.d("FieldAdapter", "Setting icon background for active field " + name);
            // holder.sourceIcon.setBackgroundResource(R.drawable.custom_round_button);
            holder.sourceIcon.setBackgroundResource(R.drawable.round_outline_button);

        } else {
            // Clear any modifications for non-active fields
            holder.sourceIcon.setBackground(null);
        }
    }

    private void toggleGroupExpansion(int headerPosition) {
        FieldViewItem header = getItem(headerPosition);
        header.isExpanded = !header.isExpanded;

        notifyItemChanged(headerPosition);

        String groupName = header.groupName;
        List<FieldViewItem> currentList = new ArrayList<>(getCurrentList());

        if (header.isExpanded) {
            // add all fields for this group
            int insertPosition = headerPosition + 1;
            for (FieldObject field : fullFieldList) {
                if ((groupName == null && field.getGroupName() == null) ||
                        (groupName != null && groupName.equals(field.getGroupName()))) {
                    currentList.add(insertPosition++, new FieldViewItem(field));
                }
            }
        } else {
            // remove all fields for this group
            int i = headerPosition + 1;
            while (i < currentList.size() && currentList.get(i).isFieldItem()) {
                currentList.remove(i);
            }
        }

        submitList(currentList);
    }

    public void submitFieldList(List<FieldObject> fields) {
        fullFieldList.clear();
        if (fields != null) {
            fullFieldList.addAll(fields);
        }

        Map<String, List<FieldObject>> groupedFields = getGroupedFields(fullFieldList);
        String archivedVal = context.getString(R.string.group_archived_value);

        List<FieldViewItem> arrayList = new ArrayList<>();
        for (Map.Entry<String, List<FieldObject>> entry : groupedFields.entrySet()) {
            String groupName = entry.getKey();
            List<FieldObject> groupFields = entry.getValue();

            if (!groupFields.isEmpty() && !archivedVal.equals(groupName)) {
                FieldViewItem header = new FieldViewItem(groupName, false);
                arrayList.add(header);

                for (FieldObject field : groupFields) {
                    arrayList.add(new FieldViewItem(field));
                }
            }
        }

        if (groupedFields.containsKey(archivedVal) && !groupedFields.get(archivedVal).isEmpty()) {
            FieldViewItem archiveHeader = new FieldViewItem(archivedVal, true);
            arrayList.add(archiveHeader);
        }

        submitList(arrayList);
    }

    /**
     * Returns a structure of group/fields, and the same order will be shown
     */
    private Map<String, List<FieldObject>> getGroupedFields(List<FieldObject> fields) {
        Map<String, List<FieldObject>> groupedFields = new LinkedHashMap<>();
        String archivedVal = context.getString(R.string.group_archived_value);

        // "null" for ungrouped fields
        groupedFields.put(null, new ArrayList<>());

        for (FieldObject field : fields) {
            String group = field.getGroupName();

            // add all groups
            if (group != null && !archivedVal.equals(group)) {
                groupedFields.putIfAbsent(group, new ArrayList<>());
            }

            // add archived group
            if (archivedVal.equals(group)) {
                groupedFields.putIfAbsent(archivedVal, new ArrayList<>());
            }

            // add the field to its group
            List<FieldObject> fieldList = groupedFields.get(group);
            if (fieldList != null){
                fieldList.add(field);
            }
        }

        return groupedFields;
    }

    public void setTextFilter(String filter) {
        this.filterText = filter;
        List<FieldObject> filteredFields = new ArrayList<>(fullFieldList);
        for (FieldObject field : fullFieldList) {
            if (!filter.isEmpty() && !field.getExp_name().toLowerCase().contains(filter.toLowerCase())) {
                filteredFields.remove(field);
            }
        }
        submitFieldList(filteredFields);
    }

    public static class FieldViewItem {
        public FieldViewType viewType;
        public String groupName;
        public FieldObject field;
        public boolean isExpanded = true;

        public FieldViewItem(String groupName, boolean isArchive) {
            this.viewType = isArchive? FieldViewType.TYPE_ARCHIVE_HEADER : FieldViewType.TYPE_GROUP_HEADER;
            this.groupName = groupName;
        }

        public FieldViewItem(FieldObject field) {
            this.viewType = FieldViewType.TYPE_FIELD;
            this.field = field;
            this.groupName = field.getGroupName();
        }

        public boolean isGroupHeader() {
            return viewType == FieldViewType.TYPE_GROUP_HEADER;
        }

        public boolean isArchiveHeader() {
            return viewType == FieldViewType.TYPE_ARCHIVE_HEADER;
        }

        public boolean isFieldItem() {
            return viewType == FieldViewType.TYPE_FIELD;
        }
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).viewType.ordinal();
    }
}