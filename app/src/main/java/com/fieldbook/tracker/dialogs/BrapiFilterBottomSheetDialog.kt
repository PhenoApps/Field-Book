package com.fieldbook.tracker.dialogs

import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.appcompat.content.res.AppCompatResources
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.brapi.io.BrapiCropsFilterActivity
import com.fieldbook.tracker.activities.brapi.io.BrapiFilterTypeAdapter
import com.fieldbook.tracker.activities.brapi.io.BrapiProgramFilterActivity
import com.fieldbook.tracker.activities.brapi.io.BrapiSeasonsFilterActivity
import com.fieldbook.tracker.activities.brapi.io.BrapiStudyFilterActivity
import com.fieldbook.tracker.activities.brapi.io.BrapiTrialsFilterActivity
import com.fieldbook.tracker.preferences.GeneralKeys
import com.google.android.material.badge.ExperimentalBadgeUtils
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup


class BrapiFilterBottomSheetDialog(
    private val preferences: SharedPreferences,
    private val onFilterSelected: OnFilterSelectedListener) :
    BottomSheetDialogFragment() {

    private lateinit var filtersChipGroup: ChipGroup
    private lateinit var chipGroup: ChipGroup
    private lateinit var programChip: Chip
    private lateinit var seasonsChip: Chip
    private lateinit var trialsChip: Chip
    private lateinit var cropsChip: Chip
    private lateinit var clearFilterButton: ImageButton
    private lateinit var subtitleTv: TextView

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

        clearFilterButton = v.findViewById(R.id.brapi_filter_delete_all_ib)
        subtitleTv = v.findViewById(R.id.brapi_filter_subtitle_tv)

        setupButtons()
        resetChipsUi()

        return v
    }

    private fun setupButtons() {

        programChip.setOnClickListener {
            onFilterSelected.onFilterSelected(BrapiStudyFilterActivity.FilterChoice.PROGRAM.ordinal)
            dismiss()
        }

        trialsChip.setOnClickListener {
            onFilterSelected.onFilterSelected(BrapiStudyFilterActivity.FilterChoice.TRIAL.ordinal)
            dismiss()
        }

        seasonsChip.setOnClickListener {
            onFilterSelected.onFilterSelected(BrapiStudyFilterActivity.FilterChoice.SEASON.ordinal)
            dismiss()
        }

        cropsChip.setOnClickListener {
            onFilterSelected.onFilterSelected(BrapiStudyFilterActivity.FilterChoice.CROP.ordinal)
            dismiss()
        }

        clearFilterButton.setOnClickListener {

            with(preferences.edit()) {
                for (f in listOf(
                    BrapiProgramFilterActivity.FILTER_NAME,
                    BrapiTrialsFilterActivity.FILTER_NAME,
                    BrapiSeasonsFilterActivity.FILTER_NAME,
                    BrapiCropsFilterActivity.FILTER_NAME
                )) {
                    remove(f)
                }

                remove(GeneralKeys.LIST_FILTER_TEXTS)

                apply()
            }

            filtersChipGroup.removeAllViews()

            resetChipsUi()
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

        filtersChipGroup.removeAllViews()

        //add new children that are loaded from the program filter activity
        for (f in listOf(
            BrapiProgramFilterActivity.FILTER_NAME,
            BrapiTrialsFilterActivity.FILTER_NAME,
            BrapiSeasonsFilterActivity.FILTER_NAME,
            BrapiCropsFilterActivity.FILTER_NAME
        )) {
            resetChipsUi(f)
        }

        val searchText = preferences.getStringSet(GeneralKeys.LIST_FILTER_TEXTS, setOf())
        searchText?.forEach { text ->
            val chip = createChip(R.style.FourthChipTheme, text, text)
            chip.setOnCloseIconClickListener {
                val currentTexts = preferences.getStringSet(GeneralKeys.LIST_FILTER_TEXTS, setOf())?.toMutableSet()
                currentTexts?.remove(text)
                preferences.edit().putStringSet(GeneralKeys.LIST_FILTER_TEXTS, currentTexts).apply()
                filtersChipGroup.removeView(chip)
                resetChipsUi()
            }
            filtersChipGroup.addView(chip)
        }

        clearFilterButton.visibility = if (filtersChipGroup.childCount > 0) View.VISIBLE else View.GONE
        subtitleTv.visibility = if (filtersChipGroup.childCount > 0) View.VISIBLE else View.GONE
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