package com.fieldbook.tracker.brapi.service;

import com.fieldbook.tracker.brapi.model.Observation;

import java.util.List;

public interface BrAPIChunkedUploadProgressFailedCallback<T> {
    void apply(int errorCode, List<T> failedChunk, boolean done);
}
