package com.fieldbook.tracker.brapi.service;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.preference.PreferenceManager;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.preferences.GeneralKeys;
import com.fieldbook.tracker.preferences.PreferenceKeys;

public class BrapiPaginationManager {

    private Integer currentPage = 0;
    private Integer totalPages = 1;
    private Integer pageSize;

    private Button nextBtn;
    private Button prevBtn;
    private TextView pageIndicator;
    private final Context context;

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
        String pageSizeStr = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(PreferenceKeys.BRAPI_PAGE_SIZE, "50");

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

    public Integer getTotalPages() { return totalPages; }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    /**
     * Function to move the paginationManager to the next page
     */
    public void moveToNextPage() {
        currentPage++;
    }

    /**
     * Function to update the total number of pages
     * @param totalPages
     */
    public void updateTotalPages(Integer totalPages) {
        this.totalPages = totalPages;
    }

    public void updatePageInfo(Integer totalPages) {
        updateTotalPages(totalPages);
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
