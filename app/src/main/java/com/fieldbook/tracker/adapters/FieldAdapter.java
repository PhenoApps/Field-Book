package com.fieldbook.tracker.adapters;

import androidx.appcompat.app.AlertDialog;

import android.app.Activity;
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

import com.fieldbook.tracker.activities.ConfigActivity;
import com.fieldbook.tracker.activities.CollectActivity;
import com.fieldbook.tracker.R;
import com.fieldbook.tracker.brapi.BrapiInfoDialog;
import com.fieldbook.tracker.activities.FieldEditorActivity;
import com.fieldbook.tracker.objects.FieldObject;
import com.fieldbook.tracker.utilities.DialogUtils;

import java.util.ArrayList;

/**
 * Loads data on field manager screen
 */

public class FieldAdapter extends BaseAdapter {

    private LayoutInflater mLayoutInflater;
    private ArrayList<FieldObject> list;
    private Context context;
    private SharedPreferences ep;

    public FieldAdapter(Context context, ArrayList<FieldObject> list) {
        this.context = context;
        mLayoutInflater = LayoutInflater.from(context);
        this.list = list;
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
            ed.putString("FieldFile", item.getExp_name());
            ed.putInt("SelectedFieldExpId", item.getExp_id());
            ed.putString("ImportUniqueName", item.getUnique_id());
            ed.putString("ImportFirstName", item.getPrimary_id());
            ed.putString("ImportSecondName", item.getSecondary_id());
        } else {
            ed.putString("FieldFile", null);
            ed.putInt("SelectedFieldExpId", -1);
            ed.putString("ImportUniqueName", null);
            ed.putString("ImportFirstName", null);
            ed.putString("ImportSecondName", null);
        }
        ed.putBoolean("ImportFieldFinished", has_contents);
        ed.putString("lastplot", null);
        ed.apply();
    }

    @Override
    public View getView(final int position, View convertView, final ViewGroup parent) {

        ep = context.getSharedPreferences("Settings", 0);

        ViewHolder holder;
        if (convertView == null) {
            holder = new ViewHolder();
            convertView = mLayoutInflater.inflate(R.layout.listitem_field, null);

            holder.fieldName = convertView.findViewById(R.id.field_name);
            holder.count = convertView.findViewById(R.id.field_count);
            holder.importDate = convertView.findViewById(R.id.field_import_date);
            holder.editDate = convertView.findViewById(R.id.field_edit_date);
            holder.exportDate = convertView.findViewById(R.id.field_export_date);
            holder.active = convertView.findViewById(R.id.fieldRadio);
            holder.menuPopup = convertView.findViewById(R.id.popupMenu);

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

        if (importDate != null) {
            importDate = importDate.split(" ")[0];
        }

        if (editDate != null) {
            editDate = editDate.split(" ")[0];
        }

        if (exportDate != null) {
            exportDate = exportDate.split(" ")[0];
        }

        holder.fieldName.setText(getItem(position).getExp_name());
        holder.count.setText(getItem(position).getCount());
        holder.importDate.setText(importDate);
        holder.editDate.setText(editDate);
        holder.exportDate.setText(exportDate);

        holder.active.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                fieldClick(getItem(position));
            }
        });

        if (ep.getString("FieldFile", "").contentEquals(holder.fieldName.getText())) {
            holder.active.setChecked(true);
        } else {
            holder.active.setChecked(false);
        }

        holder.menuPopup.setOnClickListener(makeMenuPopListener(position));

        return convertView;
    }

    private View.OnClickListener makeMenuPopListener(final int position) {
        return new View.OnClickListener() {
            // Do it when clicking ":"
            @Override
            public void onClick(final View view) {
                PopupMenu popup = new PopupMenu(FieldEditorActivity.thisActivity, view);
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
                final Activity thisActivity = FieldEditorActivity.thisActivity;
                final String strDel = thisActivity.getString(R.string.fields_delete);

                if (item.getTitle().equals(strDel)) {
                    AlertDialog alert = createDeleteItemAlertDialog(position);
                    alert.show();
                    DialogUtils.styleDialogs(alert);
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

                ConfigActivity.dt.deleteField(getItem(position).getExp_id());

                if (getItem(position).getExp_id() == ep.getInt("SelectedFieldExpId", -1)) {
                    setEditorItem(ep, null);
                }

                FieldEditorActivity.loadData();
                CollectActivity.reloadData = true;
            }
        };
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

        ConfigActivity.dt.switchField(selectedField.getExp_id());
        CollectActivity.reloadData = true;
        notifyDataSetChanged();

        // Check if this is a BrAPI field and show BrAPI info dialog if so
        if (selectedField.getExp_source() != null &&
                selectedField.getExp_source() != "" &&
                selectedField.getExp_source() != "local") {

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
    }
}