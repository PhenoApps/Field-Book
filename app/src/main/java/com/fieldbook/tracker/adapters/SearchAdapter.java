package com.fieldbook.tracker.adapters;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.dialogs.OperatorDialog;
import com.fieldbook.tracker.objects.SearchDialogDataModel;

import java.util.List;


public class SearchAdapter extends RecyclerView.Adapter<SearchAdapter.SearchViewHolder> {
    List<SearchDialogDataModel> dataSet;
    private OperatorDialog.OnOperatorClickedListener onOperatorClickedListener;
    private onEditTextChangedListener onEditTextChangedListener;
    private  onDeleteClickedListener onDeleteClickedListener;
    private static Context context;
    boolean isOnTextChanged = false;

    public SearchAdapter(List<SearchDialogDataModel> dataSet, OperatorDialog.OnOperatorClickedListener onOperatorClickedListener, onEditTextChangedListener onEditTextChangedListener, onDeleteClickedListener onDeleteClickedListener, Context context) {
        this.dataSet = dataSet;
        this.onOperatorClickedListener = onOperatorClickedListener;
        this.onEditTextChangedListener = onEditTextChangedListener;
        this.onDeleteClickedListener = onDeleteClickedListener;
        SearchAdapter.context = context;

    }

    public class SearchViewHolder extends RecyclerView.ViewHolder {
        TextView c;
        ImageView l;
        EditText e;
        ImageView d;

        public SearchViewHolder(View itemView) {
            super(itemView);
            c = itemView.findViewById(R.id.list_item_search_columns);
            l = itemView.findViewById(R.id.list_item_search_like);
            e = itemView.findViewById(R.id.list_item_search_search_text);
            d = itemView.findViewById(R.id.list_item_search_delete_btn);

            l.setOnClickListener(arg0 -> {
                OperatorDialog od = new OperatorDialog(context, onOperatorClickedListener, getBindingAdapterPosition());
                od.show();
            });

        }
    }

    @Override
    public SearchViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_search, parent, false);
        return new SearchViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull SearchViewHolder holder, int position) {
        holder.c.setText(dataSet.get(position).getAttribute());
        holder.l.setImageResource(dataSet.get(position).getImageResourceId());
        holder.e.setText(dataSet.get(position).getText());
        holder.e.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                isOnTextChanged = true;
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (isOnTextChanged) {
                    isOnTextChanged = false;
                    onEditTextChangedListener.onEditTextChanged(holder.getBindingAdapterPosition(), String.valueOf(editable));
                }
            }
        });


        holder.d.setOnClickListener(arg0 -> {
            onDeleteClickedListener.onDeleteClicked(holder.getBindingAdapterPosition());
        });
    }

    @Override
    public int getItemCount() {
        return dataSet.size();
    }

    public interface onEditTextChangedListener {
        void onEditTextChanged(int pos, String editText);
    }
    public interface onDeleteClickedListener {
        void onDeleteClicked(int pos);
    }
}