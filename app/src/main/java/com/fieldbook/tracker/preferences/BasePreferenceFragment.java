package com.fieldbook.tracker.preferences;

import android.os.Bundle;

import androidx.preference.PreferenceFragmentCompat;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class BasePreferenceFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

    }
}
