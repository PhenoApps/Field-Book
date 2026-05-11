package com.fieldbook.tracker.enums.traits

import com.fieldbook.tracker.R

enum class ToggleLayoutType(
    val layoutId: Int,
    val toggleButtonId: Int
) {
    ENABLED_DISABLED(
        layoutId = R.layout.list_item_trait_parameter_default_enabled_toggle,
        toggleButtonId = R.id.dialog_new_trait_default_enabled_toggle_btn
    ),
    TRUE_FALSE(
        layoutId = R.layout.list_item_trait_parameter_default_toggle_value,
        toggleButtonId = R.id.dialog_new_trait_default_toggle_btn
    )
}