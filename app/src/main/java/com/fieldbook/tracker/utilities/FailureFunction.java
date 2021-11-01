package com.fieldbook.tracker.utilities;

public interface FailureFunction<T> {
    void apply(T failureInput);
}
