package com.fieldbook.tracker.brapi.service;

import androidx.arch.core.util.Function;

import java.util.List;

public interface SaveChunkFunction<T> {
    void apply(List<T> chunk, final Function<List<T>, Void> successFn,
               final Function<Integer, Void> failFunction);
}
