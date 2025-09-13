package com.fieldbook.tracker.enums

import com.fieldbook.tracker.R

enum class FieldCreationStep(val position: Int, val icon: Int?) {
    FIELD_SIZE(0, R.drawable.ic_field_config),
    START_CORNER(1, R.drawable.ic_grid_corner),
    WALKING_DIRECTION(2, R.drawable.ic_direction),
    WALKING_PATTERN(3, R.drawable.ic_walk),
    FIELD_PREVIEW(4, R.drawable.ic_field_preview),

    COMPLETED(5, null);

    companion object {
        // return all values except COMPLETED which is just denotes the state
        fun displayableEntries(): Array<FieldCreationStep> {
            return entries.filterNot { it == COMPLETED }.toTypedArray()
        }
    }
}