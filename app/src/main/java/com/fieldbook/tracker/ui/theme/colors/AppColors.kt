package com.fieldbook.tracker.ui.theme.colors

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class AppColors(
    val primary: Color,
    val primaryDark: Color,
    val accent: Color,
    val primaryTransparent: Color,
    val background: Color,

    val surface: SurfaceColors,

    val text: TextColors,

    val button: ButtonColors,

    val interactive: InteractiveColors,

    val status: StatusColors,

    val dataVisualization: DataVisualizationColors,

    val trait: TraitColors,

    val chip: ChipColors,

    val stepper: StepperColors,

    val crop: CropColors,
)

@Immutable
data class TextColors(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color, // for toolbar (blue theme has white)
    val hint: Color,
    val highContrast: Color,
    val title: Color,
    val subheading: Color,
    val button: Color,
)

@Immutable
data class ButtonColors(
    val normal: Color,
    val pressed: Color,
    val textBackground: Color,
    val categoricalPress: Color,
    val categoricalSelected: Color,
    val traitBackground: Color,
)

@Immutable
data class InteractiveColors(
    val tapTarget: Color,
    val spinnerSelected: Color,
    val spinnerFocused: Color,
    val seekBar: Color,
    val seekBarThumb: Color,
    val selectedItemBackground: Color,
)

@Immutable
data class StatusColors(
    val valueSaved: Color,
    val valueAltered: Color,
    val success: Color,
    val error: Color,
    val bluetoothConnected: Color,
    val progressBar: Color,
)

@Immutable
data class DataVisualizationColors(
    val heatmap: HeatmapColors,
    val graph: GraphColors,
    val dataGrid: DataGridColors,
)

@Immutable
data class HeatmapColors(
    val low: Color,
    val medium: Color,
    val high: Color,
    val max: Color,
)

@Immutable
data class GraphColors(
    val itemSelected: Color,
    val itemUnselected: Color,
    val itemText: Color,
)

@Immutable
data class DataGridColors(
    val emptyCell: Color,
    val activeCell: Color,
    val tableBorder: Color,
    val dataFilled: Color,
    val cellText: Color,
    val activeCellText: Color,
)

@Immutable
data class TraitColors(
    val percent: PercentTraitColors,
    val boolean: BooleanTraitColors,
)

@Immutable
data class PercentTraitColors(
    val backgroundCenter: Color,
    val backgroundStartEnd: Color,
    val stroke: Color,
    val start: Color,
)

@Immutable
data class BooleanTraitColors(
    val `true`: Color,
    val `false`: Color,
)

@Immutable
data class ChipColors(
    val defaultBackground: Color,
    val selectableBackground: Color,
    val selectableStroke: Color,
    val first: Color,
    val second: Color,
    val third: Color,
    val fourth: Color,
)

@Immutable
data class StepperColors(
    val icon: Color,
    val iconBackground: Color,
    val iconOnDone: Color,
    val iconOnDoneBackground: Color,
    val line: Color,
    val lineOnDone: Color,
)

@Immutable
data class CropColors(
    val inverseRegion: Color,
)

@Immutable
data class SurfaceColors(
    val border: Color,
    val preferencesHorizontalBreak: Color,
    val iconTint: Color,
    val iconFillTint: Color,
)