package com.fieldbook.tracker.brapi.service;

import android.content.Context;
import android.util.Log;

import androidx.arch.core.util.Function;

import com.fieldbook.tracker.brapi.model.Observation;
import com.fieldbook.tracker.preferences.GeneralKeys;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractBrAPIService implements BrAPIService {

    private static final String TAG = AbstractBrAPIService.class.getName();

    protected Integer getTimeoutValue(Context context) {
        String timeoutString = context.getSharedPreferences("Settings", 0)
                .getString(GeneralKeys.BRAPI_TIMEOUT, "120");

        int timeout = 120;

        try {
            if (timeoutString != null) {
                timeout = Integer.parseInt(timeoutString);
            }
        } catch (NumberFormatException nfe) {
            String message = nfe.getLocalizedMessage();
            if (message != null) {
                Log.d("FieldBookError", nfe.getLocalizedMessage());
            } else {
                Log.d("FieldBookError", "Timeout Preference number format error.");
            }
            nfe.printStackTrace();
        }

        return timeout;
    }

    public void createObservationsChunked(List<Observation> observations, BrAPIChunkedUploadProgressCallback<Observation> uploadProgressCallback, Function<Integer, Void> failFn) {
        saveChunks(observations, uploadProgressCallback, failFn, this::createObservations);
    }

    public void updateObservationsChunked(List<Observation> observations, BrAPIChunkedUploadProgressCallback<Observation> uploadProgressCallback, Function<Integer, Void> failFn) {
        saveChunks(observations, uploadProgressCallback, failFn, this::updateObservations);
    }

    private <T> void saveChunks(List<T> items, BrAPIChunkedUploadProgressCallback<T> uploadProgressCallback, Function<Integer, Void> failFn, SaveChunkFunction<T> processFn) {
        List<List<T>> chunkedItemLists = createChunks(items);

        /*
         Allow for up to two parallel write calls to happen at a time.
         May be worth enhancing this to be more dynamic based on the device specs
         */
        Semaphore requestSemaphore = new Semaphore(2);
        AtomicBoolean failed = new AtomicBoolean(false);
        for (int chunkNum = 0; chunkNum < chunkedItemLists.size(); chunkNum++) {
            List<T> chunk = chunkedItemLists.get(chunkNum);
            int finalChunkNum = chunkNum+1;

            try {
                requestSemaphore.acquire();
            } catch (InterruptedException e) {
                Log.e(TAG, "Error getting semaphore", e);
                failFn.apply(0);
                failed.set(true);
            }

            //once a semaphore is acquired, check that there weren't any failures while waiting
            if(failed.get()) {
                return; //break out of the loop and don't let any more requests be sent.  This could be removed if the UI is updated to keep track of multiple failures
            }

            Log.d(TAG,"Starting chunk " + finalChunkNum + "/" + chunkedItemLists.size());
            processFn.apply(chunk, input -> {
                Log.d(TAG,"Finished chunk " + finalChunkNum + "/" + chunkedItemLists.size());
                uploadProgressCallback.apply(input, finalChunkNum, chunk, (finalChunkNum + 1) == chunkedItemLists.size());
                requestSemaphore.release();
                return null;
            }, error -> {
                Log.d(TAG,"Finished chunk " + finalChunkNum + "/" + chunkedItemLists.size());
                Log.e(TAG,"error with chunk "+finalChunkNum+": " + error);
                failFn.apply(error);
                failed.set(true);
                requestSemaphore.release();
                return null;
            });
        }
    }

    protected <T> List<List<T>> createChunks(List<T> items) {
        return createChunks(items, 500); //TODO use pageSize for chunk?
    }

    protected <T> List<List<T>> createChunks(List<T> items, int chunkSize) {
        List<List<T>> chunkedItemLists = new ArrayList<>();

        List<T> currentChunk = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (i % chunkSize == 0 && i != 0) {
                chunkedItemLists.add(currentChunk);
                currentChunk = new ArrayList<>();
            }

            currentChunk.add(items.get(i));
        }

        if(currentChunk.size() > 0) {
            chunkedItemLists.add(currentChunk);
        }

        return chunkedItemLists;
    }
}
