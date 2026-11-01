package com.fieldbook.tracker.dialogs

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.adapters.AttributeAdapter.AttributeModel
import com.fieldbook.tracker.preferences.DropDownKeyModel
import com.fieldbook.tracker.preferences.GeneralKeys

/**
 * Dialog to choose the attribute or trait value encoded by the code display trait.
 * Extends the AttributeChooserDialog the same way InfoBars do, the choice is saved to preferences
 * and shared by all code display traits.
 */
class CodeDisplayAttributeChooserDialog : AttributeChooserDialog() {

    companion object {

        /**
         * Reads the saved choice, defaulting to the field's unique attribute.
         * Returns null if the saved trait no longer exists.
         */
        fun getSelectedAttribute(activity: CollectActivity): AttributeModel? {
            val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
            val traitId = prefs.getString(GeneralKeys.CODE_DISPLAY_TRAIT, DropDownKeyModel.DEFAULT_TRAIT_ID)
                ?: DropDownKeyModel.DEFAULT_TRAIT_ID

            if (traitId != DropDownKeyModel.DEFAULT_TRAIT_ID) {
                val trait = activity.getDatabase().getTraitById(traitId) ?: return null
                return AttributeModel(trait.alias, trait = trait)
            }

            val label = prefs.getString(GeneralKeys.CODE_DISPLAY_ATTRIBUTE, null)
                ?: prefs.getString(GeneralKeys.UNIQUE_NAME, "").orEmpty()

            return AttributeModel(label)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as AlertDialog
        dialog.setTitle(R.string.trait_code_display_data_title)
        return dialog
    }

    override fun getSelected(): AttributeModel? =
        (activity as? CollectActivity)?.let { getSelectedAttribute(it) }

    /** Saves the newly selected attribute, then notifies the listener to redraw the code. */
    override fun onAttributeClicked(model: AttributeModel, position: Int) {
        PreferenceManager.getDefaultSharedPreferences(requireActivity()).edit {
            putString(GeneralKeys.CODE_DISPLAY_ATTRIBUTE, model.label)
            putString(GeneralKeys.CODE_DISPLAY_TRAIT, model.trait?.id ?: DropDownKeyModel.DEFAULT_TRAIT_ID)
        }

        super.onAttributeClicked(model, position)
    }
}
