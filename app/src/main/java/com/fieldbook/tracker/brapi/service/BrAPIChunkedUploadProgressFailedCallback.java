package com.fieldbook.tracker.brapi.service;

import java.util.List;

public interface BrAPIChunkedUploadProgressFailedCallback<T> {
    void apply(int errorCode, List<T> failedChunk, boolean done);
}
