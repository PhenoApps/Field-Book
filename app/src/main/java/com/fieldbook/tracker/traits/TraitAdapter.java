package com.fieldbook.tracker.traits;

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

import com.fieldbook.tracker.ConfigActivity;
import com.fieldbook.tracker.MainActivity;
import com.fieldbook.tracker.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

/**
 * Loads data on trait editor screen
 */
class TraitAdapter extends BaseAdapter {

    ArrayList<TraitObject> list;
    private Context context;
    private OnItemClickListener listener;
    private HashMap visibility;

    TraitAdapter(Context context, int resource, ArrayList<TraitObject> list, OnItemClickListener listener, HashMap visibility) {
        this.context = context;
        this.list = list;
        this.listener = listener;
        this.visibility = visibility;
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

    private class ViewHolder {
        TextView name;
        ImageView format;
        CheckBox visible;
        ImageView dragSort;
        ImageView menuPopup;
        String id;
        String realPosition;
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

        holder.id = getItem(position).id;
        holder.realPosition = getItem(position).realPosition;
        holder.name.setText(getItem(position).trait);

        switch (getItem(position).format) {
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
                    visibility.put(holder.name.getText().toString(),"true");
                } else {
                    ConfigActivity.dt.updateTraitVisibility(holder.name.getText().toString(), false);
                    visibility.put(holder.name.getText().toString(),false);
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
                popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    public boolean onMenuItemClick(MenuItem item) {
                        if (item.getTitle().equals(TraitEditorActivity.thisActivity.getString(R.string.traits_options_copy))) {
                            int pos = ConfigActivity.dt.getMaxPositionFromTraits() + 1;

                            String traitName = getItem(position).trait;

                            if (traitName.contains("-Copy")) {
                                traitName = traitName.substring(0, traitName.indexOf("-Copy"));
                            }

                            String newTraitName = "";

                            String[] allTraits = ConfigActivity.dt.getAllTraits();

                            for (int i = 0; i < allTraits.length; i++) {
                                newTraitName = traitName + "-Copy-" + "(" + Integer.toString(i) + ")";

                                if (!Arrays.asList(allTraits).contains(newTraitName)) {
                                    break;
                                }
                            }

                            TraitObject trait = getItem(position);
                            trait.trait = newTraitName;
                            trait.visible = true;
                            trait.realPosition = String.valueOf(pos);

                            //MainActivity.dt.insertTraits(newTraitName, getItem(position).format, getItem(position).defaultValue, getItem(position).minimum, getItem(position).maximum, getItem(position).details, getItem(position).categories, "true", String.valueOf(pos));
                            ConfigActivity.dt.insertTraits(trait);
                            TraitEditorActivity.loadData();
                            MainActivity.reloadData = true;

                        } else if (item.getTitle().equals(TraitEditorActivity.thisActivity.getString(R.string.traits_options_delete))) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.AppAlertDialog);

                            builder.setTitle(context.getString(R.string.traits_options_delete_title));
                            builder.setMessage(context.getString(R.string.traits_warning_delete));

                            builder.setPositiveButton(context.getString(R.string.dialog_yes), new DialogInterface.OnClickListener() {

                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();

                                    ConfigActivity.dt.deleteTrait(holder.id);
                                    TraitEditorActivity.loadData();
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

                        } else if (item.getTitle().equals(TraitEditorActivity.thisActivity.getString(R.string.traits_options_edit))) {
                            listener.onItemClick((AdapterView) parent, v, position, v.getId());
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