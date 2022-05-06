package com.fieldbook.tracker.brapi.service;

import java.util.List;

public interface BrAPIChunkedUploadProgressCallback<T> {
    void apply(List<T> input, int completedChunkNum, List<T> chunks, boolean done);
}
