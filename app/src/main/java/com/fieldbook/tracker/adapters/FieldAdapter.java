package com.fieldbook.tracker.adapters;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.FieldArchivedActivity;
import com.fieldbook.tracker.activities.FieldEditorActivity;
import com.fieldbook.tracker.database.models.GroupModel;
import com.fieldbook.tracker.interfaces.FieldGroupController;
import com.fieldbook.tracker.objects.FieldObject;
import com.fieldbook.tracker.objects.ImportFormat;
import com.fieldbook.tracker.preferences.GeneralKeys;

import java.util.ArrayList;
import java.util.Collections;
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
    private final Context context;
    private AdapterCallback callback;
    private final FieldGroupController fieldGroupController;
    private OnFieldActionListener listener;
    private String filterText = "";
    private final List<FieldObject> fullFieldList = new ArrayList<>();
    private final SharedPreferences preferences;
    private final boolean isArchivedFieldsActivity;
    public enum FieldViewType {
        TYPE_GROUP_HEADER, TYPE_FIELD, TYPE_ARCHIVE_HEADER
    }

    public interface OnFieldActionListener {
        void onFieldDetailSelected(int fieldId);
        void onFieldSetActive(int fieldId);
    }

    public interface AdapterCallback {
        void onItemSelected(int count);
        void onItemClear();
    }

    public FieldAdapter(Context context, AdapterCallback callback,
                        FieldGroupController fieldGroupController, boolean isArchivedFieldsActivity) {
        super(new DiffUtil.ItemCallback<FieldViewItem>() {
            @Override
            public boolean areItemsTheSame(@NonNull FieldViewItem oldItem, @NonNull FieldViewItem newItem) {
                if (oldItem.viewType != newItem.viewType) {
                    return false;
                }

                if (oldItem.isGroupHeader() || oldItem.isArchiveHeader()) { // group or archive header
                    return areNamesEqual(oldItem.groupName, newItem.groupName);
                } else { // field
                    return oldItem.field.getStudyId() == newItem.field.getStudyId();
                }
            }

            @Override
            public boolean areContentsTheSame(@NonNull FieldViewItem oldItem, @NonNull FieldViewItem newItem) {
                if (oldItem.viewType != newItem.viewType) {
                    return false;
                }

                if (oldItem.isGroupHeader() || oldItem.isArchiveHeader()) {
                    return areNamesEqual(oldItem.groupName, newItem.groupName)
                            && oldItem.isExpanded == newItem.isExpanded
                            && oldItem.groupSize == newItem.groupSize;
                } else { // field
                    return oldItem.field.getAlias().equals(newItem.field.getAlias())
                            && oldItem.isActive == newItem.isActive;
                }
            }

            private boolean areNamesEqual(String name1, String name2) {
                return (name1 == null && name2 == null) || (name1 != null && name1.equals(name2));
            }
        });
        this.context = context;
        this.callback = callback;
        this.fieldGroupController = fieldGroupController;
        this.isArchivedFieldsActivity = isArchivedFieldsActivity;
        this.preferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public void setOnFieldActionListener(OnFieldActionListener listener) {
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
        listener.onFieldDetailSelected(itemId);
    }

    public void selectAll() {
        for (FieldObject field : fullFieldList) {
            boolean groupingEnabled = preferences.getBoolean(GeneralKeys.FIELD_GROUPING_ENABLED, false);
            if (isArchivedFieldsActivity) { // for FieldArchivedActivity, add all the fields
                selectedIds.add(field.getStudyId());
            } else if (!groupingEnabled || !field.getArchived()) {
                // for FieldEditorActivity if grouping is
                // enabled: add non-archived fields
                // disabled: add all fields
                selectedIds.add(field.getStudyId());
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
        final TextView headerTv;
        final ImageView expandIcon;

        GroupViewHolder(View itemView) {
            super(itemView);
            headerTv = itemView.findViewById(R.id.groupName);
            expandIcon = itemView.findViewById(R.id.expandIcon);

            itemView.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    toggleGroupExpansion(position);
                }
            });

            itemView.setOnLongClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    FieldViewItem headerItem = getItem(position);
                    if (!headerItem.isExpanded) { // expand group
                        toggleGroupExpansion(position);
                    }
                    Integer groupId = fieldGroupController.getStudyGroupIdByName(headerItem.groupName);
                    selectAllFieldsInGroup(groupId);
                    isInSelectionMode = true;
                }
                return false;
            });
        }
    }

    class ArchiveViewHolder extends RecyclerView.ViewHolder {
        final TextView headerTv;

        ArchiveViewHolder(View itemView) {
            super(itemView);
            headerTv = itemView.findViewById(R.id.archiveName);

            itemView.setOnClickListener(v -> {
                Intent intent = new Intent(context, FieldArchivedActivity.class);
                context.startActivity(intent);
            });
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
                            toggleSelection(field.getStudyId());
                        } else if (field != null) {
                            listener.onFieldSetActive(field.getStudyId());
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
                                toggleSelection(field.getStudyId());
                            } else if (field != null && listener != null) {
                                listener.onFieldDetailSelected(field.getStudyId());
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
                            toggleSelection(field.getStudyId());
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

            String groupName = ((fieldViewItem.groupName == null || fieldViewItem.groupName.isEmpty())
                    ? context.getString(R.string.fields_ungrouped) : fieldViewItem.groupName);
            String headerText = groupName + " (" + fieldViewItem.groupSize + ")";

            groupHolder.headerTv.setText(headerText);
            groupHolder.expandIcon.setImageResource(fieldViewItem.isExpanded ? R.drawable.ic_chevron_up : R.drawable.ic_chevron_down);
        } else if (fieldViewItem.isArchiveHeader()) {
            ArchiveViewHolder archiveHolder = (ArchiveViewHolder) holder;

            String headerText = fieldViewItem.groupName + " (" + fieldViewItem.groupSize + ")";

            archiveHolder.headerTv.setText(headerText);
        } else {
            FieldViewHolder fieldHolder = (FieldViewHolder) holder;
            bindFieldViewHolder(fieldHolder, fieldViewItem);
        }
    }

    private void bindFieldViewHolder(FieldViewHolder holder, FieldViewItem fieldViewItem) {
        FieldObject field = fieldViewItem.field;
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
        int activeStudyId = preferences.getInt(GeneralKeys.SELECTED_FIELD_ID, -1);
        Log.d("FieldAdapter", "Field id is " + field.getStudyId() + " and active field id is "+activeStudyId);
        if (field.getStudyId() == activeStudyId) {
            // Indicate active state
            Log.d("FieldAdapter", "Setting icon background for active field " + name);
            // holder.sourceIcon.setBackgroundResource(R.drawable.custom_round_button);
            holder.sourceIcon.setBackgroundResource(R.drawable.round_outline_button);

        } else {
            // Clear any modifications for non-active fields
            holder.sourceIcon.setBackground(null);
        }
    }

    private void setGroupExpansionStatus(FieldViewItem header) {
        String headerName = header.groupName;
        if (headerName != null && !headerName.isEmpty()) { // save the isExpanded status
            Integer groupId = fieldGroupController.getStudyGroupIdByName(header.groupName);
            if (groupId != null) {
                fieldGroupController.updateIsExpanded(groupId, header.isExpanded);
            }
        } else { // if "ungrouped", save in preference
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean(GeneralKeys.UNGROUPED_FIELDS_EXPANDED, header.isExpanded);
            editor.apply();
        }
    }

    private void toggleGroupExpansion(int headerPosition) {
        FieldViewItem header = getItem(headerPosition);
        header.isExpanded = !header.isExpanded;
        String headerName = header.groupName;

        setGroupExpansionStatus(header);
        notifyItemChanged(headerPosition);

        List<FieldViewItem> currentList = new ArrayList<>(getCurrentList());

        if (header.isExpanded) {
            // add all fields for this group
            int insertPosition = headerPosition + 1;
            for (FieldObject field : fullFieldList) {
                String fieldGroupName = fieldGroupController.getStudyGroupNameById(field.getGroupId());
                if (!field.getArchived()) {
                    if ((headerName == null && fieldGroupName == null) || // ungrouped
                            (headerName != null && headerName.equals(fieldGroupName))) { // grouped

                        FieldViewItem fieldItem = new FieldViewItem(field, fieldGroupController);
                        int activeFieldId = preferences.getInt(GeneralKeys.SELECTED_FIELD_ID, -1);
                        fieldItem.updateIsActive(activeFieldId); // update active state

                        currentList.add(insertPosition++, fieldItem);
                    }
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

    private void selectAllFieldsInGroup(Integer groupId) {
        for (FieldObject field : fullFieldList) {
            if (!field.getArchived()) { // make sure we are not selecting archived fields
                if ((groupId == null && field.getGroupId() == null) ||
                        (groupId != null && groupId.equals(field.getGroupId()))) {
                    selectedIds.add(field.getStudyId());
                }
            }
        }

        notifyDataSetChanged();
        if (callback != null) {
            callback.onItemSelected(selectedIds.size());
        }
    }

    public void resetFieldsList(List<FieldObject> fields) {
        fullFieldList.clear();
        if (fields != null) {
            fullFieldList.addAll(fields);
        }

        submitFieldsList(fullFieldList);
    }

    private void submitFieldsList(List<FieldObject> fields) {
        List<FieldViewItem> arrayList;
        if (isArchivedFieldsActivity) {
            arrayList = buildArchivedFieldsList(fields);
        } else {
            arrayList = buildFieldsList(fields);
        }

        submitList(arrayList);
    }

    /**
     * Builds a list of field items for the ArchivedFieldsActivity
     */
    private List<FieldViewItem> buildArchivedFieldsList(List<FieldObject> fieldsList) {
        List<FieldViewItem> items = new ArrayList<>();
        for (FieldObject field : fieldsList) {
            if (field.getArchived()) {
                items.add(new FieldViewItem(field, fieldGroupController));
            }
        }
        return items;
    }

    /**
     * Builds a list of field items for the FieldEditorActivity
     * When grouping is
     *  - disabled: show all fields that are not archived
     *  - enabled: groups fields by their group name and includes group headers
     * Attaches archived list item at the end (regardless of grouping state)
     */
    private List<FieldViewItem> buildFieldsList(List<FieldObject> fieldsList) {
        List<FieldViewItem> arrayList = new ArrayList<>();
        boolean groupingEnabled = preferences.getBoolean(GeneralKeys.FIELD_GROUPING_ENABLED, false);
        int activeFieldId = preferences.getInt(GeneralKeys.SELECTED_FIELD_ID, -1);

        if (!groupingEnabled) {
            for (FieldObject field : fieldsList) {
                if (!field.getArchived()) {
                    FieldViewItem item = new FieldViewItem(field, fieldGroupController);
                    item.updateIsActive(activeFieldId);
                    arrayList.add(item);
                }
            }

            addArchivedHeaderToList(arrayList, fieldsList);

            return arrayList;
        }

        Map<String, List<FieldObject>> groupedFields = getGroupedFields(fieldsList);

        List<FieldObject> ungroupedFields = groupedFields.remove(null);

        // add group-field entries
        for (Map.Entry<String, List<FieldObject>> entry : groupedFields.entrySet()) {
            String groupName = entry.getKey();
            List<FieldObject> groupFields = entry.getValue();

            if (groupFields.isEmpty()) continue;

            Integer groupId = fieldGroupController.getStudyGroupIdByName(groupName);
            boolean isExpanded = groupId == null || fieldGroupController.getIsExpanded(groupId);

            addGroupToList(arrayList, groupName, isExpanded, groupFields);
        }

        if (ungroupedFields != null && !ungroupedFields.isEmpty()) { // add ungrouped header at the end BEFORE archived list item
            boolean ungroupedExpanded = preferences.getBoolean(GeneralKeys.UNGROUPED_FIELDS_EXPANDED, true);

            addGroupToList(arrayList, null, ungroupedExpanded, ungroupedFields);
        }

        addArchivedHeaderToList(arrayList, fieldsList);

        return arrayList;
    }

    /**
     * Adds a group to the array list with header and fields (if expanded and not archived)
     */
    private void addGroupToList(
            List<FieldViewItem> arrayList,
            String groupName,
            boolean isExpanded,
            @NonNull List<FieldObject> groupFields
    ) {
        // add header
        FieldViewItem header = new FieldViewItem(groupName, groupFields.size(), false);
        header.isExpanded = isExpanded;
        arrayList.add(header);

        // add children if expanded
        if (isExpanded) {
            int activeFieldId = preferences.getInt(GeneralKeys.SELECTED_FIELD_ID, -1);
            for (FieldObject f : groupFields) {
                if (!f.getArchived()) {
                    FieldViewItem item = new FieldViewItem(f, fieldGroupController);
                    item.updateIsActive(activeFieldId);
                    arrayList.add(item);
                }
            }
        }
    }

    private void addArchivedHeaderToList(List<FieldViewItem> arrayList, List<FieldObject> fieldsList) {
        long archivedCount = fieldsList.stream().filter(FieldObject::getArchived).count();
        if (archivedCount > 0) { // add archived list item at the bottom
            String archivedVal = context.getString(R.string.group_archived_value);
            FieldViewItem archiveHeader = new FieldViewItem(archivedVal, archivedCount, true);
            arrayList.add(archiveHeader);
        }
    }

    /**
     * Returns a structure of group/fields, and the same order will be shown
     * Order of addition to groupedFields affects the ordering on screen as we are using LinkedHashMap
     * Ungrouped fields are handled in buildFieldsList
     */
    private Map<String, List<FieldObject>> getGroupedFields(List<FieldObject> fields) {
        Map<String, List<FieldObject>> groupedFields = new LinkedHashMap<>();

        // "null" for ungrouped fields
        groupedFields.put(null, new ArrayList<>());

        // add all study group names
        List<GroupModel> allStudyGroups = fieldGroupController.getAllStudyGroups();
        if (allStudyGroups != null && !allStudyGroups.isEmpty()) {
            for (GroupModel group : allStudyGroups) {
                groupedFields.put(group.getGroupName(), new ArrayList<>());
            }
        }

        for (FieldObject field : fields) {
            if (field.getArchived()) { // handle archived fields in buildFieldList
                continue;
            }

            String groupName = fieldGroupController.getStudyGroupNameById(field.getGroupId());

            // add the field to its group
            List<FieldObject> fieldList = groupedFields.get(groupName);
            if (fieldList != null){
                fieldList.add(field);
            }
        }

        String currentSortOrder = preferences.getString(GeneralKeys.FIELDS_LIST_SORT_ORDER, "date_import");
        boolean isSortingByName = "study_alias".equals(currentSortOrder);

        return isSortingByName ? sortGroupsByName(groupedFields) : groupedFields;
    }

    /**
     * Returns the grouped fields in sorted order
     */
    private Map<String, List<FieldObject>> sortGroupsByName(Map<String, List<FieldObject>> groupedFields) {
        Map<String, List<FieldObject>> sortedGroups = new LinkedHashMap<>();

        // add the ungrouped fields at the beginning
        sortedGroups.put(null, groupedFields.remove(null));

        // add all other groups in sorted order
        List<String> groupNames = new ArrayList<>(groupedFields.keySet());
        Collections.sort(groupNames, String::compareToIgnoreCase);

        for (String name : groupNames) {
            sortedGroups.put(name, groupedFields.get(name));
        }

        return sortedGroups;
    }

    public void setTextFilter(String filter) {
        this.filterText = filter;
        List<FieldObject> filteredFields = new ArrayList<>(fullFieldList);
        for (FieldObject field : fullFieldList) {
            if (!filter.isEmpty()) {
                String lowerFilter = filter.toLowerCase();
                boolean matchesName = field.getName().toLowerCase().contains(lowerFilter);
                boolean matchesAlias = field.getAlias().toLowerCase().contains(lowerFilter);
                if (!matchesName && !matchesAlias) {
                    filteredFields.remove(field);
                }
            }
        }
        submitFieldsList(filteredFields);
    }

    public void changeStateOfAllGroups(boolean isExpanded) {
        List<GroupModel> allStudyGroups = fieldGroupController.getAllStudyGroups();
        if (allStudyGroups != null) {
            for (GroupModel group : allStudyGroups) {
                fieldGroupController.updateIsExpanded(group.getId(), isExpanded);
            }
        }
        preferences.edit().putBoolean(GeneralKeys.UNGROUPED_FIELDS_EXPANDED, isExpanded).apply();

        submitFieldsList(fullFieldList);
    }

    public static class FieldViewItem {
        public FieldViewType viewType;
        public String groupName;
        public long groupSize;
        public FieldObject field;
        public boolean isExpanded = true;
        public boolean isActive = false;

        public FieldViewItem(String groupName, long groupSize, boolean isArchive) {
            this.viewType = isArchive? FieldViewType.TYPE_ARCHIVE_HEADER : FieldViewType.TYPE_GROUP_HEADER;
            this.groupName = groupName;
            this.groupSize = groupSize;
        }

        public FieldViewItem(FieldObject field, FieldGroupController fieldGroupController) {
            this.viewType = FieldViewType.TYPE_FIELD;
            this.field = field;
            this.groupName = fieldGroupController.getStudyGroupNameById(field.getGroupId());
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

        public void updateIsActive(int activeFieldId) {
            this.isActive = (field != null && field.getStudyId() == activeFieldId);
        }
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).viewType.ordinal();
    }
}