package com.fieldbook.tracker.dialogs

import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.appcompat.content.res.AppCompatResources
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.brapi.update.BrapiCropsFilterActivity
import com.fieldbook.tracker.activities.brapi.update.BrapiFilterTypeAdapter
import com.fieldbook.tracker.activities.brapi.update.BrapiProgramFilterActivity
import com.fieldbook.tracker.activities.brapi.update.BrapiSeasonsFilterActivity
import com.fieldbook.tracker.activities.brapi.update.BrapiTrialsFilterActivity
import com.fieldbook.tracker.activities.brapi.update.BrapiStudyFilterActivity
import com.google.android.material.badge.ExperimentalBadgeUtils
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup


class BrapiFilterBottomSheetDialog(
    private val preferences: SharedPreferences,
    private val onFilterSelected: OnFilterSelectedListener) :
    BottomSheetDialogFragment() {

    lateinit var filtersChipGroup: ChipGroup
    lateinit var chipGroup: ChipGroup
    lateinit var programChip: Chip
    lateinit var seasonsChip: Chip
    lateinit var trialsChip: Chip
    lateinit var cropsChip: Chip
    lateinit var okButton: MaterialButton
    lateinit var resetButton: MaterialButton

    interface OnFilterSelectedListener {
        fun onFilterSelected(filter: Int)
        fun onDismiss()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onFilterSelected.onDismiss()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val v = inflater.inflate(R.layout.brapi_filter_bottom_sheet, container, false)

        chipGroup = v.findViewById(R.id.brapi_filter_action_cg)
        programChip = v.findViewById(R.id.brapi_filter_programs_cp)
        seasonsChip = v.findViewById(R.id.brapi_filter_seasons_cp)
        cropsChip = v.findViewById(R.id.brapi_filter_crops_cp)

        trialsChip = v.findViewById(R.id.brapi_filter_trials_cp)
        filtersChipGroup = v.findViewById(R.id.brapi_filter_cg)

        okButton = v.findViewById(R.id.brapi_filter_ok_btn)
        resetButton = v.findViewById(R.id.brapi_filter_reset_btn)

        setupButtons()
        resetChipsUi()

        return v
    }

    private fun setupButtons() {

        okButton.setOnClickListener {

            val actionId = when {
                programChip.isChecked -> BrapiStudyFilterActivity.FilterChoice.PROGRAM.ordinal
                trialsChip.isChecked -> BrapiStudyFilterActivity.FilterChoice.TRIAL.ordinal
                seasonsChip.isChecked -> BrapiStudyFilterActivity.FilterChoice.SEASON.ordinal
                cropsChip.isChecked -> BrapiStudyFilterActivity.FilterChoice.CROP.ordinal
                else -> return@setOnClickListener
            }

            onFilterSelected.onFilterSelected(actionId)

            dismiss()
        }

        resetButton.setOnClickListener {

            with(preferences.edit()) {
                for (f in listOf(
                    BrapiProgramFilterActivity.FILTER_NAME,
                    BrapiTrialsFilterActivity.FILTER_NAME,
                    BrapiSeasonsFilterActivity.FILTER_NAME,
                    BrapiCropsFilterActivity.FILTER_NAME
                )) {
                    remove(f)
                }
                apply()
            }

            filtersChipGroup.removeAllViews()
        }
    }

    private fun createChip(styleResId: Int, label: String, id: String) =
        Chip(ContextThemeWrapper(context, styleResId)).apply {
            text = label
            tag = id
            closeIcon = AppCompatResources.getDrawable(context, R.drawable.close)
            isCloseIconVisible = true
            isCheckable = false
        }

    @OptIn(ExperimentalBadgeUtils::class)
    private fun resetChipsUi() {

        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            okButton.isEnabled = checkedIds.isNotEmpty()
        }

        //add new children that are loaded from the program filter activity
        for (f in listOf(
            BrapiProgramFilterActivity.FILTER_NAME,
            BrapiTrialsFilterActivity.FILTER_NAME,
            BrapiSeasonsFilterActivity.FILTER_NAME,
            BrapiCropsFilterActivity.FILTER_NAME
        )) {
            resetChipsUi(f)
        }

        resetButton.isEnabled = filtersChipGroup.childCount > 0
    }

    private fun resetChipsUi(filterName: String) {

        BrapiFilterTypeAdapter.toModelList(preferences, filterName)
            .forEach { model ->

                val chip = createChip(
                    when (filterName) {
                        BrapiProgramFilterActivity.FILTER_NAME -> R.style.FirstChipTheme
                        BrapiSeasonsFilterActivity.FILTER_NAME -> R.style.SecondChipTheme
                        BrapiTrialsFilterActivity.FILTER_NAME -> R.style.ThirdChipTheme
                        else -> R.style.FourthChipTheme
                    }, model.label, model.id
                )

                chip.setOnCloseIconClickListener {

                    deleteFilterId(filterName, model.id)
                }

                filtersChipGroup.addView(chip)
            }
    }

    private fun deleteFilterId(filterName: String, id: String) {

        filtersChipGroup.removeAllViews()

        BrapiFilterTypeAdapter.deleteFilterId(preferences, filterName, id)

        resetChipsUi()
    }
}