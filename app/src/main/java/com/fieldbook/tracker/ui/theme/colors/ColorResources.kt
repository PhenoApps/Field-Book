package com.fieldbook.tracker.ui.theme.colors

import androidx.compose.ui.graphics.Color

/**
 * Define colors in BaseColors. Specify overriding colors in other objects
 * You can reuse colors like Primary, PrimaryDark, etc. for other colors eg. TraitPercentStart
 *
 * For other themes, only define colors that are specific to it (colors different than its corresponding value in BaseColors)
 */

val LightGray = Color(0xFFE7E8E8)
val Gray = Color(0xFF9E9E9E)

object BaseColors {
    val Border = Color.Black
    val Primary = Color(0xFF8BC34A)
    val PrimaryDark = Color(0xFF689F38)
    val Accent = Color(0xFF795548)
    val PrimaryTransparent = Color(0x428BC34A)
    val TraitPercentBackgroundCenter = Color.White
    val TraitPercentBackgroundStartEnd = Color(0xFFDDDDDD)
    val TraitPercentStroke = PrimaryDark
    val TraitPercentStart = Primary
    val TapTarget = Color.Black
    val Background = Color.White
    val LightGrayColor = LightGray
    val TextLight = Color.Black
    val TextDark = Color.Black
    val TextButtonBackground = Color(0xFFD6D7D7)
    val IconTint = Color.Black
    val IconFillTint = Color.Black
    val BooleanFalse = Color.Red
    val BooleanTrue = PrimaryDark
    val BluetoothConnected = Color(0xFF33B5E5)
    val HintText = Color.Black
    val ValueSaved = Color(0xFFD50000)
    val ValueAltered = Color(0xFF0000D5)
    val CategoricalButtonPress = Color(0xFF33B5E5)
    val ButtonText = Color.Black
    val ButtonColorNormal = Color(0xFFD9D9D9)
    val ButtonColorPressed = Color(0xFF595959)
    val TextHighContrastInverted = ButtonText
    val SpinnerFocused = Accent
    val SpinnerSelected = PrimaryTransparent
    val SubheadingColor = Color(0xFF595959)
    val TextSecondary = Color(0xFF595959)
    val SeekbarColor = Color(0x42000000)
    val SeekbarThumb = Color(0x90654321)
    val ChipFirst = Color(0xFF47B65D)
    val ChipSecond = Color(0xFF00A771)
    val ChipThird = Color(0xFF009683)
    val InverseCropRegion = Color(0x80101010)
    val DatagridEmptyCell = Color(0xFFB6BABA)
    val HeatmapLow = Color(0xFFB2F2BB)
    val HeatmapMedium = Color(0xFF69DB7C)
    val HeatmapHigh = Color(0xFF40C057)
    val HeatmapMax = Color(0xFF2F9E44)
    val CategoricalButtonSelected = Primary
    val TraitButtonBackgroundTint = LightGray
    val PreferencesHorizontalBreak = Color.Transparent
    val ErrorMessage = Color.Red
    val SelectedItemBackground = PrimaryTransparent
    val DefaultChipBackground = PrimaryTransparent
    val SelectableChipBackground = Color.White
    val SelectableChipStroke = Primary
    val GraphItemSelected = Primary
    val GraphItemUnselected = Color.White
    val GraphItemText = Color.Black
    val DataFilled = PrimaryTransparent
    val EmptyCell = DatagridEmptyCell
    val ActiveCell = PrimaryDark
    val ActiveCellText = Color.White
    val CellText = Color.Black
    val TableBorder = Color.Black
    val StepperIcon = Primary
    val StepperIconBg = LightGray
    val StepperIconOnDone = Color.White
    val StepperIconOnDoneBg = Primary
    val StepperLine = Gray
    val StepperLineOnDone = Primary
}

object BlueThemeOverrides {
    val Primary = Color(0xFF01A7C2)
    val PrimaryDark = Color(0xFF007090)
    val Accent = Color(0xFF795548)
    val PrimaryTransparent = Color(0x4201A7C2)
    val TraitPercentStroke = PrimaryDark
    val TraitPercentStart = Primary
    val TextLight = Color.White
    val IconFillTint = Color.White
    val ButtonColorPressed = Color(0xFF969696)
    val SpinnerFocused = Accent
    val SpinnerSelected = PrimaryTransparent
    val ChipFirst = Color(0xFF2FD2B3)
    val ChipSecond = Color(0xFF74E39B)
    val ChipThird = Color(0xFFB5F082)
    val HeatmapLow = Color(0xFFBDA0BC)
    val HeatmapMedium = Primary
    val HeatmapHigh = Color(0xFF6457A6)
    val HeatmapMax = Color(0xFF5C2751)
    val CategoricalButtonSelected = Primary
    val SelectedItemBackground = PrimaryTransparent
    val DefaultChipBackground = PrimaryTransparent
    val SelectableChipStroke = Primary
    val GraphItemSelected = Primary
    val DataFilled = PrimaryTransparent
    val ActiveCell = PrimaryDark
    val StepperIcon = Primary
    val StepperIconOnDoneBg = Primary
    val StepperLineOnDone = Primary
}

object HighContrastOverrides {
    val Primary = Color.White
    val PrimaryDark = Color.Black
    val Accent = Color.Black
    val PrimaryTransparent = Color.White
    val LightGrayColor = Color.Black
    val TraitPercentBackgroundCenter = Color.White
    val TraitPercentBackgroundStartEnd = Color.White
    val TraitPercentStroke = Color.Black
    val TraitPercentStart = Color.White
    val TextHighContrastInverted = Color.Black
    val IconFillTint = Color.White
    val BooleanFalse = Color.Black
    val BooleanTrue = Color.Black
    val BluetoothConnected = Color.Black
    val ValueSaved = Color.Black
    val ValueAltered = Color.Black
    val CategoricalButtonPress = Color.White
    val ButtonColorNormal = Color.Black
    val ButtonColorPressed = Color.White
    val SpinnerFocused = Accent
    val SpinnerSelected = PrimaryTransparent
    val ChipFirst = Color(0xFFC6C6C6)
    val ChipSecond = Color(0xFF919191)
    val ChipThird = Color(0xFF5E5E5E)
    val HeatmapLow = Color(0xFF626262)
    val HeatmapMedium = Color(0xFF585858)
    val HeatmapHigh = Color(0xFF484848)
    val HeatmapMax = Color(0xFF343434)
    val CategoricalButtonSelected = Gray
    val TraitButtonBackgroundTint = Color.Black
    val PreferencesHorizontalBreak = Color.Black
    val ErrorMessage =  Gray
    val SelectedItemBackground = LightGray
    val DefaultChipBackground = LightGray
    val SelectableChipStroke = Gray
    val GraphItemSelected = Color.Black
    val GraphItemUnselected = Gray
    val GraphItemText = Color.Black
    val DataFilled = Color(0xFF878585)
    val EmptyCell = Color.White
    val ActiveCell = Color(0xFF373737)
    val StepperIcon = Color.Black
    val StepperIconBg = Color.White
    val StepperIconOnDone = Color.White
    val StepperIconOnDoneBg = Color.Black
    val StepperLine = Color.Black
    val StepperLineOnDone = Color.Black
}
