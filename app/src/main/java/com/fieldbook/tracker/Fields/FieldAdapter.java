package com.fieldbook.tracker.Fields;

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

import com.fieldbook.tracker.MainActivity;
import com.fieldbook.tracker.R;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Loads data on field manager screen
 */
public class FieldAdapter extends BaseAdapter {

    LayoutInflater mLayoutInflater;
    ArrayList<FieldObject> list;
    Context context;
    HashMap visibility;
    SharedPreferences ep;

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

    private class ViewHolder {
        ImageView menuPopup;
        String id;
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

            holder.fieldName = (TextView) convertView.findViewById(R.id.text1);
            holder.count = (TextView) convertView.findViewById(R.id.text2);
            holder.importDate = (TextView) convertView.findViewById(R.id.text3);
            holder.editDate = (TextView) convertView.findViewById(R.id.text4);
            holder.exportDate = (TextView) convertView.findViewById(R.id.text5);
            holder.active = (RadioButton) convertView.findViewById(R.id.fieldRadio);
            holder.menuPopup = (ImageView) convertView.findViewById(R.id.popupMenu);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        convertView.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                //MainActivity.importId = getItem(position).id;

                SharedPreferences.Editor ed = ep.edit();
                ed.putString("FieldFile", getItem(position).fileName);
                //ed.putString("ImportID", MainActivity.importId);
                ed.putString("ImportUniqueName", getItem(position).uniqueName);
                ed.putString("ImportFirstName", getItem(position).firstName);
                ed.putString("ImportSecondName", getItem(position).secondName);
                ed.putBoolean("ImportFieldFinished", true);
                ed.putString("DROP1", null);
                ed.putString("DROP2", null);
                ed.putString("DROP3", null);
                ed.apply();

                MainActivity.reloadData = true;

                notifyDataSetChanged();
            }
        });

        String importDate = getItem(position).importDate;
        String editDate = getItem(position).editDate;
        String exportDate = getItem(position).exportDate;

        if (importDate != null) {
            importDate = importDate.split(" ")[0];
        }

        if(editDate != null) {
            editDate = editDate.split(" ")[0];
        }

        if(exportDate != null) {
            exportDate = exportDate.split(" ")[0];
        }

        holder.id = getItem(position).id;
        holder.fieldName.setText(getItem(position).fileName);
        holder.count.setText(getItem(position).count);
        holder.importDate.setText(importDate);
        holder.editDate.setText(editDate);
        holder.exportDate.setText(exportDate);

        holder.active.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                //MainActivity.importId = getItem(position).id;

                SharedPreferences.Editor ed = ep.edit();
                ed.putString("FieldFile", getItem(position).fileName);
                //ed.putString("ImportID", MainActivity.importId);
                ed.putString("ImportUniqueName", getItem(position).uniqueName);
                ed.putString("ImportFirstName", getItem(position).firstName);
                ed.putString("ImportSecondName", getItem(position).secondName);
                ed.putBoolean("ImportFieldFinished", true);
                ed.putString("DROP1", null);
                ed.putString("DROP2", null);
                ed.putString("DROP3", null);
                ed.apply();

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

                                    //MainActivity.dt.dropTable(getItem(position).fileName);
                                    //MainActivity.dt.deleteExpId(getItem(position).fileName);

                                    if(getItem(position).fileName.equals(ep.getString("FieldFile",""))) {
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
                                        //MainActivity.importId = null;
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

                        return false;
                    }
                });

                popup.show();//showing popup menu
            }
        });

        return convertView;
    }
}