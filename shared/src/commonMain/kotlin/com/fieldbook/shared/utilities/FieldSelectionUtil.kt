package com.fieldbook.shared.utilities

import com.fieldbook.shared.database.repository.ObservationUnitAttributeRepository
import com.fieldbook.shared.database.repository.StudyRepository
import com.fieldbook.shared.sqldelight.DriverFactory
import com.fieldbook.shared.sqldelight.createDatabase

fun selectFirstField(driverFactory: DriverFactory) {
    val db = createDatabase(driverFactory)
    val studyRepository = StudyRepository(db)
    val fs = studyRepository.getAllFields()
    val attrRepo = ObservationUnitAttributeRepository(db)
    FieldSwitchImpl(attrRepo).switchField(fs.firstOrNull())
}
