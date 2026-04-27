package com.fieldbook.tracker.brapi.service;

import org.brapi.client.v2.ApiCallback;
import org.brapi.client.v2.model.exceptions.ApiException;

import java.util.List;
import java.util.Map;

public abstract class BrapiV2ApiCallBack<T> implements ApiCallback<T> {

    @Override
    public abstract void onFailure(ApiException error, int statusCode, Map<String, List<String>> responseHeaders);

    @Override
    public void onUploadProgress(long l, long l1, boolean b) {
    }

    @Override
    public void onDownloadProgress(long l, long l1, boolean b) {
    }
}
