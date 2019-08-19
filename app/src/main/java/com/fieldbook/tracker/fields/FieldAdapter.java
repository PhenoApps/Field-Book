package com.fieldbook.tracker.fields;


import androidx.appcompat.app.AlertDialog;
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
import android.widget.Toast;

import com.fieldbook.tracker.ConfigActivity;
import com.fieldbook.tracker.MainActivity;
import com.fieldbook.tracker.R;

import java.util.ArrayList;

/**
 * Loads data on field manager screen
 */

class FieldAdapter extends BaseAdapter {

    private LayoutInflater mLayoutInflater;
    private ArrayList<FieldObject> list;
    private Context context;
    private SharedPreferences ep;

    FieldAdapter(Context context, ArrayList<FieldObject> list) {
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

    private class ViewHolder {
        ImageView menuPopup;
        TextView fieldName;
        TextView count;
        TextView importDate;
        TextView editDate;
        TextView exportDate;
        RadioButton active;
    }

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
                SharedPreferences.Editor ed = ep.edit();
                ed.putString("FieldFile", getItem(position).getExp_name());
                ed.putString("ImportUniqueName", getItem(position).getUnique_id());
                ed.putString("ImportFirstName", getItem(position).getPrimary_id());
                ed.putString("ImportSecondName", getItem(position).getSecondary_id());
                ed.putBoolean("ImportFieldFinished", true);
                ed.putBoolean("FieldSelected",true);
                ed.putString("lastplot", null);
                ed.putString("DROP1", null);
                ed.putString("DROP2", null);
                ed.putString("DROP3", null);
                ed.apply();

                ConfigActivity.dt.switchField(getItem(position).getExp_id());
                MainActivity.reloadData = true;
                notifyDataSetChanged();
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
                SharedPreferences.Editor ed = ep.edit();
                ed.putString("FieldFile", getItem(position).getExp_name());
                ed.putInt("ExpID", getItem(position).getExp_id());
                ed.putString("ImportUniqueName", getItem(position).getUnique_id());
                ed.putString("ImportFirstName", getItem(position).getPrimary_id());
                ed.putString("ImportSecondName", getItem(position).getSecondary_id());
                ed.putBoolean("ImportFieldFinished", true);
                ed.putBoolean("FieldSelected",true);
                ed.putString("lastplot", null);
                ed.putString("DROP1", null);
                ed.putString("DROP2", null);
                ed.putString("DROP3", null);
                ed.apply();

                ConfigActivity.dt.switchField(getItem(position).getExp_id());
                MainActivity.reloadData = true;
                notifyDataSetChanged();
            }
        });

        if (ep.getString("FieldFile", "").contentEquals(holder.fieldName.getText())) {
            holder.active.setChecked(true);
        } else {
            holder.active.setChecked(false);
        }

        holder.menuPopup.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                PopupMenu popup = new PopupMenu(FieldEditorActivity.thisActivity, v);
                //Inflating the Popup using xml file
                popup.getMenuInflater().inflate(R.menu.menu_field_listitem, popup.getMenu());

                //registering popup with OnMenuItemClickListener
                popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    public boolean onMenuItemClick(MenuItem item) {
                        if (item.getTitle().equals(FieldEditorActivity.thisActivity.getString(R.string.fields_delete))) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.AppAlertDialog);

                            builder.setTitle(context.getString(R.string.fields_delete_study));
                            builder.setMessage(context.getString(R.string.fields_delete_study_confirmation));

                            builder.setPositiveButton(context.getString(R.string.dialog_yes), new DialogInterface.OnClickListener() {

                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();

                                    ConfigActivity.dt.deleteField(getItem(position).getExp_id());

                                    if (getItem(position).getExp_name().equals(ep.getString("FieldFile", ""))) {
                                        SharedPreferences.Editor ed = ep.edit();
                                        ed.putString("FieldFile", null);
                                        ed.putBoolean("ImportFieldFinished", false);
                                        ed.putBoolean("FieldSelected",false);
                                        ed.putString("lastplot", null);
                                        ed.putString("ImportID", null);
                                        ed.putString("ImportUniqueName", null);
                                        ed.putString("ImportFirstName", null);
                                        ed.putString("ImportSecondName", null);
                                        ed.putString("DROP1", null);
                                        ed.putString("DROP2", null);
                                        ed.putString("DROP3", null);
                                        ed.apply();
                                    }

                                    FieldEditorActivity.loadData();
                                    MainActivity.reloadData = true;
                                }
                            });

                            builder.setNegativeButton(context.getString(R.string.dialog_no), new DialogInterface.OnClickListener() {

                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }

                            });

                            AlertDialog alert = builder.create();
                            alert.show();
                        }

                        if (item.getTitle().equals(FieldEditorActivity.thisActivity.getString(R.string.fields_study_statistics))) {
                            Toast.makeText(FieldEditorActivity.thisActivity, "Coming soon!", Toast.LENGTH_SHORT).show();
                        }

                        return false;
                    }
                });

                popup.show();//showing popup menu
            }
        });

        return convertView;
    }
}
