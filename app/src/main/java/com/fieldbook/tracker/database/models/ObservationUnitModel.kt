package com.fieldbook.tracker.database.models

import com.fieldbook.tracker.database.Row

/**
 * Plot-level table structure.
 * */
data class ObservationUnitModel(val map: Row) {

        val internal_id_observation_unit: Int by map //comp. pk 1
        val study_id: Int by map  //fk to studies table
        val observation_unit_db_id: String by map //unique id
        val primary_id: String by map
        val secondary_id: String by map
        val geo_coordinates: String? by map //blob?
        val additionalInfo: String? by map //blob, can be replaced with value/attr query?
        val germplasmDbId: String? by map //brapId ?
        val germplasmName: String? by map
        val observationLevel: String? by map
        val position_coordinate_x: String? by map //x-axis value e.g row=1
        val positionCoordinateXType: String? by map //x-axis label?
        val position_coordinate_y: String? by map //y-axis value e.g col=2
        val positionCoordinateYType: String? by map
}