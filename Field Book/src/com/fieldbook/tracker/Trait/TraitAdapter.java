package com.fieldbook.tracker.Trait;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.fieldbook.tracker.MainActivity;
import com.fieldbook.tracker.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;

/**
 * Loads data on trait editor screen
 */
public class TraitAdapter extends BaseAdapter {

    LayoutInflater mLayoutInflater;
    ArrayList<TraitObject> list;
    Context context;
    OnItemClickListener listener;
    HashMap visibility;

    public TraitAdapter(Context context, ArrayList<TraitObject> list, OnItemClickListener listener, HashMap visibility) {
        this.context = context;
        mLayoutInflater = LayoutInflater.from(context);
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
        TextView format;
        CheckBox visible;
        ImageView up;
        ImageView down;
        Button copy;
        Button del;
        Button edt;
        String id;
        String realPosition;
    }

    public View getView(final int position, View convertView, final ViewGroup parent) {
        final ViewHolder holder;

        if (convertView == null) {
            holder = new ViewHolder();
            convertView = mLayoutInflater.inflate(R.layout.traitline, null);
            holder.name = (TextView) convertView.findViewById(R.id.text1);
            holder.format = (TextView) convertView.findViewById(R.id.text2);
            holder.visible = (CheckBox) convertView.findViewById(R.id.visible);
            holder.up = (ImageView) convertView.findViewById(R.id.upBtn);
            holder.down = (ImageView) convertView.findViewById(R.id.downBtn);
            holder.copy = (Button) convertView.findViewById(R.id.copyBtn);
            holder.del = (Button) convertView.findViewById(R.id.delBtn);
            holder.edt = (Button) convertView.findViewById(R.id.edtBtn);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        convertView.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
            }
        });


        holder.id = getItem(position).id;
        holder.realPosition = getItem(position).realPosition;

        holder.name.setText(getItem(position).trait);
        holder.format.setText(getItem(position).format);

        // Check or uncheck the list items based on existing
        // visibility
        if (visibility != null) {
            if (visibility.get(holder.name.getText().toString()) != null) {
                if (visibility.get(holder.name.getText().toString()).equals("true")) {
                    holder.visible.setChecked(true);
                } else
                    holder.visible.setChecked(false);
            }
        }

        holder.visible.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton arg0, boolean position) {

                if (holder.visible.isChecked()) {
                    MainActivity.dt.updateTraitVisibility(holder.name.getText().toString(), true);
                } else {
                    MainActivity.dt.updateTraitVisibility(holder.name.getText().toString(), false);
                }

                MainActivity.reloadData = true;
            }
        });

        holder.del.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(context);

                builder.setTitle(context.getString(R.string.deletetrait));
                builder.setMessage(context.getString(R.string.areyousure));

                builder.setPositiveButton(context.getString(R.string.yes), new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();

                        MainActivity.dt.deleteTrait(holder.id);
                        TraitEditorActivity.loadData();
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
        });

        holder.up.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (position > 0) {
                    String prevID = getItem(position - 1).id;
                    String prevPosition = getItem(position - 1).realPosition;

                    String currentID = holder.id;
                    String currentPosition = holder.realPosition;

                    MainActivity.dt.updateTraitPosition(prevID, currentPosition);
                    MainActivity.dt.updateTraitPosition(currentID, prevPosition);
                    TraitEditorActivity.loadData();
                }
            }
        });

        holder.down.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (position < getCount() - 1) {
                    String nextID = getItem(position + 1).id;
                    String nextPosition = getItem(position + 1).realPosition;

                    String currentID = holder.id;
                    String currentPosition = holder.realPosition;

                    MainActivity.dt.updateTraitPosition(nextID, currentPosition);
                    MainActivity.dt.updateTraitPosition(currentID, nextPosition);
                    TraitEditorActivity.loadData();
                }
            }
        });

        holder.copy.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                int pos = MainActivity.dt.getMaxPositionFromTraits() + 1;

                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
                String postfix = "-" + context.getString(R.string.copy) + "-" + dateFormat.format(Calendar.getInstance().getTime());

                MainActivity.dt.insertTraits(getItem(position).trait + postfix, getItem(position).format, getItem(position).defaultValue, getItem(position).minimum, getItem(position).maximum, getItem(position).details, getItem(position).categories, "true", String.valueOf(pos));
                TraitEditorActivity.loadData();
            }
        });

        holder.edt.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                listener.onItemClick((AdapterView) parent, v, position, v.getId());
            }
        });

        return convertView;
    }

}
