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

    // Profile
    public static final String PROFILE_NAME_FIRST                   = "FirstName";
    public static final String PROFILE_NAME_LAST                    = "LastName";
    public static final String PROFILE_LOCATION                     = "Location";

    // Behavior
    public static final String RETURN_CHARACTER                     = "RETURN_CHARACTER";
    public static final String VOLUME_NAVIGATION                    = "VOLUME_NAVIGATION";
    public static final String CYCLING_TRAITS_ADVANCES              = "CycleTraits";
    public static final String DISABLE_ENTRY_ARROW_NO_DATA          = "DISABLE_ENTRY_ARROW_NO_DATA";


    // General
    public static final String TUTORIAL_MODE                        = "Tips";
    public static final String NEXT_ENTRY_NO_DATA                   = "NextEmptyPlot";
    public static final String QUICK_GOTO                           = "QuickGoTo";
    public static final String UNIQUE_CAMERA                        = "BarcodeScan";
    public static final String UNIQUE_TEXT                          = "JumpToPlot";
    public static final String DATAGRID_SETTING                     = "DataGrid";
    public static final String HIDE_ENTRIES_WITH_DATA               = "IgnoreExisting";
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
    public static final String BRAPI_TOKEN                          = "BRAPI_TOKEN";
    public static final String BRAPI_VERSION                        = "BRAPI_VERSION";
    public static final String BRAPI_PAGE_SIZE                      = "BRAPI_PAGE_SIZE";
    // @formatter:on

    private GeneralKeys() {

    }
}
