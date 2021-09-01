package com.fieldbook.tracker.interfaces

import com.fieldbook.tracker.cropontology.tables.Variable

interface OnOntologyVariableClicked {
    fun onItemClicked(ontology: Variable)
}