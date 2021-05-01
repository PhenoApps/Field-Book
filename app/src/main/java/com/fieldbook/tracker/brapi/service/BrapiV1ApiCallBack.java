package com.fieldbook.tracker.brapi.service;

import android.util.Log;

import java.util.List;
import java.util.Map;

import io.swagger.client.ApiCallback;
import io.swagger.client.ApiException;

public abstract class BrapiV1ApiCallBack<T> implements ApiCallback<T> {

    @Override
    public void onFailure(ApiException error, int i, Map<String, List<String>> map) {
        Log.e("error-of", error.toString());
    }

    @Override
    public void onUploadProgress(long l, long l1, boolean b) {
    }

    @Override
    public void onDownloadProgress(long l, long l1, boolean b) {
    }
}
