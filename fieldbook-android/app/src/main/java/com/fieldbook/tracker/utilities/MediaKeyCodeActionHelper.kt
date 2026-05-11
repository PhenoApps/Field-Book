package com.fieldbook.tracker.utilities

import android.view.KeyEvent
import com.fieldbook.tracker.interfaces.CollectController
import com.fieldbook.tracker.preferences.PreferenceKeys
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
                prefs.getString(PreferenceKeys.VOLUME_NAVIGATION, VolumeNavigation.DISABLED.ordinal.toString())
            } catch (e: ClassCastException) {
                prefs.edit().remove(PreferenceKeys.VOLUME_NAVIGATION).apply()
            }

            val volumeNavEnabled = when(prefs.getString(PreferenceKeys.VOLUME_NAVIGATION, VolumeNavigation.DISABLED.ordinal.toString())) {
                VolumeNavigation.DISABLED.ordinal.toString() -> VolumeNavigation.DISABLED
                VolumeNavigation.TRAIT_NAVIGATION.ordinal.toString() -> VolumeNavigation.TRAIT_NAVIGATION
                VolumeNavigation.RANGE_NAVIGATION.ordinal.toString() -> VolumeNavigation.RANGE_NAVIGATION
                else -> VolumeNavigation.DISABLED
            }

            val mediaControlEnabled = prefs.getBoolean(PreferenceKeys.MEDIA_KEYCODE_NAVIGATION, false)

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
                                collector.getTraitBox().moveTrait(TraitBoxView.MoveDirection.RIGHT)
                            }
                        }
                        VolumeNavigation.TRAIT_NAVIGATION-> collector.getRangeBox().moveEntryRight()
                        VolumeNavigation.RANGE_NAVIGATION -> collector.getTraitBox().moveTrait(
                            TraitBoxView.MoveDirection.RIGHT)
                    }
                    true
                }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    when (volumeNavEnabled) {
                        VolumeNavigation.DISABLED -> {
                            if (mediaControlEnabled) {
                                collector.getTraitBox().moveTrait(TraitBoxView.MoveDirection.LEFT)
                            }
                        }
                        VolumeNavigation.TRAIT_NAVIGATION-> collector.getRangeBox().moveEntryLeft()
                        VolumeNavigation.RANGE_NAVIGATION -> collector.getTraitBox().moveTrait(
                            TraitBoxView.MoveDirection.LEFT)
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
                        VolumeNavigation.TRAIT_NAVIGATION-> collector.getTraitBox().moveTrait(
                            TraitBoxView.MoveDirection.RIGHT)
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
                        VolumeNavigation.TRAIT_NAVIGATION -> collector.getTraitBox().moveTrait(
                            TraitBoxView.MoveDirection.LEFT)
                        VolumeNavigation.RANGE_NAVIGATION -> collector.getRangeBox().moveEntryLeft()
                    }
                    true
                }
                else -> false

            }
        }
    }
}
