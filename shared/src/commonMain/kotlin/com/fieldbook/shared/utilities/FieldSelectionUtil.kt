package com.fieldbook.shared.utilities

import com.fieldbook.shared.database.repository.ObservationUnitAttributeRepository
import com.fieldbook.shared.database.repository.StudiesRepository
import com.fieldbook.shared.sqldelight.DriverFactory
import com.fieldbook.shared.sqldelight.createDatabase

fun selectFirstField(driverFactory: DriverFactory) {
    val db = createDatabase(driverFactory)
    val studiesRepository = StudiesRepository(db)
    val fs = studiesRepository.getAllFields()
    val attrRepo = ObservationUnitAttributeRepository(db)
    FieldSwitchImpl(attrRepo).switchField(fs.firstOrNull())
}
