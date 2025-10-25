package com.fieldbook.tracker.utilities

import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.objects.TraitObject

object TraitNameValidator {

    /**
     * Validates trait alias against existing trait names/aliases
     * Returns null if no conflicts
     */
    fun validateTraitAlias(alias: String, db: DataHelper, currentTrait: TraitObject? = null): Int? {
        val alias = alias.trim()
        if (alias.isBlank()) {
            return R.string.traits_create_warning_name_blank
        }

        val traitByName = db.getTraitByName(alias)
        val traitByAlias = db.getTraitByAlias(alias)
        val conflictingTrait = traitByName ?: traitByAlias

        if (conflictingTrait != null && conflictingTrait.id != currentTrait?.id) {
            return R.string.traits_create_warning_duplicate
        }

        return null
    }
}
