package com.fieldbook.tracker.database.models

import com.fieldbook.tracker.database.Row

data class ObservationUnitPropertyModel(val map: Row) {
    val plot_id: String by map
    val row: String by map
    val column: String by map
    val plot: String by map
    val tray_row: String? by map
    val tray_id: String? by map
    val seed_id: String? by map
    val seed_name: String? by map
    val pedigree: String? by map
    val id: Int by map
    val geo_coordinates: String? by map

}