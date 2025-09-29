package com.fieldbook.shared.preferences

object GeneralKeys {
    // Crashlytics
    const val CRASHLYTICS_KEY_USER_TOKEN = "user_token"
    const val CRASHLYTICS_ID_ENABLED = "com.tracker.fieldbook.preference.crashlytics.user_id_enabled"
    const val CRASHLYTICS_ID_REFRESH = "com.tracker.fieldbook.preference.crashlytics.refresh"
    const val CRASHLYTICS_ID = "com.tracker.fieldbook.preference.crashlytics.id"
    const val FIRST_NAME = "FirstName"
    const val LAST_NAME = "LastName"
    const val PERSON_UPDATE = "PersonUpdate"
    const val MODIFY_PROFILE_SETTINGS = "ModifyProfileSettings"
    const val DEVICE_NAME = "DeviceName"
    // Appearance
    const val INFOBAR_UPDATE = "INFOBAR_UPDATE"
    const val APPLICATION_THEME = "APPLICATION_THEME"
    const val RESTORE_DEFAULT_THEME = "RESTORE_DEFAULT_THEME"
    // General
    const val TUTORIAL_MODE = "Tips"
    const val NEXT_ENTRY_NO_DATA = "NextEmptyPlot"
    const val HIDE_ENTRIES_WITH_DATA = "com.fieldbook.tracker.HIDE_ENTRIES"
    const val ATTR_CHOOSER_DIALOG_TAB = "ATTR_CHOOSER_DIALOG_TAB"
    // Files and Naming
    const val DEFAULT_STORAGE_LOCATION_PREFERENCE = "DEFAULT_STORAGE_LOCATION_PREFERENCE"
    const val DEFAULT_STORAGE_LOCATION_DIRECTORY = "DEFAULT_STORAGE_LOCATION_DIRECTORY"
    const val FILE_NAME_FORMAT = "FILE_NAME_FORMAT"
    const val PHOTO_NAME_FORMAT = "PHOTO_NAME_FORMAT"
    // GeoNav
    private const val GEONAV_PREFIX = "com.fieldbook.tracker.geonav."
    const val GEONAV_AUTO = GEONAV_PREFIX + "GEONAV_AUTO"
    const val GEONAV_COMPASS = GEONAV_PREFIX + "GEONAV_COMPASS"
    const val GEONAV_CONFIG_AUDIO_ON_DROP = GEONAV_PREFIX + "AUDIO_ON_DROP"
    const val GEONAV_CONFIG_DEGREE_PRECISION = GEONAV_PREFIX + "DEGREE_PRECISION"
    const val GEONAV_POPUP_DISPLAY = GEONAV_PREFIX + "POPUP_DISPLAY"
    // GNSS
    const val GNSS_LAST_PAIRED_DEVICE_NAME = "GNSS_LAST_PAIRED_DEVICE_NAME"
    const val GNSS_LAST_CHOSEN_PRECISION = "GNSS_LAST_CHOSEN_PRECISION"
    const val GNSS_WARNED_PRECISION = "GNSS_WARNED_PRECISION"
    const val GNSS_PRECISION_OK_SOUND = "GNSS_PRECISION_OK_SOUND"
    // Canon
    const val CANON_SSID_NAME = "com.tracker.fieldbook.preferences.keys.canon.ssid_name"
    const val CANON_IP = "com.tracker.fieldbook.preferences.keys.canon.ip"
    const val CANON_PORT = "com.tracker.fieldbook.preferences.keys.canon.port"
    const val CANON_DEBUG = "com.tracker.fieldbook.preferences.keys.canon.debug"
    const val CANON_HELP = "com.tracker.fieldbook.preferences.keys.canon.help"
    const val CANON_CAMERA_PREVIEW = "com.fieldbook.tracker.traits.camera.canon.PREVIEW"
    const val LAST_TIME_OPENED = "LastTimeAppOpened"
    const val ASKED_SINCE_OPENED = "AskedSinceAppOpened"
    const val VERIFY_USER = "VerifyUserEvery24Hours"
    const val TRAITS_EXPORTED = "TraitsExported"
    const val ALL_TRAITS_VISIBLE = "allTraitsVisible"
    const val LAST_USED_TRAIT = "com.fieldbook.tracker.LAST_USED_TRAIT"
    const val LAST_USED_RESOURCE_FILE = "com.fieldbook.tracker.LAST_USED_RESOURCE_FILE"
    // themes
    const val SAVED_DATA_COLOR = "SAVED_DATA_COLOR"
    const val THEME_FLAG = "key_preferences_theme_theme_flag"
    const val FIRST_RUN = "FirstRun"
    const val UPDATE_VERSION = "UpdateVersion"
    const val REGION = "region"
    const val TIPS_CONFIGURED = "TipsConfigured"
    // shared preferences file name
    const val SHARED_PREF_FILE_NAME = "Settings"
    // used to get the name of the currently selected field that is saved in preferences
    const val FIELD_FILE = "FieldFile"
    // field alias is currently used from imported brapi studies, it is the study db id
    const val FIELD_ALIAS = "FieldAlias"
    // key that saves where the field source is from local, brapi, etc.
    const val FIELD_SOURCE = "ImportExpSource"
    // used to get the current study table's integer primary key
    const val SELECTED_FIELD_ID = "SelectedFieldExpId"
    // next three are used to save/get the currently selected field's unique, primary and secondary keys
    const val UNIQUE_NAME = "ImportUniqueName"
    const val PRIMARY_NAME = "ImportFirstName"
    const val SECONDARY_NAME = "ImportSecondName"
    // Used to get name of observation level of currently selected field
    const val FIELD_OBS_LEVEL = "FieldObsLevel"
    // flag saved in preferences to determine if import is completed
    const val IMPORT_FIELD_FINISHED = "ImportFieldFinished"
    // preference key to save the last plot during collect activity
    const val LAST_PLOT = "lastplot"
    // collect search activity defaults
    const val SEARCH_COLUMN_DEFAULT = "SEARCH_COLUMN_DEFAULT"
    const val SEARCH_LIKE_DEFAULT = "SEARCH_LIKE_DEFAULT"
    const val DATA_LOCK_STATE = "DataLockState"
    // export flags
    const val EXPORT_COLUMNS_UNIQUE = "EXPORT_COLUMNS_UNIQUE"
    const val EXPORT_COLUMNS_ALL = "EXPORT_COLUMNS_ALL"
    const val EXPORT_TRAITS_ALL = "EXPORT_TRAITS_ALL"
    const val EXPORT_TRAITS_ACTIVE = "EXPORT_TRAITS_ACTIVE"
    const val EXPORT_FORMAT_TABLE = "EXPORT_FORMAT_TABLE"
    const val EXPORT_FORMAT_DATABASE = "EXPORT_FORMAT_DATABASE"
    const val EXPORT_OVERWRITE = "Overwrite"
    // Field detail
    const val FIELD_DETAIL_OVERVIEW_COLLAPSED = "FIELD_DETAIL_OVERVIEW_COLLAPSED"
    const val FIELD_DETAIL_DATA_COLLAPSED = "FIELD_DETAIL_DATA_COLLAPSED"
    // Data grid
    const val DATAGRID_PREFIX_TRAIT = "com.fieldbook.tracker.datagrid.DATAGRID_PREFIX_TRAIT"
    // summary filter
    const val SUMMARY_FILTER_ATTRIBUTES = "com.fieldbook.tracker.summary.SUMMARY_FILTER_ATTRIBUTES"
    // Calendar Trait
    const val CALENDAR_LAST_SAVED_DATE = "com.fieldbook.tracker.CALENDAR_LAST_SAVED_DATE"
}

