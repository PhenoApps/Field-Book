package com.fieldbook.shared.utilities

import com.fieldbook.shared.database.repository.ObservationUnitAttributeRepository
import com.fieldbook.shared.database.repository.StudyRepository

fun selectFirstField() {
    val studyRepository = StudyRepository()
    val fs = studyRepository.getAllFields()
    val attrRepo = ObservationUnitAttributeRepository()

    FieldSwitchImpl(attrRepo, studyRepository).switchField(fs.firstOrNull())
}
