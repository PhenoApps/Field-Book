package com.fieldbook.tracker.adapters;

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

import androidx.appcompat.app.AlertDialog;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.CollectActivity;
import com.fieldbook.tracker.brapi.BrapiInfoDialog;
import com.fieldbook.tracker.dialogs.BrapiSyncObsDialog;
import com.fieldbook.tracker.interfaces.FieldAdapterController;
import com.fieldbook.tracker.interfaces.FieldSortController;
import com.fieldbook.tracker.interfaces.FieldSwitcher;
import com.fieldbook.tracker.objects.FieldObject;
import com.fieldbook.tracker.preferences.GeneralKeys;

import java.util.ArrayList;

/**
 * Loads data on field manager screen
 */

public class FieldAdapter extends BaseAdapter {

    private static final String TAG = "FieldAdapter";

    private final LayoutInflater mLayoutInflater;
    private final ArrayList<FieldObject> list;
    private final Context context;
    private SharedPreferences ep;
    private final FieldSwitcher fieldSwitcher;
    public FieldAdapter(Context context, ArrayList<FieldObject> list, FieldSwitcher switcher) {
        this.context = context;
        mLayoutInflater = LayoutInflater.from(context);
        this.list = list;
        this.fieldSwitcher = switcher;
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

    private void setEditorItem(SharedPreferences ep, FieldObject item) {
        SharedPreferences.Editor ed = ep.edit();
        boolean has_contents = item != null;
        if (has_contents) {
            ed.putString(GeneralKeys.FIELD_FILE, item.getExp_name());
            ed.putString(GeneralKeys.FIELD_OBS_LEVEL, item.getObservation_level());
            ed.putInt(GeneralKeys.SELECTED_FIELD_ID, item.getExp_id());
            ed.putString(GeneralKeys.UNIQUE_NAME, item.getUnique_id());
            ed.putString(GeneralKeys.PRIMARY_NAME, item.getPrimary_id());
            ed.putString(GeneralKeys.SECONDARY_NAME, item.getSecondary_id());
        } else {
            ed.putString(GeneralKeys.FIELD_FILE, null);
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

        ep = context.getSharedPreferences(GeneralKeys.SHARED_PREF_FILE_NAME, 0);

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

        holder.fieldName.setText(getItem(position).getExp_name());
        holder.count.setText(getItem(position).getCount());
        holder.importDate.setText(importDate);
        holder.editDate.setText(editDate);
        holder.exportDate.setText(exportDate);
        holder.observationLevel.setText(observationLevel);

        holder.active.setOnClickListener(v -> fieldClick(getItem(position)));

        //Check both file name and observation level
        if (ep.getInt(GeneralKeys.SELECTED_FIELD_ID, -1) != -1) {
            FieldObject field = getItem(position);

            if (field.getExp_source() == null) {
                holder.active.setChecked((ep.getString(GeneralKeys.FIELD_FILE, "")
                        .contentEquals(holder.fieldName.getText())) &&
                        (ep.getString(GeneralKeys.FIELD_OBS_LEVEL, "")
                                .contentEquals(holder.observationLevel.getText())));
            } else if (field.getExp_alias() != null) {
                String alias = ep.getString(GeneralKeys.FIELD_ALIAS, "");
                String level = ep.getString(GeneralKeys.FIELD_OBS_LEVEL, "");
                holder.active.setChecked(alias.contentEquals(field.getExp_alias())
                        && level.contentEquals(holder.observationLevel.getText()));
            }

        } else holder.active.setChecked(false);

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
                    BrapiSyncObsDialog alert = new BrapiSyncObsDialog(context);
                    alert.setFieldObject(getItem(position));
                    alert.show();
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

                if (getItem(position).getExp_id() == ep.getInt(GeneralKeys.SELECTED_FIELD_ID, -1)) {
                    setEditorItem(ep, null);
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

        setEditorItem(ep, selectedField);

        fieldSwitcher.switchField(selectedField);

        CollectActivity.reloadData = true;
        notifyDataSetChanged();

        // Check if this is a BrAPI field and show BrAPI info dialog if so
        if (selectedField.getExp_source() != null &&
                !selectedField.getExp_source().equals("") &&
                !selectedField.getExp_source().equals("local")) {

            BrapiInfoDialog brapiInfo = new BrapiInfoDialog(context,
                    context.getResources().getString(R.string.brapi_info_message));
            brapiInfo.show();
        }
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