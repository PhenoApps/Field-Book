package com.fieldbook.tracker.traits.formats

import com.fieldbook.tracker.R
import com.fieldbook.tracker.traits.formats.feature.ChartableData
import com.fieldbook.tracker.traits.formats.parameters.AttachMediaParameter
import com.fieldbook.tracker.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.traits.formats.parameters.NameParameter
import com.fieldbook.tracker.traits.formats.parameters.RepeatedMeasureParameter
import com.fieldbook.tracker.traits.formats.parameters.TreeResourceFileParameter

class TreeArchitectureFormat : TraitFormat(
    format = Formats.TREE_ARCHITECTURE,
    defaultLayoutId = R.layout.trait_tree,
    layoutView = null,
    databaseName = "tree architecture",
    nameStringResourceId = R.string.traits_format_tree_architecture,
    iconDrawableResourceId = R.drawable.ic_trait_tree,
    stringNameAux = null,
    NameParameter(),
    DetailsParameter(),
    TreeResourceFileParameter(),
    RepeatedMeasureParameter(),
    AttachMediaParameter(),
), ChartableData

class TreeSummaryFormat : TraitFormat(
    format = Formats.TREE_SUMMARY,
    defaultLayoutId = R.layout.trait_tree_summary,
    layoutView = null,
    databaseName = "tree summary",
    nameStringResourceId = R.string.traits_format_tree_summary,
    iconDrawableResourceId = R.drawable.ic_trait_tree,
    stringNameAux = null,
    NameParameter(),
    DetailsParameter(),
), ChartableData
