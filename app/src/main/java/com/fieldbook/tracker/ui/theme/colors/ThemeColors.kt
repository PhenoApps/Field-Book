package com.fieldbook.tracker.ui.theme.colors

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class ThemeColors(
    // these are changed for each theme
    val primary: Color,
    val primaryDark: Color,
    val accent: Color,
    val primaryTransparent: Color,
    val background: Color,

    // tutorial library
    val tapTarget: Color,

    // at times for each theme its helpful to have a dark/light text color
    val textLight: Color,
    val textDark: Color,
    val textSecondary: Color,
    val textHighContrastInverted: Color,

    // color of edit text hint
    val hintText: Color,

    // color of buttons with text
    val textButtonBackground: Color,
    // text color of all buttons
    val buttonText: Color,

    // color of the highlight applied to the list item that is currently selected
    val spinnerSelected: Color,
    // color of the box surrounding a spinner list item when it is clicked
    val spinnerFocused: Color,

    // these will change for all icons
    val iconTint: Color,
    val iconFillTint: Color,

    // toolbar titles
    val titleText: Color,
    val subheading: Color,

    // circular progress bar
    val indeterminateProgressBar: Color,

    // specific to certain features/traits
    // round button normal/pressed colors (counters, gps, ...)
    val buttonColorNormal: Color,
    val buttonColorPressed: Color,

    // default red/blue when a value is saved or a saved value is changed
    val valueSaved: Color,
    val valueAltered: Color,

    // button press color for categorical traits
    val categoricalButtonPress: Color,

    val bluetoothConnected: Color,
    val booleanTrue: Color,
    val booleanFalse: Color,

    // percent trait colors
    val seekBar: Color,
    val traitPercentBackgroundCenter: Color,
    val traitPercentBackgroundStartEnd: Color,
    val traitPercentStroke: Color,
    val traitPercentStart: Color,

    val seekBarColor: Color,
    val seekBarThumbColor: Color,

    val border: Color,

    val traitButtonBackgroundTint: Color,

    val categoricalButtonSelected: Color,

    val preferencesHorizontalBreak: Color,

    // within dialog error message color
    val errorMessageColor: Color,

    // statistics activity heatmap colors
    val heatmapLow: Color,
    val heatmapMedium: Color,
    val heatmapHigh: Color,
    val heatmapMax: Color,

    // action mode selected item
    val selectedItemBackground: Color,

    // chip colors
    val defaultChipBackground: Color,
    val selectableChipBackground: Color,
    val selectableChipStroke: Color,

    // generic chip colors for the different filtering layers/themes
    val firstChip: Color,
    val secondChip: Color,
    val thirdChip: Color,
    val fourthChip: Color,

    val inverseCropRegion: Color,

    val graphItemSelected: Color,
    val graphItemUnselected: Color,
    val graphItemText: Color,

    // daa grid colors
    val dataFilled: Color,
    val emptyCell: Color,
    val activeCell: Color,
    val activeCellText: Color,
    val cellText: Color,
    val tableBorder: Color,

    // stepper ui colors
    val stepperIcon: Color,
    val stepperIconBg: Color,
    val stepperIconOnDone: Color,
    val stepperIconOnDoneBg: Color,
    val stepperLine: Color,
    val stepperLineOnDone: Color

)