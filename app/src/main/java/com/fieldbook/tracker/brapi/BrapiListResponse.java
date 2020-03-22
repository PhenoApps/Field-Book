package com.fieldbook.tracker.brapi;

import java.util.List;

import io.swagger.client.model.Metadata;

public class BrapiListResponse<T> {

    private Metadata metadata;
    private List<T> data;

    public Metadata getMetadata() {
        return metadata;
    }

    void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    public List<T> getData() {
        return data;
    }

    public void setData(List<T> data) {
        this.data = data;
    }

}