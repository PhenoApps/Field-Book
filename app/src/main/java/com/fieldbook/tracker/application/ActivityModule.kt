package com.fieldbook.tracker.application

import android.content.Context
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.offbeat.traits.formats.Formats
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
    fun providesPreferences(@ActivityContext context: Context) =
        context.getSharedPreferences(GeneralKeys.SHARED_PREF_FILE_NAME, Context.MODE_PRIVATE)


    //TODO this should be @Singleton in SingletonComponent scope
    @Provides
    fun providesDatabase(@ActivityContext context: Context): DataHelper {

        return DataHelper(context)

    }

    @Provides
    fun providesTraitFormats(): List<Formats> {

        return listOf(
            Formats.AUDIO,
            Formats.BOOLEAN,
            Formats.CAMERA,
            Formats.CATEGORICAL,
            Formats.COUNTER,
            Formats.DATE,
            Formats.LOCATION,
            Formats.NUMERIC,
            Formats.PERCENT,
            Formats.TEXT,
            Formats.DISEASE_RATING,
            Formats.GNSS,
            Formats.GO_PRO,
            Formats.LABEL_PRINT
        )
    }

//    @Provides
//    fun providesTraitFormatDefinitions(database: DataHelper) {
//
//        return
//    }
}