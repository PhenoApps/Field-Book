package com.fieldbook.shared.preferences

enum class GeneralKeys(val key: String) {
    // Crashlytics
    CRASHLYTICS_KEY_USER_TOKEN("user_token"),
    CRASHLYTICS_ID_ENABLED("com.tracker.fieldbook.preference.crashlytics.user_id_enabled"),
    CRASHLYTICS_ID_REFRESH("com.tracker.fieldbook.preference.crashlytics.refresh"),
    CRASHLYTICS_ID("com.tracker.fieldbook.preference.crashlytics.id"),
    FIRST_NAME("FirstName"),
    LAST_NAME("LastName"),
    PERSON_UPDATE("PersonUpdate"),
    MODIFY_PROFILE_SETTINGS("ModifyProfileSettings"),
    DEVICE_NAME("DeviceName"),

    // Appearance
    INFOBAR_UPDATE("INFOBAR_UPDATE"),
    APPLICATION_THEME("APPLICATION_THEME"),
    RESTORE_DEFAULT_THEME("RESTORE_DEFAULT_THEME"),

    // General
    TUTORIAL_MODE("Tips"),
    NEXT_ENTRY_NO_DATA("NextEmptyPlot"),
    HIDE_ENTRIES_WITH_DATA("com.fieldbook.tracker.HIDE_ENTRIES"),
    ATTR_CHOOSER_DIALOG_TAB("ATTR_CHOOSER_DIALOG_TAB"),

    // Files and Naming
    DEFAULT_STORAGE_LOCATION_PREFERENCE("DEFAULT_STORAGE_LOCATION_PREFERENCE"),
    DEFAULT_STORAGE_LOCATION_DIRECTORY("DEFAULT_STORAGE_LOCATION_DIRECTORY"),
    FILE_NAME_FORMAT("FILE_NAME_FORMAT"),
    PHOTO_NAME_FORMAT("PHOTO_NAME_FORMAT"),

    // GeoNav
    GEONAV_AUTO("com.fieldbook.tracker.geonav.GEONAV_AUTO"),
    GEONAV_COMPASS("com.fieldbook.tracker.geonav.GEONAV_COMPASS"),
    GEONAV_CONFIG_AUDIO_ON_DROP("com.fieldbook.tracker.geonav.AUDIO_ON_DROP"),
    GEONAV_CONFIG_DEGREE_PRECISION("com.fieldbook.tracker.geonav.DEGREE_PRECISION"),
    GEONAV_POPUP_DISPLAY("com.fieldbook.tracker.geonav.POPUP_DISPLAY"),

    // GNSS
    GNSS_LAST_PAIRED_DEVICE_NAME("GNSS_LAST_PAIRED_DEVICE_NAME"),
    GNSS_LAST_CHOSEN_PRECISION("GNSS_LAST_CHOSEN_PRECISION"),
    GNSS_WARNED_PRECISION("GNSS_WARNED_PRECISION"),
    GNSS_PRECISION_OK_SOUND("GNSS_PRECISION_OK_SOUND"),

    // Canon
    CANON_SSID_NAME("com.tracker.fieldbook.preferences.keys.canon.ssid_name"),
    CANON_IP("com.tracker.fieldbook.preferences.keys.canon.ip"),
    CANON_PORT("com.tracker.fieldbook.preferences.keys.canon.port"),
    CANON_DEBUG("com.tracker.fieldbook.preferences.keys.canon.debug"),
    CANON_HELP("com.tracker.fieldbook.preferences.keys.canon.help"),
    CANON_CAMERA_PREVIEW("com.fieldbook.tracker.traits.camera.canon.PREVIEW"),
    LAST_TIME_OPENED("LastTimeAppOpened"),
    ASKED_SINCE_OPENED("AskedSinceAppOpened"),
    VERIFY_USER("VerifyUserEvery24Hours"),
    TRAITS_EXPORTED("TraitsExported"),
    ALL_TRAITS_VISIBLE("allTraitsVisible"),
    LAST_USED_TRAIT("com.fieldbook.tracker.LAST_USED_TRAIT"),
    LAST_USED_RESOURCE_FILE("com.fieldbook.tracker.LAST_USED_RESOURCE_FILE"),

    // themes
    SAVED_DATA_COLOR("SAVED_DATA_COLOR"),
    THEME_FLAG("key_preferences_theme_theme_flag"),
    FIRST_RUN("FirstRun"),
    UPDATE_VERSION("UpdateVersion"),
    REGION("region"),
    TIPS_CONFIGURED("TipsConfigured"),

    // shared preferences file name
    SHARED_PREF_FILE_NAME("Settings"),

    // used to get the name of the currently selected field that is saved in preferences
    FIELD_FILE("FieldFile"),

    // field alias is currently used from imported brapi studies, it is the study db id
    FIELD_ALIAS("FieldAlias"),

    // key that saves where the field source is from local, brapi, etc.
    FIELD_SOURCE("ImportExpSource"),

    // used to get the current study table's integer primary key
    SELECTED_FIELD_ID("SelectedFieldExpId"),

    // next three are used to save/get the currently selected field's unique, primary and secondary keys
    UNIQUE_NAME("ImportUniqueName"),
    PRIMARY_NAME("ImportFirstName"),
    SECONDARY_NAME("ImportSecondName"),

    // Used to get name of observation level of currently selected field
    FIELD_OBS_LEVEL("FieldObsLevel"),

    // flag saved in preferences to determine if import is completed
    IMPORT_FIELD_FINISHED("ImportFieldFinished"),

    // preference key to save the last plot during collect activity
    LAST_PLOT("lastplot"),

    // collect search activity defaults
    SEARCH_COLUMN_DEFAULT("SEARCH_COLUMN_DEFAULT"),
    SEARCH_LIKE_DEFAULT("SEARCH_LIKE_DEFAULT"),
    DATA_LOCK_STATE("DataLockState"),

    // export flags
    EXPORT_COLUMNS_UNIQUE("EXPORT_COLUMNS_UNIQUE"),
    EXPORT_COLUMNS_ALL("EXPORT_COLUMNS_ALL"),
    EXPORT_TRAITS_ALL("EXPORT_TRAITS_ALL"),
    EXPORT_TRAITS_ACTIVE("EXPORT_TRAITS_ACTIVE"),
    EXPORT_FORMAT_TABLE("EXPORT_FORMAT_TABLE"),
    EXPORT_FORMAT_DATABASE("EXPORT_FORMAT_DATABASE"),
    DIALOG_EXPORT_BUNDLE_CHECKED("com.fieldbook.tracker.DIALOG_EXPORT_BUNDLE_CHECKED"),
    EXPORT_OVERWRITE("Overwrite"),

    // Field detail
    FIELD_DETAIL_OVERVIEW_COLLAPSED("FIELD_DETAIL_OVERVIEW_COLLAPSED"),
    FIELD_DETAIL_DATA_COLLAPSED("FIELD_DETAIL_DATA_COLLAPSED"),

    // Data grid
    DATAGRID_PREFIX_TRAIT("com.fieldbook.tracker.datagrid.DATAGRID_PREFIX_TRAIT"),

    // summary filter
    SUMMARY_FILTER_ATTRIBUTES("com.fieldbook.tracker.summary.SUMMARY_FILTER_ATTRIBUTES"),

    // Other

    SORT_ORDER("SORT_ORDER");

    // ...add remaining keys as needed...
}
