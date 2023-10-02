package com.fieldbook.tracker.preferences;

import org.jetbrains.annotations.NotNull;

public class GeneralKeys {
    // @formatter:off

    //Crashlytics
    public static final String CRASHLYTICS_KEY_USER_TOKEN           = "user_token";

    //Profile
    public static final String CRASHLYTICS_ID_ENABLED               = "com.tracker.fieldbook.preference.crashlytics.user_id_enabled";
    public static final String CRASHLYTICS_ID_REFRESH               = "com.tracker.fieldbook.preference.crashlytics.refresh";
    public static final String CRASHLYTICS_ID                       = "com.tracker.fieldbook.preference.crashlytics.id";
    public static final String FIRST_NAME = "FirstName";
    public static final String LAST_NAME = "LastName";
    public static final String PERSON_UPDATE = "PersonUpdate";
    public static final String MODIFY_PROFILE_SETTINGS = "ModifyProfileSettings";
    public static final String REQUIRE_USER_TO_COLLECT              = "com.tracker.fieldbook.preference.require_user_to_collect";
    public static final String REQUIRE_USER_INTERVAL              = "com.tracker.fieldbook.preference.require_user_interval";

    // Appearance
    public static final String TOOLBAR_CUSTOMIZE                    = "TOOLBAR_CUSTOMIZE";
    public static final String INFOBAR_NUMBER                       = "INFOBAR_NUMBER";
    public static final String APPLICATION_THEME                    = "APPLICATION_THEME";
    public static final String RESTORE_DEFAULT_THEME                = "RESTORE_DEFAULT_THEME";
    public static final String LANGUAGE_LOCALE_ID                   = "com.tracker.fieldbook.preference.language.id";
    public static final String LANGUAGE_LOCALE_SUMMARY              = "com.tracker.fieldbook.preference.language.summary";
    public static final String LANGUAGE_LOCALE_DEFAULT_ID                   = "com.tracker.fieldbook.preference.language.default_id";

    // Behavior
    public static final String RETURN_CHARACTER                     = "RETURN_CHARACTER";
    public static final String VOLUME_NAVIGATION                    = "com.fieldbook.tracker.VOLUME_NAVIGATION";
    public static final String CYCLING_TRAITS_ADVANCES              = "CycleTraits";
    public static final String RETURN_FIRST_TRAIT              = "ReturnFirst";
    public static final String DISABLE_ENTRY_ARROW_NO_DATA          = "DISABLE_ENTRY_ARROW_NO_DATA";

    // General
    public static final String TUTORIAL_MODE                        = "Tips";
    public static final String NEXT_ENTRY_NO_DATA                   = "NextEmptyPlot";
    public static final String QUICK_GOTO                           = "QuickGoTo";
    public static final String MOVE_TO_UNIQUE_ID                    = "com.fieldbook.tracker.MOVE_TO_UNIQUE_ID";
    public static final String DATAGRID_SETTING                     = "DataGrid";
    public static final String HIDE_ENTRIES_WITH_DATA               = "com.fieldbook.tracker.HIDE_ENTRIES";
    public static final String HIDE_ENTRIES_WITH_DATA_TOOLBAR       = "com.fieldbook.tracker.HIDE_ENTRIES_WITH_DATA_TOOLBAR";
    public static final String USE_DAY_OF_YEAR                      = "UseDay";
    public static final String DISABLE_SHARE                        = "DisableShare";
    public static final String GENERAL_LOCATION_COLLECTION          = "com.fieldbook.tracker.GENERAL_LOCATION_COLLECTION";
    public static final String ATTR_CHOOSER_DIALOG_TAB              = "ATTR_CHOOSER_DIALOG_TAB";

    // Files and Naming
    public static final String DEFAULT_STORAGE_LOCATION_PREFERENCE  = "DEFAULT_STORAGE_LOCATION_PREFERENCE";

    //TODO check usages of default storage dir
    public static final String DEFAULT_STORAGE_LOCATION_DIRECTORY   = "DEFAULT_STORAGE_LOCATION_DIRECTORY";
    public static final String FILE_NAME_FORMAT                     = "FILE_NAME_FORMAT";
    public static final String PHOTO_NAME_FORMAT                    = "PHOTO_NAME_FORMAT";

    // Sounds
    public static final String PRIMARY_SOUND                        = "RangeSound";
    public static final String CYCLE_TRAITS_SOUND                   = "CYCLE_TRAITS_SOUND";
    public static final String ENTRY_NAVIGATION_SOUND               = "ENTRY_NAVIGATION_SOUND";

    public static final String DELETE_OBSERVATION_SOUND             = "DELETE_OBSERVATION_SOUND";

    //BrAPI
    public static final String BRAPI_ENABLED                        = "BRAPI_ENABLED";
    public static final String BRAPI_BASE_URL                       = "BRAPI_BASE_URL";
    public static final String BRAPI_OIDC_URL                       = "BRAPI_OIDC_URL";
    public static final String BRAPI_OIDC_FLOW                      = "BRAPI_OIDC_FLOW";
    public static final String BRAPI_EXPLICIT_OIDC_URL              = "BRAPI_EXPLICIT_OIDC_URL";
    public static final String BRAPI_TOKEN                          = "BRAPI_TOKEN";
    public static final String BRAPI_VERSION                        = "BRAPI_VERSION";
    public static final String BRAPI_PAGE_SIZE                      = "BRAPI_PAGE_SIZE";
    public static final String BRAPI_TIMEOUT                        = "BRAPI_TIMEOUT";
    public static final String BRAPI_CHUNK_SIZE                     = "BRAPI_CHUNK_SIZE";

    public static final String BRAPI_DISPLAY_NAME                   = "BRAPI_DISPLAY_NAME";

    //GeoNav
    private static final String GEONAV_PREFIX                       = "com.fieldbook.tracker.geonav.";
    public static final String ENABLE_GEONAV                        = GEONAV_PREFIX + "ENABLE_GEONAV";
    public static final String SEARCH_ANGLE                         = GEONAV_PREFIX + "SEARCH_ANGLE";
    public static final String UPDATE_INTERVAL                      = GEONAV_PREFIX + "UPDATE_INTERVAL";
    public static final String PAIR_BLUETOOTH                       = GEONAV_PREFIX + "PAIR_BLUETOOTH";
    public static final String PAIRED_DEVICE_ADDRESS                = GEONAV_PREFIX + "PAIRED_DEVICE_ADDRESS";
    public static final String GEONAV_AUTO                          = GEONAV_PREFIX + "GEONAV_AUTO";
    public static final String GEONAV_COMPASS                       = GEONAV_PREFIX + "GEONAV_COMPASS";
    public static final String GEONAV_AVERAGING                     = GEONAV_PREFIX + "GEONAV_AVERAGING";
    public static final String GEONAV_AVERAGING_INTERVAL            = GEONAV_PREFIX + "GEONAV_AVERAGIN_INTERVAL";
    public static final String GEONAV_PARAMETERS_CATEGORY           = GEONAV_PREFIX + "parameters.CATEGORY_KEY";
    public static final String GEONAV_PARAMETER_D1                  = GEONAV_PREFIX + "parameters.trapezoid.D1";
    public static final String GEONAV_PARAMETER_D2                  = GEONAV_PREFIX + "parameters.trapezoid.D2";
    public static final String GEONAV_SEARCH_METHOD                 = GEONAV_PREFIX + "SEARCH_METHOD";

    public static final String GEONAV_LOGGING_MODE                 = GEONAV_PREFIX + "GEONAV_LOGGING_MODE";

    // GeoNav Configuration Preferences
    public static final String GEONAV_CONFIG_AUDIO_ON_DROP          = GEONAV_PREFIX + "AUDIO_ON_DROP";

    public static final String GEONAV_CONFIG_DEGREE_PRECISION       = GEONAV_PREFIX + "DEGREE_PRECISION";
  
    public static final String GEONAV_POPUP_DISPLAY       = GEONAV_PREFIX + "POPUP_DISPLAY";
    // @formatter:on

    // GNSS
    public static final String GNSS_LAST_PAIRED_DEVICE_NAME      = "GNSS_LAST_PAIRED_DEVICE_NAME";

    public static final String GNSS_LAST_CHOSEN_PRECISION        = "GNSS_LAST_CHOSEN_PRECISION";

    public static final String GNSS_WARNED_PRECISION             = "GNSS_WARNED_PRECISION";

    public static final String GNSS_PRECISION_OK_SOUND           = "GNSS_PRECISION_OK_SOUND";

    //Beta feature keys
    public static final String REPEATED_VALUES_PREFERENCE_KEY = "com.tracker.fieldbook.preferences.keys.repeated_values";

    public static final String LAST_TIME_OPENED = "LastTimeAppOpened";
    public static final String ASKED_SINCE_OPENED = "AskedSinceAppOpened";
    public static final String VERIFY_USER = "VerifyUserEvery24Hours";

    public static final String FLIP_FLOP_ARROWS = "FLIP_FLOP_ARROWS";

    public static final String CREATE_TRAIT_FINISHED = "CreateTraitFinished";
    public static final String TRAITS_EXPORTED = "TraitsExported";
    public static final String ALL_TRAITS_VISIBLE = "allTraitsVisible";
    public static final String LAST_USED_TRAIT = "com.fieldbook.tracker.LAST_USED_TRAIT";

    //themes
    public static final String SAVED_DATA_COLOR = "SAVED_DATA_COLOR";
    public static final String THEME = "key_preferences_theme_theme";
    public static final String TEXT_THEME = "key_preferences_theme_text";
    public static final String THEME_FLAG = "key_preferences_theme_theme_flag";
    public static final String IMPORT_SOURCE_DEFAULT = "IMPORT_SOURCE_DEFAULT";
    public static final String EXPORT_SOURCE_DEFAULT = "EXPORT_SOURCE_DEFAULT";
    public static final String FIRST_RUN = "FirstRun";
    public static final String UPDATE_VERSION = "UpdateVersion";
    public static final String REGION = "region";
    public static final String TIPS = "Tips";
    public static final String TIPS_CONFIGURED = "TipsConfigured";

    //shared preferences file name
    public static final String SHARED_PREF_FILE_NAME = "Settings";
    public static final String LANGUAGE_PREF = "language";

    //used to get the name of the currently selected field that is saved in preferences
    //example: field_sample how to get: sharedPreferences.getString(Constants.FIELD_FILE, "")
    public static final String FIELD_FILE = "FieldFile";

    //field alias is currently used from imported brapi studies, it is the study db id
    public static final String FIELD_ALIAS = "FieldAlias";

    //key that saves where the field source is from local, brapi, etc.
    public static final String FIELD_SOURCE = "ImportExpSource";

    //used to get the current study table's integer primary key
    public static final String SELECTED_FIELD_ID = "SelectedFieldExpId";

    //next three are used to save/get the currently selected field's unique, primary and secondary keys
    public static final String UNIQUE_NAME = "ImportUniqueName";
    public static final String PRIMARY_NAME = "ImportFirstName";
    public static final String SECONDARY_NAME = "ImportSecondName";

    //Used to get name of observation level of currently selected field
    public static final String FIELD_OBS_LEVEL = "FieldObsLevel";

    //flag saved in preferences to determine if import is completed
    public static final String IMPORT_FIELD_FINISHED = "ImportFieldFinished";

    //preference key to save the last plot during collect activity
    public static final String LAST_PLOT = "lastplot";

    // collect search activity defaults
    public static final String SEARCH_COLUMN_DEFAULT = "SEARCH_COLUMN_DEFAULT";
    public static final String SEARCH_LIKE_DEFAULT = "SEARCH_LIKE_DEFAULT";

    public static final String DATA_LOCK_STATE = "DataLockState";

    //export flags
    public static final String EXPORT_COLUMNS_UNIQUE = "EXPORT_COLUMNS_UNIQUE";
    public static final String EXPORT_COLUMNS_ALL = "EXPORT_COLUMNS_ALL";
    public static final String EXPORT_TRAITS_ALL = "EXPORT_TRAITS_ALL";
    public static final String EXPORT_TRAITS_ACTIVE = "EXPORT_TRAITS_ACTIVE";
    public static final String EXPORT_FORMAT_TABLE = "EXPORT_FORMAT_TABLE";
    public static final String EXPORT_FORMAT_DATABSE = "EXPORT_FORMAT_DATABASE";
    public static final String EXPORT_OVERWRITE = "Overwrite";

    //Data grid
    public static final String DATAGRID_PREFIX_TRAIT = "com.fieldbook.tracker.datagrid.DATAGRID_PREFIX_TRAIT";

    //summary filter
    public static final String SUMMARY_FILTER_ATTRIBUTES = "com.fieldbook.tracker.summary.SUMMARY_FILTER_ATTRIBUTES";

    //Calendar Trait
    public static final String CALENDAR_LAST_SAVED_DATE = "com.fieldbook.tracker.CALENDAR_LAST_SAVED_DATE";

    //Zebra Label Print Trait
    public static final String LABEL_PRINT_DEVICE_NAME      = "LABEL_PRINT_DEVICE_NAME";

    //Dialog Export
    public static final String DIALOG_EXPORT_BUNDLE_CHECKED = "com.fieldbook.tracker.DIALOG_EXPORT_BUNDLE_CHECKED";

    //storage definer / migrator
    public static final String FIRST_MIGRATE = "FIRST_MIGRATE";

    //categorical trait
    public static final String LABELVAL_CUSTOMIZE = "LABELVAL_CUSTOMIZE";

    //tts
    public static final String TTS_LANGUAGE_ENABLED = "TTS_LANGUAGE_ENABLED";
    public static final String TTS_LANGUAGE = "TTS_LANGUAGE";
    public static final String TTS_LANGUAGE_SUMMARY = "TTS_LANGUAGE_SUMMARY";

    @NotNull
    public static final Object SORT_ORDER = "com.fieldbook.tracker.field_sort_order";

    private GeneralKeys() {

    }
}