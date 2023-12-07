package com.fieldbook.tracker.application

import android.content.Context
import android.content.SharedPreferences
import com.fieldbook.tracker.preferences.GeneralKeys
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.qualifiers.ActivityContext

@Module
@InstallIn(ActivityComponent::class)
object ActivityModule {

    @Provides
    fun providesPreferences(@ActivityContext context: Context): SharedPreferences =
        context.getSharedPreferences(GeneralKeys.SHARED_PREF_FILE_NAME, Context.MODE_PRIVATE)
}