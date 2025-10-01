package com.fieldbook.shared.utilities

import com.fieldbook.shared.sqldelight.DriverFactory
import com.fieldbook.shared.sqldelight.FieldbookDatabase
import com.fieldbook.shared.database.repository.StudiesRepository

fun selectFirstField(driverFactory: DriverFactory) {
    val db = FieldbookDatabase(driverFactory.createDriver())
    val studiesRepository = StudiesRepository(db)
    val fs = studiesRepository.getAllFields()
    FieldSwitchImpl().switchField(fs.firstOrNull())
}

