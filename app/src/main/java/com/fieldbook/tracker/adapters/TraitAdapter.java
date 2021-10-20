package com.fieldbook.tracker.adapters;

import androidx.appcompat.app.AlertDialog;

import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.fieldbook.tracker.activities.ConfigActivity;
import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.activities.CollectActivity;
import com.fieldbook.tracker.R;
import com.fieldbook.tracker.objects.TraitObject;
import com.fieldbook.tracker.activities.TraitEditorActivity;
import com.fieldbook.tracker.utilities.DialogUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

/**
 * Loads data on trait editor screen
 */
public class TraitAdapter extends BaseAdapter {

    public Boolean infoDialogShown = false;
    public ArrayList<TraitObject> list;
    private Context context;
    private OnItemClickListener listener;
    private HashMap visibility;

    public TraitAdapter(Context context, int resource, ArrayList<TraitObject> list, OnItemClickListener listener, HashMap visibility, Boolean dialogShown) {
        this.context = context;
        this.list = list;
        this.listener = listener;
        this.visibility = visibility;
        // dialog shown indicates whether dialog has been shown on activity or not
        this.infoDialogShown = dialogShown;
    }

    public int getCount() {
        return list.size();
    }

    public TraitObject getItem(int position) {
        return list.get(position);
    }

    public long getItemId(int position) {

        if (position < 0 || position >= visibility.size()) {
            return -1;
        }

        return position;
    }

    public View getView(final int position, View convertView, final ViewGroup parent) {
        final ViewHolder holder;

        if (convertView == null) {
            LayoutInflater vi = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = vi.inflate(R.layout.listitem_trait, parent, false);

            holder = new ViewHolder();
            holder.name = convertView.findViewById(R.id.field_name);
            holder.format = convertView.findViewById(R.id.traitType);
            holder.visible = convertView.findViewById(R.id.visible);
            holder.dragSort = convertView.findViewById(R.id.dragSort);
            holder.menuPopup = convertView.findViewById(R.id.popupMenu);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        holder.id = getItem(position).getId();
        holder.realPosition = String.valueOf(getItem(position).getRealPosition());
        holder.name.setText(getItem(position).getTrait());

        switch (getItem(position).getFormat()) {
            case "numeric":
                holder.format.setBackgroundResource(R.drawable.ic_trait_numeric);
                break;
            case "categorical":
                holder.format.setBackgroundResource(R.drawable.ic_trait_categorical);
                break;
            case "date":
                holder.format.setBackgroundResource(R.drawable.ic_trait_date);
                break;
            case "percent":
                holder.format.setBackgroundResource(R.drawable.ic_trait_percent);
                break;
            case "boolean":
                holder.format.setBackgroundResource(R.drawable.ic_trait_boolean);
                break;
            case "text":
                holder.format.setBackgroundResource(R.drawable.ic_trait_text);
                break;
            case "photo":
                holder.format.setBackgroundResource(R.drawable.ic_trait_camera);
                break;
            case "audio":
                holder.format.setBackgroundResource(R.drawable.ic_trait_audio);
                break;
            case "counter":
                holder.format.setBackgroundResource(R.drawable.ic_trait_counter);
                break;
            case "disease rating":
                holder.format.setBackgroundResource(R.drawable.ic_trait_disease_rating);
                break;
            case "rust rating":
                holder.format.setBackgroundResource(R.drawable.ic_trait_disease_rating);
                break;
            case "multicat":
                holder.format.setBackgroundResource(R.drawable.ic_trait_multicat);
                break;
            case "location":
                holder.format.setBackgroundResource(R.drawable.ic_trait_location);
                break;
            case "barcode":
                holder.format.setBackgroundResource(R.drawable.ic_trait_barcode);
                break;
            case "zebra label print":
                holder.format.setBackgroundResource(R.drawable.ic_trait_labelprint);
                break;
            case "gnss":
                holder.format.setBackgroundResource(R.drawable.ic_trait_gnss);
                break;
            default:
                holder.format.setBackgroundResource(R.drawable.ic_reorder);
                break;
        }

        // Check or uncheck the list items
        if (visibility != null) {
            if (visibility.get(holder.name.getText().toString()) != null) {
                if (visibility.get(holder.name.getText().toString()).equals("true")) {
                    holder.visible.setChecked(true);
                } else {
                    holder.visible.setChecked(false);
                }
            }
        }

        holder.visible.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton arg0, boolean isChecked) {
                if (holder.visible.isChecked()) {
                    ConfigActivity.dt.updateTraitVisibility(holder.name.getText().toString(), true);
                    visibility.put(holder.name.getText().toString(), "true");


                } else {
                    ConfigActivity.dt.updateTraitVisibility(holder.name.getText().toString(), false);
                    visibility.put(holder.name.getText().toString(), false);
                }

            }
        });

        holder.visible.setOnClickListener(new OnClickListener() {
            // We make this separate form the on check changed listener so that we can
            // separate the difference between user interaction and programmatic checking.

            @Override
            public void onClick(View v) {

                // Only show dialog if it hasn't been show yet
                if (!infoDialogShown) {

                    // Check if the button is checked or not.
                    CheckBox visibleCheckBox = (CheckBox) v;
                    if (visibleCheckBox.isChecked()) {

                        // Show our BrAPI info box if this is a non-BrAPI trait
                        String traitName = holder.name.getText().toString();
                        infoDialogShown = TraitEditorActivity.displayBrapiInfo(context, new DataHelper(context), traitName, false);

                    }

                }
            }
        });

        holder.menuPopup.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                PopupMenu popup = new PopupMenu(TraitEditorActivity.thisActivity, v);
                //Inflating the Popup using xml file
                popup.getMenuInflater().inflate(R.menu.menu_trait_listitem, popup.getMenu());

                //registering popup with OnMenuItemClickListener
                popup.setOnMenuItemClickListener(createTraitListListener(parent, holder, v, position));

                popup.show();//showing popup menu

            }
        });

        return convertView;
    }

    private PopupMenu.OnMenuItemClickListener createTraitListListener(
            final ViewGroup parent, final ViewHolder holder,
            final View v, final int position) {
        return new PopupMenu.OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getTitle().equals(TraitEditorActivity.thisActivity.getString(R.string.traits_options_copy))) {
                    copyTrait(position);
                } else if (item.getTitle().equals(TraitEditorActivity.thisActivity.getString(R.string.traits_options_delete))) {
                    deleteTrait(holder);
                } else if (item.getTitle().equals(TraitEditorActivity.thisActivity.getString(R.string.traits_options_edit))) {
                    listener.onItemClick((AdapterView) parent, v, position, v.getId());
                }

                return false;
            }
        };
    }

    private void copyTrait(final int position) {
        int pos = ConfigActivity.dt.getMaxPositionFromTraits() + 1;

        String traitName = getItem(position).getTrait();
        final String newTraitName = copyTraitName(traitName);

        TraitObject trait = getItem(position);
        trait.setTrait(newTraitName);
        trait.setVisible(true);
        trait.setRealPosition(pos);

        //MainActivity.dt.insertTraits(newTraitName, getItem(position).format, getItem(position).defaultValue, getItem(position).minimum, getItem(position).maximum, getItem(position).details, getItem(position).categories, "true", String.valueOf(pos));
        ConfigActivity.dt.insertTraits(trait);
        TraitEditorActivity.loadData();
        CollectActivity.reloadData = true;
    }

    private String copyTraitName(String traitName) {
        if (traitName.contains("-Copy")) {
            traitName = traitName.substring(0, traitName.indexOf("-Copy"));
        }

        String newTraitName = "";

        String[] allTraits = ConfigActivity.dt.getAllTraits();

        for (int i = 0; i < allTraits.length; i++) {
            newTraitName = traitName + "-Copy-(" + Integer.toString(i) + ")";
            if (!Arrays.asList(allTraits).contains(newTraitName)) {
                return newTraitName;
            }
        }
        return "";    // not come here
    }

    private void deleteTrait(final ViewHolder holder) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.AppAlertDialog);

        builder.setTitle(context.getString(R.string.traits_options_delete_title));
        builder.setMessage(context.getString(R.string.traits_warning_delete));

        builder.setPositiveButton(context.getString(R.string.dialog_yes), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                ConfigActivity.dt.deleteTrait(holder.id);
                TraitEditorActivity.loadData();
                CollectActivity.reloadData = true;
            }
        });

        builder.setNegativeButton(context.getString(R.string.dialog_no), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        AlertDialog alert = builder.create();
        alert.show();
        DialogUtils.styleDialogs(alert);
    }

    private class ViewHolder {
        TextView name;
        ImageView format;
        CheckBox visible;
        ImageView dragSort;
        ImageView menuPopup;
        String id;
        String realPosition;
    }
}