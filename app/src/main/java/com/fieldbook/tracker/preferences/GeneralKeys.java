package com.fieldbook.tracker.preferences;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;

public class GeneralKeys {
    // @formatter:off

    // Appearance
    public static final String TOOLBAR_CUSTOMIZE                    = "TOOLBAR_CUSTOMIZE";
    public static final String LANGUAGE                             = "language";
    public static final String INFOBAR_NUMBER                       = "INFOBAR_NUMBER";
    public static final String APPLICATION_THEME                    = "APPLICATION_THEME";
    public static final String SAVED_DATA_COLOR                     = "SAVED_DATA_COLOR";
    public static final String RESTORE_DEFAULT_THEME                = "RESTORE_DEFAULT_THEME";
    public static final String LABELVAL_CUSTOMIZE                   = "LABELVAL_CUSTOMIZE";

    // Profile
    public static final String PROFILE_NAME_FIRST                   = "FirstName";
    public static final String PROFILE_NAME_LAST                    = "LastName";
    public static final String PROFILE_LOCATION                     = "Location";

    // Behavior
    public static final String RETURN_CHARACTER                     = "RETURN_CHARACTER";
    public static final String VOLUME_NAVIGATION                    = "VOLUME_NAVIGATION";
    public static final String CYCLING_TRAITS_ADVANCES              = "CycleTraits";
    public static final String DISABLE_ENTRY_ARROW_NO_DATA          = "DISABLE_ENTRY_ARROW_NO_DATA";
    public static final String FLIP_FLOP_COLLECT_ARROWS             = "FLIP_FLOP_TRAIT_ARROWS";

    // General
    public static final String TUTORIAL_MODE                        = "Tips";
    public static final String NEXT_ENTRY_NO_DATA                   = "NextEmptyPlot";
    public static final String QUICK_GOTO                           = "QuickGoTo";
    public static final String UNIQUE_CAMERA                        = "BarcodeScan";
    public static final String UNIQUE_TEXT                          = "JumpToPlot";
    public static final String DATAGRID_SETTING                     = "DataGrid";
    public static final String HIDE_ENTRIES_WITH_DATA               = "com.fieldbook.tracker.HIDE_ENTRIES";
    public static final String USE_DAY_OF_YEAR                      = "UseDay";
    public static final String DISABLE_SHARE                        = "DisableShare";

    // Files and Naming
    public static final String DEFAULT_STORAGE_LOCATION_PREFERENCE  = "DEFAULT_STORAGE_LOCATION_PREFERENCE";
    public static final String DEFAULT_STORAGE_LOCATION_DIRECTORY   = "DEFAULT_STORAGE_LOCATION_DIRECTORY";
    public static final String DEFAULT_STORAGE_LOCATION_URI         = "DEFAULT_STORAGE_LOCATION_URI";
    public static final String FILE_NAME_FORMAT                     = "FILE_NAME_FORMAT";
    public static final String PHOTO_NAME_FORMAT                    = "PHOTO_NAME_FORMAT";

    // Sounds
    public static final String PRIMARY_SOUND                        = "RangeSound";
    public static final String CYCLE_TRAITS_SOUND                   = "CYCLE_TRAITS_SOUND";
    public static final String ENTRY_NAVIGATION_SOUND               = "ENTRY_NAVIGATION_SOUND";

    //BrAPI
    public static final String BRAPI_BASE_URL                       = "BRAPI_BASE_URL";
    public static final String BRAPI_OIDC_URL                       = "BRAPI_OIDC_URL";
    public static final String BRAPI_OIDC_FLOW                      = "BRAPI_OIDC_FLOW";
    public static final String BRAPI_TOKEN                          = "BRAPI_TOKEN";
    public static final String BRAPI_VERSION                        = "BRAPI_VERSION";
    public static final String BRAPI_PAGE_SIZE                      = "BRAPI_PAGE_SIZE";
    public static final String BRAPI_TIMEOUT                        = "BRAPI_TIMEOUT";

    //GeoNav
    private static final String GEONAV_PREFIX                       = "com.fieldbook.tracker.geonav.";
    public static final String ENABLE_GEONAV                        = GEONAV_PREFIX + "ENABLE_GEONAV";
    public static final String SEARCH_ANGLE                         = GEONAV_PREFIX + "SEARCH_ANGLE";
    public static final String UPDATE_INTERVAL                      = GEONAV_PREFIX + "UPDATE_INTERVAL";
    public static final String PAIR_BLUETOOTH                       = GEONAV_PREFIX + "PAIR_BLUETOOTH";
    public static final String PAIRED_DEVICE_ADDRESS                = GEONAV_PREFIX + "PAIRED_DEVICE_ADDRESS";
    public static final String GEONAV_AUTO                          = GEONAV_PREFIX + "GEONAV_AUTO";
    public static final String GEONAV_COMPASS                       = GEONAV_PREFIX + "GEONAV_COMPASS";
    public static final String GEONAV_LOG                           = GEONAV_PREFIX + "GEONAV_LOG";
    public static final String GEONAV_AVERAGING                     = GEONAV_PREFIX + "GEONAV_AVERAGING";
    public static final String GEONAV_AVERAGING_INTERVAL            = GEONAV_PREFIX + "GEONAV_AVERAGIN_INTERVAL";
    public static final String GEONAV_PARAMETERS_CATEGORY           = GEONAV_PREFIX + "parameters.CATEGORY_KEY";
    public static final String GEONAV_PARAMETER_D1                  = GEONAV_PREFIX + "parameters.trapezoid.D1";
    public static final String GEONAV_PARAMETER_D2                  = GEONAV_PREFIX + "parameters.trapezoid.D2";
    public static final String GEONAV_SEARCH_METHOD                 = GEONAV_PREFIX + "SEARCH_METHOD";
    // @formatter:on

    //Calendar Trait
    public static final String CALENDAR_LAST_SAVED_DATE             = "com.fieldbook.tracker.CALENDAR_LAST_SAVED_DATE";

    //Dialog Export
    public static final String DIALOG_EXPORT_BUNDLE_CHECKED         = "com.fieldbook.tracker.DIALOG_EXPORT_BUNDLE_CHECKED";

    //Data grid
    public static final String DATAGRID_PREFIX_TRAIT                = "com.fieldbook.tracker.datagrid.DATAGRID_PREFIX_TRAIT";

    //Collect Activity
    public static final String LAST_USED_TRAIT                      = "com.fieldbook.tracker.LAST_USED_TRAIT";

    private GeneralKeys() {

    }
}
