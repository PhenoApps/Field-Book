package com.fieldbook.tracker.preferences


/**
 * This class mainly consists of preferences keys that go in Field Book's settings.
 * Moreover, these are the preferences that can be shared with other devices.
 * Only add the preferences that:
 *      - do not need to be setup individually on a new device
 *      - are not used in state management
 * eg. do not add FIRST_NAME, LAST_NAME or Crashlytics related preferences
 */
class PreferenceKeys {
    companion object {
        // PROFILE
        const val VERIFICATION_INTERVAL = "com.tracker.fieldbook.preference.profile_verification_interval"

        // FEATURES
        const val TIPS = "Tips"
        const val DATAGRID_SETTING = "DataGrid"
        const val HIDE_ENTRIES_WITH_DATA_TOOLBAR = "com.fieldbook.tracker.HIDE_ENTRIES_WITH_DATA_TOOLBAR"
        const val MOVE_TO_UNIQUE_ID = "com.fieldbook.tracker.MOVE_TO_UNIQUE_ID"

        // APPEARANCE
        const val TOOLBAR_CUSTOMIZE = "TOOLBAR_CUSTOMIZE"
        const val INFOBAR_NUMBER = "INFOBAR_NUMBER"
        const val HIDE_INFOBAR_PREFIX = "HIDE_INFOBAR_PREFIX"
        const val QUICK_GOTO = "QuickGoTo"
        // themes
        const val THEME = "key_preferences_theme_theme"
        const val TEXT_THEME = "key_preferences_theme_text"
        const val SAVED_DATA_COLOR = "SAVED_DATA_COLOR"
        // language
        const val LANGUAGE_PREF = "language"
        const val LANGUAGE_LOCALE_ID = "com.tracker.fieldbook.preference.language.id"
        const val LANGUAGE_LOCALE_SUMMARY = "com.tracker.fieldbook.preference.language.summary"
        const val LANGUAGE_LOCALE_DEFAULT_ID = "com.tracker.fieldbook.preference.language.default_id"

        // SYSTEM
        const val IMPORT_SOURCE_DEFAULT = "IMPORT_SOURCE_DEFAULT"
        const val EXPORT_SOURCE_DEFAULT = "EXPORT_SOURCE_DEFAULT"
        const val ENABLE_SHARE = "EnableShare"
        // TODO: check if crashlytics related preferences need to go here
    }
}