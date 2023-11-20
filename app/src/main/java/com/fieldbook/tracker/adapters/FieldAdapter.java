package com.fieldbook.tracker.adapters;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.CollectActivity;
import com.fieldbook.tracker.activities.FieldEditorActivity;
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

public class FieldAdapter extends RecyclerView.Adapter<FieldAdapter.ViewHolder> {

    private static final String TAG = "FieldAdapter";
    private final LayoutInflater mLayoutInflater;
    private final ArrayList<FieldObject> list;
    private final Context context;
    private final FieldSwitcher fieldSwitcher;
    public interface OnFieldSelectedListener {
        void onFieldSelected(FieldObject field);
    }
    private OnFieldSelectedListener listener;
    public void setOnFieldSelectedListener(OnFieldSelectedListener listener) {
        this.listener = listener;
    }
    public FieldAdapter(Context context, ArrayList<FieldObject> list, FieldSwitcher switcher) {
        this.context = context;
        mLayoutInflater = LayoutInflater.from(context);
        this.list = list;
        this.fieldSwitcher = switcher;
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
                FieldObject field = list.get(position);

                if (item.getItemId() == R.id.delete) {
                    createDeleteItemAlertDialog(position).show();
                } else if (item.getItemId() == R.id.sort) {
                    ((FieldSortController) context).showSortDialog(field);
                    //DialogUtils.styleDialogs(alert);
                }
                else if (item.getItemId() == R.id.syncObs) {
                    BrapiSyncObsDialog alert = new BrapiSyncObsDialog(context);
                    alert.setFieldObject(field);
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

                FieldObject field = list.get(position);
                ((FieldAdapterController) context).getDatabase().deleteField(field.getExp_id());

//                if (field.getExp_id() == ep.getInt(GeneralKeys.SELECTED_FIELD_ID, -1)) {
//                    setEditorItem(ep, null);
                    ((FieldEditorActivity) context).updateCurrentFieldSettings(null);
//                }

                ((FieldAdapterController) context).queryAndLoadFields();

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

//        setEditorItem(ep, selectedField);
        ((FieldEditorActivity) context).updateCurrentFieldSettings(selectedField);
        fieldSwitcher.switchField(selectedField);

        CollectActivity.reloadData = true;
        notifyDataSetChanged();

        // Check if this is a BrAPI field and show BrAPI info dialog if so
        String source = selectedField.getExp_source();
        if (source != null && !source.equals("csv") && !source.equals("excel")) {
            BrapiInfoDialog brapiInfo = new BrapiInfoDialog(context,
                    context.getResources().getString(R.string.brapi_info_message));
            brapiInfo.show();
        }
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        CheckBox checkBox;
        ImageView sourceIcon;
        TextView name;
        ImageView menuPopup;
        TextView count;

        ViewHolder(View itemView) {
            super(itemView);
            checkBox = itemView.findViewById(R.id.fieldCheckBox);
            sourceIcon = itemView.findViewById(R.id.fieldSourceIcon);
            name = itemView.findViewById(R.id.fieldName);
            count = itemView.findViewById(R.id.fieldCount);
            // initialize other views
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_field_recycler, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        FieldObject field = list.get(position);
        String name = field.getExp_name();
        holder.name.setText(name);
        holder.count.setText(field.getCount());

        // Set source icon
        String source = field.getExp_source();
//        Log.d("FieldAdapter", "Source for field " + name + ": " + source);
        if (source == null || "csv".equals(source)) {
            holder.sourceIcon.setImageResource(R.drawable.ic_file_csv);
        } else if ("excel".equals(source)) {
            holder.sourceIcon.setImageResource(R.drawable.ic_file_xls);
        } else { // brapi import
            holder.sourceIcon.setImageResource(R.drawable.ic_adv_brapi);
        }

        holder.itemView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onFieldSelected(field);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public void selectItem(int position) {
        FieldObject field = list.get(position);
        // Perform the actions you would do on click
        if (listener != null) {
            listener.onFieldSelected(field);
        }
        // Any additional logic for selecting the item
    }

}