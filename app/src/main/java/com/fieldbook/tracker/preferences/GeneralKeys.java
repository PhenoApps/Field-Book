package com.fieldbook.tracker.preferences;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    public static final String DEVICE_NAME = "DeviceName";

    // Appearance
    public static final String INFOBAR_UPDATE = "INFOBAR_UPDATE";
    public static final String APPLICATION_THEME                    = "APPLICATION_THEME";
    public static final String RESTORE_DEFAULT_THEME                = "RESTORE_DEFAULT_THEME";

    // General
    public static final String TUTORIAL_MODE                        = "Tips";
    public static final String NEXT_ENTRY_NO_DATA                   = "NextEmptyPlot";
    public static final String HIDE_ENTRIES_WITH_DATA               = "com.fieldbook.tracker.HIDE_ENTRIES";
    public static final String ATTR_CHOOSER_DIALOG_TAB              = "ATTR_CHOOSER_DIALOG_TAB";

    // Files and Naming
    public static final String DEFAULT_STORAGE_LOCATION_PREFERENCE  = "DEFAULT_STORAGE_LOCATION_PREFERENCE";

    //TODO check usages of default storage dir
    public static final String DEFAULT_STORAGE_LOCATION_DIRECTORY   = "DEFAULT_STORAGE_LOCATION_DIRECTORY";
    public static final String FILE_NAME_FORMAT                     = "FILE_NAME_FORMAT";
    public static final String PHOTO_NAME_FORMAT                    = "PHOTO_NAME_FORMAT";

    //GeoNav
    private static final String GEONAV_PREFIX                       = "com.fieldbook.tracker.geonav.";
    public static final String GEONAV_AUTO                          = GEONAV_PREFIX + "GEONAV_AUTO";
    public static final String GEONAV_COMPASS                       = GEONAV_PREFIX + "GEONAV_COMPASS";

    // GeoNav Configuration Preferences
    public static final String GEONAV_CONFIG_AUDIO_ON_DROP          = GEONAV_PREFIX + "AUDIO_ON_DROP";

    public static final String GEONAV_CONFIG_DEGREE_PRECISION       = GEONAV_PREFIX + "DEGREE_PRECISION";
  
    public static final String GEONAV_POPUP_DISPLAY                 = GEONAV_PREFIX + "POPUP_DISPLAY";

    public static final String GEONAV_POPUP_TRAIT                   = GEONAV_PREFIX + "POPUP_TRAIT";
    // @formatter:on

    // GNSS
    public static final String GNSS_LAST_PAIRED_DEVICE_NAME      = "GNSS_LAST_PAIRED_DEVICE_NAME";

    public static final String GNSS_LAST_CHOSEN_PRECISION        = "GNSS_LAST_CHOSEN_PRECISION";

    public static final String GNSS_WARNED_PRECISION             = "GNSS_WARNED_PRECISION";

    public static final String GNSS_PRECISION_OK_SOUND           = "GNSS_PRECISION_OK_SOUND";

    // Canon
    public static final String CANON_SSID_NAME = "com.tracker.fieldbook.preferences.keys.canon.ssid_name";

    public static final String CANON_IP = "com.tracker.fieldbook.preferences.keys.canon.ip";

    public static final String CANON_PORT = "com.tracker.fieldbook.preferences.keys.canon.port";

    public static final String CANON_DEBUG = "com.tracker.fieldbook.preferences.keys.canon.debug";
    public static final String CANON_HELP = "com.tracker.fieldbook.preferences.keys.canon.help";
    public static final String CANON_CAMERA_PREVIEW = "com.fieldbook.tracker.traits.camera.canon.PREVIEW";

    public static final String LAST_TIME_OPENED = "LastTimeAppOpened";
    public static final String ASKED_SINCE_OPENED = "AskedSinceAppOpened";
    public static final String VERIFY_USER = "VerifyUserEvery24Hours";

    public static final String TRAITS_EXPORTED = "TraitsExported";
    public static final String ALL_TRAITS_VISIBLE = "allTraitsVisible";
    public static final String LAST_USED_TRAIT = "com.fieldbook.tracker.LAST_USED_TRAIT";
    public static final String TRAIT_TYPE_WORD_WRAP = "com.fieldbook.tracker.TRAIT_TYPE_WORD_WRAP";
    public static final String LAST_USED_RESOURCE_FILE = "com.fieldbook.tracker.LAST_USED_RESOURCE_FILE";

    //themes
    public static final String SAVED_DATA_COLOR = "SAVED_DATA_COLOR";
    public static final String THEME_FLAG = "key_preferences_theme_theme_flag";

    public static final String FIRST_RUN = "FirstRun";
    public static final String UPDATE_VERSION = "UpdateVersion";
    public static final String REGION = "region";
    public static final String TIPS_CONFIGURED = "TipsConfigured";

    //shared preferences file name
    public static final String SHARED_PREF_FILE_NAME = "Settings";

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
    public static final String EXPORT_FORMAT_DATABASE = "EXPORT_FORMAT_DATABASE";
    public static final String EXPORT_OVERWRITE = "Overwrite";

    // Field detail
    public static final String FIELD_DETAIL_OVERVIEW_COLLAPSED = "FIELD_DETAIL_OVERVIEW_COLLAPSED";
    public static final String FIELD_DETAIL_DATA_COLLAPSED = "FIELD_DETAIL_DATA_COLLAPSED";
    public static final String FIELD_DETAIL_FIELD_ID = "FIELD_DETAIL_FIELD_ID";

    //Data grid
    public static final String DATAGRID_PREFIX_TRAIT = "com.fieldbook.tracker.datagrid.DATAGRID_PREFIX_TRAIT";

    //summary filter
    public static final String SUMMARY_FILTER_ATTRIBUTES = "com.fieldbook.tracker.summary.SUMMARY_FILTER_ATTRIBUTES";
    public static final String SUMMARY_FILTER_TRAITS = "com.fieldbook.tracker.summary.SUMMARY_FILTER_TRAITS";

    //Calendar Trait
    public static final String CALENDAR_LAST_SAVED_DATE = "com.fieldbook.tracker.CALENDAR_LAST_SAVED_DATE";

    //Zebra Label Print Trait
    public static final String LABEL_PRINT_DEVICE_NAME      = "LABEL_PRINT_DEVICE_NAME";

    //Dialog Export
    public static final String DIALOG_EXPORT_BUNDLE_CHECKED = "com.fieldbook.tracker.DIALOG_EXPORT_BUNDLE_CHECKED";

    //storage definer / migrator
    public static final String FIRST_MIGRATE = "FIRST_MIGRATE";

    //camera traits
    public static final String CAMERA_SYSTEM_PREVIEW = "com.fieldbook.tracker.traits.camera.SYSTEM_PREVIEW";

    public static final String CAMERA_SYSTEM = "com.fieldbook.tracker.traits.camera.SYSTEM";

    public static final String CAMERA_RESOLUTION = "com.fieldbook.tracker.traits.camera.RESOLUTION";

    public static final String USB_CAMERA_PREVIEW = "com.fieldbook.tracker.traits.camera.usb.PREVIEW";

    public static final String USB_CAMERA_RESOLUTION_INDEX = "com.fieldbook.tracker.traits.camera.USB_RESOLUTION_INDEX";

    public static final String USB_CAMERA_AUTO_WHITE_BALANCE = "com.fieldbook.tracker.traits.camera.usb.AUTO_WHITE_BALANCE";

    public static final String USB_CAMERA_AUTO_FOCUS = "com.fieldbook.tracker.traits.camera.usb.AUTO_FOCUS";

    @NotNull
    public static final Object SORT_ORDER = "com.fieldbook.tracker.field_sort_order";
    @Nullable
    public static final String LIST_FILTER_TEXTS = ".list_filter_texts";

    @NotNull
    public static final String FIELDS_LIST_SORT_ORDER = "com.fieldbook.tracker.fields_list_sort_order";

    @NotNull
    public static final String TRAITS_LIST_SORT_ORDER = "com.fieldbook.tracker.traits_list_sort_order";

    // stores the state of whether field grouping functionality is enabled/disabled
    public static final String FIELD_GROUPING_ENABLED = "com.fieldbook.tracker.field_grouping_enabled";

    // field grouping can be toggled by the user, or can be forced to toggle programmatically
    // this key tracks if the user has toggled the grouping
    public static final String USER_TOGGLED_FIELD_GROUPING = "com.fieldbook.tracker.user_toggled_field_grouping";

    // simply store the state of whether the "Ungrouped" fields is expanded or not
    public static final String UNGROUPED_FIELDS_EXPANDED = "com.fieldbook.tracker.ungrouped_fields_expanded";
    // app intro
    public static final String LOAD_SAMPLE_DATA = "com.fieldbook.tracker.load_sample_data";

    public static final String HIGH_CONTRAST_THEME_ENABLED = "com.fieldbook.tracker.optional_setup_theme";

    @Nullable
    public static final String FROM_INTRO_AUTOMATIC = "com.fieldbook.tracker.activities.intro.from_intro_automatic";

    @NotNull
    public static final String RESET_PREFERENCES = "RESET_PREFERENCES";

    /**
     * Function that returns the key for the crop coordinates of a trait
     * @param traitId --the internal db id of the trait
     * @return key used in preferences to obtain the (tl, tr, br, bl) coordinates used for cropping images
     */
    @NotNull
    public static String getCropCoordinatesKey(int traitId) {
        return "com.fieldbook.tracker.crop_coordinates." + traitId;
    }

    /**
     * InnoSpectra Nano
     */

    @NonNull
    public static final String INNOSPECTRA_NANO_CONFIG_INDEX = "com.fieldbook.tracker.traits.innospectra_nano.INDEX";

    @NonNull
    public static final String NIX_NAME = "com.fieldbook.tracker.traits.nix.NAME";

    @NonNull
    public static final String NIX_ADDRESS = "com.fieldbook.tracker.traits.nix.ADDRESS";

    @NonNull
    public static final String SPECTRAL_MODE = "com.fieldbook.tracker.traits.spectral.MODE";

    @NonNull
    public static final String SCALE_ADDRESS = "com.fieldbook.tracker.traits.scale.ADDRESS";

    @NonNull
    public static final String GREEN_SEEKER_ADDRESS = "com.fieldbook.tracker.traits.greenseeker.ADDRESS";

    @NotNull
    private static String getDropDownAttributeKey(int index) {
        return "DROP" + index;
    }

    @NotNull
    private static String getDropDownTraitKey(int index) {
        return "DROP.TRAIT" + index;
    }

    @NotNull
    public static String getIsInfoBarWordWrapped(int index) {
        return "INFOBAR_WORD_WRAP_" + index;
    }

    @NotNull
    public static DropDownKeyModel getDropDownKeys(int index) {
        return new DropDownKeyModel(
                getDropDownAttributeKey(index),
                getDropDownTraitKey(index)
        );
    }

    private GeneralKeys() {

    }
}