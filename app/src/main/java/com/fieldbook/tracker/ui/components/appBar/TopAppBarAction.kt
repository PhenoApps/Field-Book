package com.fieldbook.tracker.ui.components.appBar

enum class ActionDisplayMode {
    /** Always show as icon in the toolbar */
    ALWAYS,
    /** Show as icon if there's room, otherwise in overflow menu */
    IF_ROOM,
    /** Always show in the overflow menu */
    NEVER
}

data class TopAppBarAction(
    val title: String,
    val contentDescription: String,
    val icon: Any? = null,
    val displayMode: ActionDisplayMode = ActionDisplayMode.IF_ROOM,
    val onClick: () -> Unit
)