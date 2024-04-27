package com.fieldbook.tracker.adapters;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.RadioButton;
import android.widget.TextView;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.CollectActivity;
import com.fieldbook.tracker.activities.FieldEditorActivityOld;
import com.fieldbook.tracker.interfaces.FieldAdapterController;
import com.fieldbook.tracker.interfaces.FieldSortController;
import com.fieldbook.tracker.interfaces.FieldSyncController;
import com.fieldbook.tracker.interfaces.FieldSwitcher;
import com.fieldbook.tracker.objects.FieldObject;
import com.fieldbook.tracker.preferences.GeneralKeys;

import java.util.ArrayList;

/**
 * Loads data on field manager screen
 */

public class FieldAdapterOld extends BaseAdapter {

    private static final String TAG = "FieldAdapterOld";

    private final LayoutInflater mLayoutInflater;
    private final ArrayList<FieldObject> list;
    private final Context context;
    private final FieldSwitcher fieldSwitcher;
    private final FieldSyncController fieldSyncController;
    public FieldAdapterOld(Context context, ArrayList<FieldObject> list, FieldSwitcher switcher, FieldSyncController fieldSyncController) {
        this.context = context;
        mLayoutInflater = LayoutInflater.from(context);
        this.list = list;
        this.fieldSwitcher = switcher;
        this.fieldSyncController = fieldSyncController;
    }

    public int getCount() {
        return list.size();
    }

    public FieldObject getItem(int position) {
        return list.get(position);
    }

    public long getItemId(int position) {

        if (position < 0) {
            return -1;
        }

        return position;
    }

    private SharedPreferences getPreferences() {

        return ((FieldEditorActivityOld) context).getPreferences();

    }

    private void setEditorItem(SharedPreferences preferences, FieldObject item) {
        SharedPreferences.Editor ed = preferences.edit();
        boolean has_contents = item != null;
        if (has_contents) {
            ed.putString(GeneralKeys.FIELD_FILE, item.getExp_name());
            ed.putString(GeneralKeys.FIELD_ALIAS, item.getExp_alias());
            ed.putString(GeneralKeys.FIELD_OBS_LEVEL, item.getObservation_level());
            ed.putInt(GeneralKeys.SELECTED_FIELD_ID, item.getExp_id());
            ed.putString(GeneralKeys.UNIQUE_NAME, item.getUnique_id());
            ed.putString(GeneralKeys.PRIMARY_NAME, item.getPrimary_id());
            ed.putString(GeneralKeys.SECONDARY_NAME, item.getSecondary_id());
        } else {
            ed.putString(GeneralKeys.FIELD_FILE, null);
            ed.putString(GeneralKeys.FIELD_ALIAS, null);
            ed.putString(GeneralKeys.FIELD_OBS_LEVEL, null);
            ed.putInt(GeneralKeys.SELECTED_FIELD_ID, -1);
            ed.putString(GeneralKeys.UNIQUE_NAME, null);
            ed.putString(GeneralKeys.PRIMARY_NAME, null);
            ed.putString(GeneralKeys.SECONDARY_NAME, null);
        }
        ed.putBoolean(GeneralKeys.IMPORT_FIELD_FINISHED, has_contents);
        ed.putString(GeneralKeys.LAST_PLOT, null);
        ed.apply();
    }

    @Override
    public View getView(final int position, View convertView, final ViewGroup parent) {

        ViewHolder holder;
        if (convertView == null) {
            holder = new ViewHolder();
            convertView = mLayoutInflater.inflate(R.layout.list_item_field, null);

            holder.fieldName = convertView.findViewById(R.id.list_item_trait_trait_name);
            holder.count = convertView.findViewById(R.id.field_count);
            holder.importDate = convertView.findViewById(R.id.field_import_date);
            holder.editDate = convertView.findViewById(R.id.field_edit_date);
            holder.exportDate = convertView.findViewById(R.id.field_export_date);
            holder.active = convertView.findViewById(R.id.fieldRadio);
            holder.menuPopup = convertView.findViewById(R.id.popupMenu);
            holder.observationLevel = convertView.findViewById(R.id.observationLevelLbl);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        convertView.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                fieldClick(getItem(position));
            }
        });

        String importDate = getItem(position).getDate_import();
        String editDate = getItem(position).getDate_edit();
        String exportDate = getItem(position).getDate_export();
        String observationLevel = getItem(position).getObservation_level();

        if (importDate != null) {
            importDate = importDate.split(" ")[0];
        }

        if (editDate != null) {
            editDate = editDate.split(" ")[0];
        }

        if (exportDate != null) {
            exportDate = exportDate.split(" ")[0];
        }

        if (observationLevel == null) {
            holder.observationLevel.setVisibility(View.GONE);//make invisible
        } else {
            holder.observationLevel.setVisibility(View.VISIBLE);
        }

        holder.fieldName.setText(getItem(position).getExp_alias());
        holder.count.setText(getItem(position).getCount());
        holder.importDate.setText(importDate);
        holder.editDate.setText(editDate);
        holder.exportDate.setText(exportDate);
        holder.observationLevel.setText(observationLevel);

        holder.active.setOnClickListener(v -> fieldClick(getItem(position)));

        int selectedFieldId = getPreferences().getInt(GeneralKeys.SELECTED_FIELD_ID, -1);
        FieldObject field = getItem(position);
        holder.active.setChecked(false);

        if (selectedFieldId != -1) {
            // Set to active if the current field item's id matches the selected field id
            if (field.getExp_id() == selectedFieldId) {
                holder.active.setChecked(true);
            }
        }

        holder.menuPopup.setOnClickListener(makeMenuPopListener(position));

        return convertView;
    }

    private View.OnClickListener makeMenuPopListener(final int position) {
        return new View.OnClickListener() {
            // Do it when clicking ":"
            @Override
            public void onClick(final View view) {
                PopupMenu popup = new PopupMenu(context, view);
                //Inflating the Popup using xml file
                popup.getMenuInflater().inflate(R.menu.menu_field_listitem, popup.getMenu());

                //registering popup with OnMenuItemClickListener
                popup.setOnMenuItemClickListener(makeSelectMenuListener(position));
                popup.show();//showing popup menu
            }
        };
    }

    private PopupMenu.OnMenuItemClickListener makeSelectMenuListener(final int position) {
        return new PopupMenu.OnMenuItemClickListener() {
            // Do it when selecting Delete or Statistics
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == R.id.delete) {
                    createDeleteItemAlertDialog(position).show();
                } else if (item.getItemId() == R.id.sort) {
                    showSortDialog(position);
                    //DialogUtils.styleDialogs(alert);
                }
                else if (item.getItemId() == R.id.syncObs) {
                    FieldObject fieldObject = getItem(position);
                    if(fieldSyncController != null) {
                        fieldSyncController.startSync(fieldObject);
                    }
                }

                return false;
            }
        };
    }

    private DialogInterface.OnClickListener makeConfirmDeleteListener(final int position) {
        return new DialogInterface.OnClickListener() {
            // Do it when clicking Yes or No
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();

                ((FieldAdapterController) context).getDatabase().deleteField(getItem(position).getExp_id());

                if (getItem(position).getExp_id() == getPreferences().getInt(GeneralKeys.SELECTED_FIELD_ID, -1)) {
                    setEditorItem(getPreferences(), null);
                }

                ((FieldAdapterController) context).queryAndLoadFields();

                CollectActivity.reloadData = true;
            }
        };
    }

    private void showSortDialog(final int position) {

        FieldObject field = getItem(position);

        ((FieldSortController) context).showSortDialog(field);
    }

    private AlertDialog createDeleteItemAlertDialog(final int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.AppAlertDialog);

        builder.setTitle(context.getString(R.string.fields_delete_study));
        builder.setMessage(context.getString(R.string.fields_delete_study_confirmation));
        builder.setPositiveButton(context.getString(R.string.dialog_yes), makeConfirmDeleteListener(position));
        builder.setNegativeButton(context.getString(R.string.dialog_no), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }

        });

        AlertDialog alert = builder.create();
        return alert;
    }

    private void fieldClick(FieldObject selectedField) {

        setEditorItem(getPreferences(), selectedField);

        fieldSwitcher.switchField(selectedField);

        CollectActivity.reloadData = true;
        notifyDataSetChanged();

        // Check if this is a BrAPI field and show BrAPI info dialog if so
//        if (selectedField.getImport_format() == ImportFormat.BRAPI) {
//            BrapiInfoDialog brapiInfo = new BrapiInfoDialog(context,
//                    context.getResources().getString(R.string.brapi_info_message));
//            brapiInfo.show();
//        }

    }

    private class ViewHolder {
        ImageView menuPopup;
        TextView fieldName;
        TextView count;
        TextView importDate;
        TextView editDate;
        TextView exportDate;
        RadioButton active;
        TextView observationLevel;
    }
}
