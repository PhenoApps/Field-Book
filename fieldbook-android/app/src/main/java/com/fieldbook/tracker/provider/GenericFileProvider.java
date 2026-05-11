package com.fieldbook.tracker.provider;

import androidx.core.content.FileProvider;

import com.fieldbook.tracker.BuildConfig;

public class GenericFileProvider extends FileProvider {

    public static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".fileprovider";
}