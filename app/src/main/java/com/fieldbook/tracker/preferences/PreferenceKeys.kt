package com.fieldbook.tracker.preferences


/**
 * This class mainly consists of preferences keys that go in Field Book's settings.
 * Moreover, these are the preferences that can be shared with other devices.
 * Only add the preferences that:
 *      - do not need to be setup individually on a new device
 *      - are not used in state management
 * eg. do not add FIRST_NAME, LAST_NAME or Crashlytics related preferences
 *
 * Add all individual keys to the respective key set and/or add to SETTINGS_KEYS
 */
class PreferenceKeys {
    companion object {
        // PROFILE
        const val LIST_OF_PERSON_NAMES = "ListOfPersonNames"
        const val VERIFICATION_INTERVAL = "com.tracker.fieldbook.preference.profile_verification_interval"

        private val profilePreferenceKeys = setOf(VERIFICATION_INTERVAL)

        // FEATURES
        const val TIPS = "Tips"
        const val DATAGRID_SETTING = "DataGrid"
        const val HIDE_ENTRIES_WITH_DATA_TOOLBAR = "com.fieldbook.tracker.HIDE_ENTRIES_WITH_DATA_TOOLBAR"
        const val MOVE_TO_UNIQUE_ID = "com.fieldbook.tracker.MOVE_TO_UNIQUE_ID"

        private val featurePreferenceKeys = setOf(TIPS, DATAGRID_SETTING, HIDE_ENTRIES_WITH_DATA_TOOLBAR, MOVE_TO_UNIQUE_ID)

        // APPEARANCE
        const val TOOLBAR_CUSTOMIZE = "TOOLBAR_CUSTOMIZE"
        const val INFOBAR_NUMBER = "INFOBAR_NUMBER"
        const val HIDE_INFOBAR_PREFIX = "HIDE_INFOBAR_PREFIX"
        const val QUICK_GOTO = "QuickGoTo"
        const val RANGE_PROGRESS_BAR = "RANGE_PROGRESS_BAR"
        const val TRAITS_PROGRESS_BAR = "TRAITS_PROGRESS_BAR"
        const val SHOW_OBSERVATION_TIMESTAMP = "SHOW_OBSERVATION_TIMESTAMP"
        // themes
        const val THEME = "key_preferences_theme_theme"
        const val TEXT_THEME = "key_preferences_theme_text"
        const val SAVED_DATA_COLOR = "SAVED_DATA_COLOR"
        // language
        const val LANGUAGE_PREF = "language"
        const val LANGUAGE_LOCALE_ID = "com.tracker.fieldbook.preference.language.id"
        const val LANGUAGE_LOCALE_SUMMARY = "com.tracker.fieldbook.preference.language.summary"
        const val LANGUAGE_LOCALE_DEFAULT_ID = "com.tracker.fieldbook.preference.language.default_id"

        private val appearancePreferenceKeys = setOf(TOOLBAR_CUSTOMIZE, INFOBAR_NUMBER, HIDE_INFOBAR_PREFIX, QUICK_GOTO, RANGE_PROGRESS_BAR,
            TRAITS_PROGRESS_BAR, SHOW_OBSERVATION_TIMESTAMP, THEME, TEXT_THEME, SAVED_DATA_COLOR, LANGUAGE_PREF, LANGUAGE_LOCALE_ID,
            LANGUAGE_LOCALE_SUMMARY, LANGUAGE_LOCALE_DEFAULT_ID)

        // BEHAVIOR
        const val CYCLING_TRAITS_ADVANCES = "CycleTraits"
        const val RETURN_FIRST_TRAIT = "ReturnFirst"
        const val DISABLE_ENTRY_ARROW_NO_DATA = "DISABLE_ENTRY_ARROW_NO_DATA"
        const val FLIP_FLOP_ARROWS = "FLIP_FLOP_ARROWS"
        const val VOLUME_NAVIGATION = "VOLUME_NAVIGATION"
        const val RETURN_CHARACTER = "RETURN_CHARACTER"
        const val BARCODE_SCANNING_OPTIONS = "com.fieldbook.tracker.BARCODE_SCANNING_OPTIONS"
        const val MEDIA_KEYCODE_NAVIGATION = "com.tracker.fieldbook.preferences.keys.enable_media_keycode_events"

        private val behaviorPreferenceKeys = setOf(CYCLING_TRAITS_ADVANCES, RETURN_FIRST_TRAIT, DISABLE_ENTRY_ARROW_NO_DATA,
            FLIP_FLOP_ARROWS, VOLUME_NAVIGATION, RETURN_CHARACTER, BARCODE_SCANNING_OPTIONS, MEDIA_KEYCODE_NAVIGATION)

        // LOCATION
        private const val GEONAV_PREFIX = "com.fieldbook.tracker.geonav."
        const val PAIR_BLUETOOTH: String = GEONAV_PREFIX + "PAIR_BLUETOOTH"
        const val PAIRED_DEVICE_ADDRESS: String = GEONAV_PREFIX + "PAIRED_DEVICE_ADDRESS"
        const val GENERAL_LOCATION_COLLECTION = "com.fieldbook.tracker.GENERAL_LOCATION_COLLECTION"
        const val COORDINATE_FORMAT = "com.fieldbook.tracker.COORDINATE_FORMAT"
        // GeoNav
        const val ENABLE_GEONAV = GEONAV_PREFIX + "ENABLE_GEONAV"
        const val SEARCH_ANGLE = GEONAV_PREFIX + "parameters.SEARCH_ANGLE"
        const val UPDATE_INTERVAL = GEONAV_PREFIX + "UPDATE_INTERVAL"
        const val GEONAV_AVERAGING = GEONAV_PREFIX + "GEONAV_AVERAGING"
        const val GEONAV_AVERAGING_INTERVAL = GEONAV_PREFIX + "GEONAV_AVERAGIN_INTERVAL"
        const val GEONAV_PARAMETER_D1 = GEONAV_PREFIX + "parameters.trapezoid.D1"
        const val GEONAV_PARAMETER_D2 = GEONAV_PREFIX + "parameters.trapezoid.D2"
        const val GEONAV_SEARCH_METHOD = GEONAV_PREFIX + "SEARCH_METHOD"
        const val GEONAV_DISTANCE_THRESHOLD = GEONAV_PREFIX + "DISTANCE_THRESHOLD"
        const val GEONAV_LOGGING_MODE = GEONAV_PREFIX + "GEONAV_LOGGING_MODE"

        private val locationPreferenceKeys = setOf(PAIR_BLUETOOTH, PAIRED_DEVICE_ADDRESS, GENERAL_LOCATION_COLLECTION, COORDINATE_FORMAT,
            ENABLE_GEONAV, SEARCH_ANGLE, UPDATE_INTERVAL, GEONAV_AVERAGING, GEONAV_AVERAGING_INTERVAL, GEONAV_PARAMETER_D1,
            GEONAV_PARAMETER_D2, GEONAV_SEARCH_METHOD, GEONAV_DISTANCE_THRESHOLD, GEONAV_LOGGING_MODE)

        // Sounds
        const val PRIMARY_SOUND = "RangeSound"
        const val ENTRY_NAVIGATION_SOUND = "ENTRY_NAVIGATION_SOUND"
        const val CYCLE_TRAITS_SOUND = "CYCLE_TRAITS_SOUND"
        const val DELETE_OBSERVATION_SOUND = "DELETE_OBSERVATION_SOUND"
        // tts
        const val TTS_LANGUAGE_ENABLED = "TTS_LANGUAGE_ENABLED"
        const val TTS_LANGUAGE = "TTS_LANGUAGE"
        const val TTS_LANGUAGE_SUMMARY = "TTS_LANGUAGE_SUMMARY"

        private val soundPreferenceKeys = setOf(PRIMARY_SOUND, ENTRY_NAVIGATION_SOUND, CYCLE_TRAITS_SOUND, DELETE_OBSERVATION_SOUND,
            TTS_LANGUAGE_ENABLED, TTS_LANGUAGE, TTS_LANGUAGE_SUMMARY)

        // BrAPI
        const val BRAPI_ENABLED = "BRAPI_ENABLED"
        const val BRAPI_BASE_URL = "BRAPI_BASE_URL"
        const val BRAPI_OIDC_URL = "BRAPI_OIDC_URL"
        const val BRAPI_OIDC_FLOW = "BRAPI_OIDC_FLOW"
        const val BRAPI_OIDC_CLIENT_ID = "BRAPI_OIDC_CLIENT_ID"
        const val BRAPI_OIDC_SCOPE = "BRAPI_OIDC_SCOPE"
        const val BRAPI_EXPLICIT_OIDC_URL = "BRAPI_EXPLICIT_OIDC_URL"
        const val BRAPI_TOKEN = "BRAPI_TOKEN"
        const val BRAPI_ID_TOKEN = "BRAPI_ID_TOKEN"
        const val BRAPI_VERSION = "BRAPI_VERSION"
        const val BRAPI_PAGE_SIZE = "BRAPI_PAGE_SIZE"
        const val BRAPI_TIMEOUT = "BRAPI_TIMEOUT"
        const val BRAPI_CHUNK_SIZE = "BRAPI_CHUNK_SIZE"
        const val BRAPI_DISPLAY_NAME = "BRAPI_DISPLAY_NAME"
        const val BRAPI_INVALIDATE_CACHE_INTERVAL = "BRAPI_CACHE_INVALIDATE_INTERVAL"
        const val BRAPI_INVALIDATE_CACHE_LAST_CLEAR = "BRAPI_INVALIDATE_CACHE_LAST_CLEAR"
        const val BRAPI_MAX_CONCURRENT_IMAGE_CONTENT = "BRAPI_MAX_CONCURRENT_IMAGE_CONTENT"
        const val BRAPI_MAX_CONCURRENT_OBSERVATION_TRANSFER = "BRAPI_MAX_CONCURRENT_IMAGE_CONTENT"
        // Persisted last-checked timestamp text values
        const val BRAPI_LAST_CHECKED_UPLOAD = "BRAPI_LAST_CHECKED_UPLOAD"
        const val BRAPI_LAST_CHECKED_DOWNLOAD = "BRAPI_LAST_CHECKED_DOWNLOAD"

        // categorical trait
        const val LABELVAL_CUSTOMIZE = "LABELVAL_CUSTOMIZE"

        private val brapiPreferenceKeys = setOf(BRAPI_ENABLED, BRAPI_BASE_URL, BRAPI_OIDC_URL, BRAPI_OIDC_FLOW, BRAPI_OIDC_CLIENT_ID,
            BRAPI_OIDC_SCOPE, BRAPI_EXPLICIT_OIDC_URL, BRAPI_TOKEN, BRAPI_VERSION, BRAPI_PAGE_SIZE, BRAPI_TIMEOUT, BRAPI_CHUNK_SIZE,
            BRAPI_DISPLAY_NAME, BRAPI_INVALIDATE_CACHE_INTERVAL, BRAPI_INVALIDATE_CACHE_LAST_CLEAR, LABELVAL_CUSTOMIZE)

        // SYSTEM
        const val IMPORT_SOURCE_DEFAULT = "IMPORT_SOURCE_DEFAULT"
        const val EXPORT_SOURCE_DEFAULT = "EXPORT_SOURCE_DEFAULT"
        const val ENABLE_SHARE = "EnableShare"

        private val systemPreferenceKeys = setOf(IMPORT_SOURCE_DEFAULT, EXPORT_SOURCE_DEFAULT, ENABLE_SHARE)

        // EXPERIMENTAL
        const val REPEATED_VALUES_PREFERENCE_KEY = "com.tracker.fieldbook.preferences.keys.repeated_values"
        const val ENABLE_FIELD_AUDIO = "com.tracker.fieldbook.preferences.keys.enable_field_audio"
        const val EXPERIMENTAL_NEW_BRAPI_UI = "com.tracker.fieldbook.preferences.keys.enable_enhanced_brapi_import"

        private val experimentalPreferenceKeys = setOf(REPEATED_VALUES_PREFERENCE_KEY,
            ENABLE_FIELD_AUDIO, EXPERIMENTAL_NEW_BRAPI_UI)

        val SETTINGS_KEYS = profilePreferenceKeys + featurePreferenceKeys + appearancePreferenceKeys +
                behaviorPreferenceKeys + locationPreferenceKeys + soundPreferenceKeys + brapiPreferenceKeys +
                systemPreferenceKeys + experimentalPreferenceKeys
    }
}