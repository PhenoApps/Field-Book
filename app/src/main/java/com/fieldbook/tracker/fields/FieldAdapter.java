package com.fieldbook.tracker.fields;

import android.support.v7.app.AlertDialog;
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
        final ViewHolder holder;

        ep = context.getSharedPreferences("Settings", 0);

        if (convertView == null) {
            holder = new ViewHolder();
            convertView = mLayoutInflater.inflate(R.layout.fieldline, null);

            holder.fieldName = (TextView) convertView.findViewById(R.id.field_name);
            holder.count = (TextView) convertView.findViewById(R.id.field_count);
            holder.importDate = (TextView) convertView.findViewById(R.id.field_import_date);
            holder.editDate = (TextView) convertView.findViewById(R.id.field_edit_date);
            holder.exportDate = (TextView) convertView.findViewById(R.id.field_export_date);
            holder.active = (RadioButton) convertView.findViewById(R.id.fieldRadio);
            holder.menuPopup = (ImageView) convertView.findViewById(R.id.popupMenu);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        convertView.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                SharedPreferences.Editor ed = ep.edit();
                ed.putString("FieldFile", getItem(position).exp_name);
                ed.putString("ImportUniqueName", getItem(position).unique_id);
                ed.putString("ImportFirstName", getItem(position).primary_id);
                ed.putString("ImportSecondName", getItem(position).secondary_id);
                ed.putBoolean("ImportFieldFinished", true);
                ed.putString("DROP1", null);
                ed.putString("DROP2", null);
                ed.putString("DROP3", null);
                ed.apply();

                MainActivity.reloadData = true;

                notifyDataSetChanged();
            }
        });

        String importDate = getItem(position).date_import;
        String editDate = getItem(position).date_edit;
        String exportDate = getItem(position).date_export;

        if (importDate != null) {
            importDate = importDate.split(" ")[0];
        }

        if(editDate != null) {
            editDate = editDate.split(" ")[0];
        }

        if(exportDate != null) {
            exportDate = exportDate.split(" ")[0];
        }

        holder.fieldName.setText(getItem(position).exp_name);
        holder.count.setText(getItem(position).count);
        holder.importDate.setText(importDate);
        holder.editDate.setText(editDate);
        holder.exportDate.setText(exportDate);

        holder.active.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                SharedPreferences.Editor ed = ep.edit();
                ed.putString("FieldFile", getItem(position).exp_name);
                ed.putInt("ExpID", getItem(position).exp_id);
                ed.putString("ImportUniqueName", getItem(position).unique_id);
                ed.putString("ImportFirstName", getItem(position).primary_id);
                ed.putString("ImportSecondName", getItem(position).secondary_id);
                ed.putBoolean("ImportFieldFinished", true);
                ed.putString("DROP1", null);
                ed.putString("DROP2", null);
                ed.putString("DROP3", null);
                ed.apply();

                MainActivity.dt.switchField(getItem(position).exp_id);
                MainActivity.reloadData = true;
                notifyDataSetChanged();
            }
        });

        if (ep.getString("FieldFile","").equals(holder.fieldName.getText())) {
            holder.active.setChecked(true);
        } else {
            holder.active.setChecked(false);
        }

        holder.menuPopup.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                PopupMenu popup = new PopupMenu(FieldEditorActivity.thisActivity, v);
                //Inflating the Popup using xml file
                popup.getMenuInflater().inflate(R.menu.fielditemmenu, popup.getMenu());

                //registering popup with OnMenuItemClickListener
                popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    public boolean onMenuItemClick(MenuItem item) {
                        if (item.getTitle().equals(FieldEditorActivity.thisActivity.getString(R.string.delete))) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.AppAlertDialog);

                            builder.setTitle(context.getString(R.string.delete_field));
                            builder.setMessage(context.getString(R.string.delete_field_confirmation));

                            builder.setPositiveButton(context.getString(R.string.yes), new DialogInterface.OnClickListener() {

                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();

                                    MainActivity.dt.deleteField(getItem(position).exp_id);

                                    if(getItem(position).exp_name.equals(ep.getString("FieldFile",""))) {
                                        SharedPreferences.Editor ed = ep.edit();
                                        ed.putString("FieldFile", null);
                                        ed.putBoolean("ImportFieldFinished", false);
                                        ed.putString("ImportID",null);
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

                            builder.setNegativeButton(context.getString(R.string.no), new DialogInterface.OnClickListener() {

                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }

                            });

                            AlertDialog alert = builder.create();
                            alert.show();
                        }

                        if(item.getTitle().equals(FieldEditorActivity.thisActivity.getString(R.string.statistics))) {
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