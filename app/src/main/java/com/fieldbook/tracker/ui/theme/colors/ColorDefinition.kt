package com.fieldbook.tracker.ui.theme.colors

/**
 * Define theme colors for default theme, and override specific colors for other theme in their own implementation
 */
val DefaultAppColors = AppColors(
    primary = BaseColors.Primary,
    primaryDark = BaseColors.PrimaryDark,
    accent = BaseColors.Accent,
    primaryTransparent = BaseColors.PrimaryTransparent,
    background = BaseColors.Background,

    surface = SurfaceColors(
        border = BaseColors.Border,
        preferencesHorizontalBreak = BaseColors.PreferencesHorizontalBreak,
        iconTint = BaseColors.IconTint,
        iconFillTint = BaseColors.IconFillTint
    ),

    text = TextColors(
        primary = BaseColors.TextDark,
        secondary = BaseColors.TextSecondary,
        tertiary = BaseColors.TextLight,
        hint = BaseColors.HintText,
        highContrast = BaseColors.TextHighContrastInverted,
        title = BaseColors.TextLight,
        subheading = BaseColors.SubheadingColor,
        button = BaseColors.ButtonText,
    ),

    button = ButtonColors(
        normal = BaseColors.ButtonColorNormal,
        pressed = BaseColors.ButtonColorPressed,
        textBackground = BaseColors.TextButtonBackground,
        categoricalPress = BaseColors.CategoricalButtonPress,
        categoricalSelected = BaseColors.CategoricalButtonSelected,
        traitBackground = BaseColors.TraitButtonBackgroundTint,
    ),

    interactive = InteractiveColors(
        tapTarget = BaseColors.TapTarget,
        spinnerSelected = BaseColors.SpinnerSelected,
        spinnerFocused = BaseColors.SpinnerFocused,
        seekBar = BaseColors.SeekbarColor,
        seekBarThumb = BaseColors.SeekbarThumb,
        selectedItemBackground = BaseColors.SelectedItemBackground,
    ),

    status = StatusColors(
        valueSaved = BaseColors.ValueSaved,
        valueAltered = BaseColors.ValueAltered,
        success = BaseColors.PrimaryDark,
        error = BaseColors.ErrorMessage,
        bluetoothConnected = BaseColors.BluetoothConnected,
        progressBar = BaseColors.PrimaryDark,
    ),

    dataVisualization = DataVisualizationColors(
        heatmap = HeatmapColors(
            low = BaseColors.HeatmapLow,
            medium = BaseColors.HeatmapMedium,
            high = BaseColors.HeatmapHigh,
            max = BaseColors.HeatmapMax,
        ),
        graph = GraphColors(
            itemSelected = BaseColors.GraphItemSelected,
            itemUnselected = BaseColors.GraphItemUnselected,
            itemText = BaseColors.GraphItemText,
        ),
        dataGrid = DataGridColors(
            emptyCell = BaseColors.EmptyCell,
            activeCell = BaseColors.ActiveCell,
            tableBorder = BaseColors.TableBorder,
            dataFilled = BaseColors.DataFilled,
            cellText = BaseColors.CellText,
            activeCellText = BaseColors.ActiveCellText,
        )
    ),

    trait = TraitColors(
        percent = PercentTraitColors(
            backgroundCenter = BaseColors.TraitPercentBackgroundCenter,
            backgroundStartEnd = BaseColors.TraitPercentBackgroundStartEnd,
            stroke = BaseColors.TraitPercentStroke,
            start = BaseColors.TraitPercentStart
        ),
        boolean = BooleanTraitColors(
            `true` = BaseColors.BooleanTrue,
            `false` = BaseColors.BooleanFalse
        )
    ),

    chip = ChipColors(
        defaultBackground = BaseColors.DefaultChipBackground,
        selectableBackground = BaseColors.SelectableChipBackground,
        selectableStroke = BaseColors.SelectableChipStroke,
        first = BaseColors.ChipFirst,
        second = BaseColors.ChipSecond,
        third = BaseColors.ChipThird,
        fourth = BaseColors.ChipThird
    ),

    stepper = StepperColors(
        icon = BaseColors.StepperIcon,
        iconBackground = BaseColors.StepperIconBg,
        iconOnDone = BaseColors.StepperIconOnDone,
        iconOnDoneBackground = BaseColors.StepperIconOnDoneBg,
        line = BaseColors.StepperLine,
        lineOnDone = BaseColors.StepperLineOnDone
    ),

    crop = CropColors(
        inverseRegion = BaseColors.InverseCropRegion
    ),
)

val BlueAppColors = DefaultAppColors.copy(
    primary = BlueThemeOverrides.Primary,
    primaryDark = BlueThemeOverrides.PrimaryDark,
    accent = BlueThemeOverrides.Accent,
    primaryTransparent = BlueThemeOverrides.PrimaryTransparent,

    surface = DefaultAppColors.surface.copy(
        iconFillTint = BlueThemeOverrides.IconFillTint,
    ),

    text = DefaultAppColors.text.copy(
        tertiary = BlueThemeOverrides.TextLight,
    ),

    button = DefaultAppColors.button.copy(
        pressed = BlueThemeOverrides.ButtonColorPressed,
        categoricalSelected = BlueThemeOverrides.CategoricalButtonSelected
    ),

    interactive = DefaultAppColors.interactive.copy(
        spinnerFocused = BlueThemeOverrides.SpinnerFocused,
        spinnerSelected = BlueThemeOverrides.SpinnerSelected,
        selectedItemBackground = BlueThemeOverrides.SelectedItemBackground
    ),

    status = DefaultAppColors.status.copy(
        success = BlueThemeOverrides.PrimaryDark,
        progressBar = BlueThemeOverrides.PrimaryDark,
    ),

    dataVisualization = DefaultAppColors.dataVisualization.copy(
        heatmap = HeatmapColors(
            low = BlueThemeOverrides.HeatmapLow,
            medium = BlueThemeOverrides.HeatmapMedium,
            high = BlueThemeOverrides.HeatmapHigh,
            max = BlueThemeOverrides.HeatmapMax
        ),
        graph = DefaultAppColors.dataVisualization.graph.copy(
            itemSelected = BlueThemeOverrides.GraphItemSelected,
        ),
        dataGrid = DefaultAppColors.dataVisualization.dataGrid.copy(
            activeCell = BlueThemeOverrides.ActiveCell,
            dataFilled = BlueThemeOverrides.DataFilled,
        ),
    ),

    trait = DefaultAppColors.trait.copy(
        percent = DefaultAppColors.trait.percent.copy(
            stroke = BlueThemeOverrides.TraitPercentStroke,
            start = BlueThemeOverrides.TraitPercentStart
        )
    ),

    chip = DefaultAppColors.chip.copy(
        defaultBackground = BlueThemeOverrides.DefaultChipBackground,
        selectableStroke = BlueThemeOverrides.SelectableChipStroke,
        first = BlueThemeOverrides.ChipFirst,
        second = BlueThemeOverrides.ChipSecond,
        third = BlueThemeOverrides.ChipThird
    ),

    stepper = DefaultAppColors.stepper.copy(
        icon = BlueThemeOverrides.StepperIcon,
        iconOnDoneBackground = BlueThemeOverrides.StepperIconOnDoneBg,
        lineOnDone = BlueThemeOverrides.StepperLineOnDone
    ),
)

val HighContrastAppColors = DefaultAppColors.copy(
    primary = HighContrastOverrides.Primary,
    primaryDark = HighContrastOverrides.PrimaryDark,
    accent = HighContrastOverrides.Accent,
    primaryTransparent = HighContrastOverrides.PrimaryTransparent,

    surface = DefaultAppColors.surface.copy(
        iconFillTint = HighContrastOverrides.IconFillTint,
        preferencesHorizontalBreak = HighContrastOverrides.PreferencesHorizontalBreak
    ),

    text = DefaultAppColors.text.copy(
        highContrast = HighContrastOverrides.TextHighContrastInverted
    ),

    button = DefaultAppColors.button.copy(
        normal = HighContrastOverrides.ButtonColorNormal,
        pressed = HighContrastOverrides.ButtonColorPressed,
        categoricalPress = HighContrastOverrides.CategoricalButtonPress,
        categoricalSelected = HighContrastOverrides.CategoricalButtonSelected,
        traitBackground = HighContrastOverrides.TraitButtonBackgroundTint
    ),

    interactive = DefaultAppColors.interactive.copy(
        spinnerFocused = HighContrastOverrides.SpinnerFocused,
        spinnerSelected = HighContrastOverrides.SpinnerSelected,
        selectedItemBackground = HighContrastOverrides.SelectedItemBackground
    ),

    status = DefaultAppColors.status.copy(
        valueSaved = HighContrastOverrides.ValueSaved,
        valueAltered = HighContrastOverrides.ValueAltered,
        success = HighContrastOverrides.PrimaryDark,
        error = HighContrastOverrides.ErrorMessage,
        bluetoothConnected = HighContrastOverrides.BluetoothConnected,
        progressBar = HighContrastOverrides.PrimaryDark,
    ),

    dataVisualization = DefaultAppColors.dataVisualization.copy(
        heatmap = HeatmapColors(
            low = HighContrastOverrides.HeatmapLow,
            medium = HighContrastOverrides.HeatmapMedium,
            high = HighContrastOverrides.HeatmapHigh,
            max = HighContrastOverrides.HeatmapMax
        ),
        graph = DefaultAppColors.dataVisualization.graph.copy(
            itemSelected = HighContrastOverrides.GraphItemSelected,
            itemUnselected = HighContrastOverrides.GraphItemUnselected,
            itemText = HighContrastOverrides.GraphItemText,
        ),
        dataGrid = DefaultAppColors.dataVisualization.dataGrid.copy(
            emptyCell = HighContrastOverrides.EmptyCell,
            activeCell = HighContrastOverrides.ActiveCell,
            dataFilled = HighContrastOverrides.DataFilled,
        )
    ),

    trait = DefaultAppColors.trait.copy(
        percent = DefaultAppColors.trait.percent.copy(
            backgroundCenter = HighContrastOverrides.TraitPercentBackgroundCenter,
            backgroundStartEnd = HighContrastOverrides.TraitPercentBackgroundStartEnd,
            stroke = HighContrastOverrides.TraitPercentStroke,
            start = HighContrastOverrides.TraitPercentStart
        ),
        boolean = DefaultAppColors.trait.boolean.copy(
            `true` = HighContrastOverrides.BooleanTrue,
            `false` = HighContrastOverrides.BooleanFalse
        )
    ),

    chip = DefaultAppColors.chip.copy(
        defaultBackground = HighContrastOverrides.DefaultChipBackground,
        selectableStroke = HighContrastOverrides.SelectableChipStroke,
        first = HighContrastOverrides.ChipFirst,
        second = HighContrastOverrides.ChipSecond,
        third = HighContrastOverrides.ChipThird,
        fourth = HighContrastOverrides.ChipThird
    ),

    stepper = DefaultAppColors.stepper.copy(
        icon = HighContrastOverrides.StepperIcon,
        iconBackground = HighContrastOverrides.StepperIconBg,
        iconOnDone = HighContrastOverrides.StepperIconOnDone,
        iconOnDoneBackground = HighContrastOverrides.StepperIconOnDoneBg,
        line = HighContrastOverrides.StepperLine,
        lineOnDone = HighContrastOverrides.StepperLineOnDone
    ),
)