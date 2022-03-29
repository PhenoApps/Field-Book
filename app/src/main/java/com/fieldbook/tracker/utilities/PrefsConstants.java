package com.fieldbook.tracker.utilities;

public class PrefsConstants {
    public static final String IMPORT_SECOND_NAME = "ImportSecondName";
    public static final String UPDATE_VERSION = "UpdateVersion";
    public static final String REGION = "region";
    public static final String IMPORT_FIRST_NAME = "ImportFirstName";
    public static final String TIPS = "Tips";
    public static final String TRAITS_EXPORTED = "TraitsExported";
    public static final String IMPORT_UNIQUE_NAME = "ImportUniqueName";
    public static final String LANGUAGE = "language";
    public static final String TIPS_CONFIGURED = "TipsConfigured";
    public static final String SELECTED_FIELD_ID = "SelectedFieldExpId";
    public static final String CREATE_TRAIT_FINISHED = "CreateTraitFinished";

    //shared preferences file name
    public static final String SHARED_PREF_FILE_NAME = "Settings";

    //used to get the name of the currently selected field that is saved in preferences
    //example: field_sample how to get: sharedPreferences.getString(Constants.FIELD_FILE, "")
    public static final String FIELD_FILE = "FieldFile";

    //key that saves where the field source is from local, brapi, etc.
    public static final String FIELD_SOURCE = "ImportExpSource";

    //used to get the current study table's integer primary key
    public static final String SELECTED_FIELD_EXP_ID = "SelectedFieldExpId";

    //next three are used to save/get the currently selected field's unique, primary and secondary keys
    public static final String UNIQUE_NAME = "ImportUniqueName";
    public static final String PRIMARY_NAME = "ImportFirstName";
    public static final String SECONDARY_NAME = "ImportSecondName";

    //flag saved in preferences to determine if import is completed
    public static final String IMPORT_FIELD_FINISHED = "ImportFieldFinished";

    //preference key to save the last plot during collect activity
    public static final String LAST_PLOT = "lastplot";

    //export flags
    public static final String EXPORT_COLUMNS_UNIQUE = "EXPORT_COLUMNS_UNIQUE";
    public static final String EXPORT_COLUMNS_ALL = "EXPORT_COLUMNS_ALL";
    public static final String EXPORT_TRAITS_ALL = "EXPORT_TRAITS_ALL";
    public static final String EXPORT_TRAITS_ACTIVE = "EXPORT_TRAITS_ACTIVE";
    public static final String EXPORT_FORMAT_TABLE = "EXPORT_FORMAT_TABLE";
    public static final String EXPORT_FORMAT_DATABSE = "EXPORT_FORMAT_DATABASE";
    public static final String EXPORT_OVERWRITE = "Overwrite";
}
