package com.fieldbook.tracker.utilities

import android.content.SharedPreferences
import android.view.KeyEvent
import com.fieldbook.tracker.interfaces.CollectController
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.traits.formats.Formats
import com.fieldbook.tracker.views.TraitBoxView

class MediaKeyCodeActionHelper {

    enum class VolumeNavigation {
        DISABLED,
        TRAIT_NAVIGATION,
        RANGE_NAVIGATION
    }

    companion object {

        fun dispatchKeyEvent(collector: CollectController, event: KeyEvent): Boolean {

            val prefs = collector.getPreferences()

            //bugfix for compatibility with old preference
            try {
                prefs.getString(GeneralKeys.VOLUME_NAVIGATION, VolumeNavigation.DISABLED.ordinal.toString())
            } catch (e: ClassCastException) {
                prefs.edit().remove(GeneralKeys.VOLUME_NAVIGATION).apply()
            }

            val volumeNavEnabled = when(prefs.getString(GeneralKeys.VOLUME_NAVIGATION, VolumeNavigation.DISABLED.ordinal.toString())) {
                VolumeNavigation.DISABLED.ordinal.toString() -> VolumeNavigation.DISABLED
                VolumeNavigation.TRAIT_NAVIGATION.ordinal.toString() -> VolumeNavigation.TRAIT_NAVIGATION
                VolumeNavigation.RANGE_NAVIGATION.ordinal.toString() -> VolumeNavigation.RANGE_NAVIGATION
                else -> VolumeNavigation.DISABLED
            }

            val mediaControlEnabled = prefs.getBoolean(GeneralKeys.MEDIA_KEYCODE_NAVIGATION, false)

            //return early if settings are disabled
            if (volumeNavEnabled == VolumeNavigation.DISABLED && !mediaControlEnabled) {
                return false
            }

            if (event.action == KeyEvent.ACTION_DOWN) {
                return true
            }

            return when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    if (mediaControlEnabled) {
                        collector.takePicture()
                    }
                    true
                }
                KeyEvent.KEYCODE_MEDIA_NEXT -> {
                    when (volumeNavEnabled) {
                        VolumeNavigation.DISABLED -> {
                            if (mediaControlEnabled) {
                                collector.getTraitBox().moveTrait("right")
                            }
                        }
                        VolumeNavigation.TRAIT_NAVIGATION-> collector.getRangeBox().moveEntryRight()
                        VolumeNavigation.RANGE_NAVIGATION -> collector.getTraitBox().moveTrait("right")
                    }
                    true
                }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    when (volumeNavEnabled) {
                        VolumeNavigation.DISABLED -> {
                            if (mediaControlEnabled) {
                                collector.getTraitBox().moveTrait("left")
                            }
                        }
                        VolumeNavigation.TRAIT_NAVIGATION-> collector.getRangeBox().moveEntryLeft()
                        VolumeNavigation.RANGE_NAVIGATION -> collector.getTraitBox().moveTrait("left")
                    }
                    true
                }
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    when (volumeNavEnabled) {
                        VolumeNavigation.DISABLED -> {
                            if (mediaControlEnabled) {
                                collector.getRangeBox().moveEntryRight()
                            }
                        }
                        VolumeNavigation.TRAIT_NAVIGATION-> collector.getTraitBox().moveTrait("right")
                        VolumeNavigation.RANGE_NAVIGATION -> collector.getRangeBox().moveEntryRight()
                    }
                    true
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    when (volumeNavEnabled) {
                        VolumeNavigation.DISABLED -> {
                            if (mediaControlEnabled) {
                                collector.getRangeBox().moveEntryLeft()
                            }
                        }
                        VolumeNavigation.TRAIT_NAVIGATION -> collector.getTraitBox().moveTrait("left")
                        VolumeNavigation.RANGE_NAVIGATION -> collector.getRangeBox().moveEntryLeft()
                    }
                    true
                }
                else -> false

            }
        }
    }
}
