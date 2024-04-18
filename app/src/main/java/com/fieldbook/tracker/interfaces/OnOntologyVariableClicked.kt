package com.fieldbook.tracker.interfaces

import com.fieldbook.tracker.activities.brapi.hackathon.cropontology.tables.BrapiStudy

interface OnOntologyVariableClicked {
    fun onItemClicked(ontology: BrapiStudy)
}