package com.fieldbook.tracker.brapi;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.BrapiTraitActivity;
import com.fieldbook.tracker.preferences.GeneralKeys;

import io.swagger.client.model.Metadata;

public class BrapiPaginationManager {

    private Integer currentPage = 0;
    private Integer totalPages = 1;
    private Integer pageSize;

    private Button nextBtn;
    private Button prevBtn;
    TextView pageIndicator;
    private Context context;

    public BrapiPaginationManager(Context context){
        this.context = context;
        // Make our prev and next buttons invisible
        nextBtn = ((Activity)context).findViewById(R.id.next);
        prevBtn = ((Activity)context).findViewById(R.id.prev);
        pageIndicator = ((Activity)context).findViewById(R.id.page_indicator);

        reset();
    }

    public BrapiPaginationManager(Integer page, Integer pageSize){
        this.context = null;

        currentPage = page;
        totalPages = 1;
        this.pageSize = pageSize;
    }

    public void reset() {
        // Initially make next and prev gone until we know there are more than 1 page.
        nextBtn.setVisibility(View.INVISIBLE);
        prevBtn.setVisibility(View.INVISIBLE);
        //set defaults
        currentPage = 0;
        totalPages = 1;
        pageSize = getDefaultPageSize();
    }

    public Integer getDefaultPageSize(){
        String pageSizeStr = context.getSharedPreferences("Settings", 0)
                .getString(GeneralKeys.BRAPI_PAGINATION, "1000");

        Integer pageSize = 1000;

        try {
            if (pageSizeStr != null) {
                pageSize = Integer.parseInt(pageSizeStr);
            }
        } catch (NumberFormatException nfe) {
            String message = nfe.getLocalizedMessage();
            if (message != null) {
                Log.d("FieldBookError", nfe.getLocalizedMessage());
            } else {
                Log.d("FieldBookError", "Pagination Preference number format error.");
            }
            nfe.printStackTrace();
        }

        return pageSize;
    }

    public Integer getPage() {
        return currentPage;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void updatePageInfo(Metadata metadata) {
        totalPages = metadata.getPagination().getTotalPages();
        refreshPageIndicator();
    }

    public void refreshPageIndicator() {
        pageIndicator.setText(String.format("Page %d of %d", currentPage + 1, totalPages));

        // Determine our button visibility. Not necessary if we only have 1 page.
        determineBtnVisibility();
    }

    public void determineBtnVisibility() {
        // Determine what buttons should be visible
        if (currentPage <= 0) {
            prevBtn.setVisibility(View.INVISIBLE);
        } else {
            prevBtn.setVisibility(View.VISIBLE);
        }

        if (currentPage >= (totalPages - 1)) {
            nextBtn.setVisibility(View.INVISIBLE);
        } else {
            nextBtn.setVisibility(View.VISIBLE);
        }
    }

    public void setNewPage(int id) {
        if(R.id.prev == id){
            if (currentPage > 0){
                currentPage = currentPage - 1;
            }else {
                currentPage = 0;
            }
        }else if (R.id.next == id){
            if (currentPage < totalPages - 1){
                currentPage = currentPage + 1;
            }else {
                currentPage = totalPages - 1;
            }
        }
    }

    public Context getContext(){
        return this.context;
    }
}
