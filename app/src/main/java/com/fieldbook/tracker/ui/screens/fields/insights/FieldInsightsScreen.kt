package com.fieldbook.tracker.ui.screens.fields.insights

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.viewmodels.FieldInsightsUiState
import com.fieldbook.tracker.database.viewmodels.FieldInsightsViewModel
import com.fieldbook.tracker.insights.FieldInsightsData
import com.fieldbook.tracker.insights.InsightTrait
import com.fieldbook.tracker.insights.InsightTraitKind
import com.fieldbook.tracker.ui.components.appBar.AppBar
import com.fieldbook.tracker.ui.theme.AppTheme
import kotlinx.coroutines.launch

enum class InsightTab(@StringRes val title: Int) {
    SUMMARY(R.string.field_insights_tab_summary),
    PROGRESS(R.string.field_insights_tab_progress),
    STATISTICS(R.string.field_insights_tab_statistics),
    MAP(R.string.field_insights_tab_map),
    COMPARE(R.string.field_insights_tab_compare),
    CORRELATIONS(R.string.field_insights_tab_correlations),
}

@Composable
fun FieldInsightsScreen(
    fieldId: Int,
    viewModel: FieldInsightsViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val palette = rememberInsightColors()

    LaunchedEffect(fieldId) {
        viewModel.load(fieldId)
    }

    val title = (uiState as? FieldInsightsUiState.Success)?.data?.fieldName
        ?: stringResource(R.string.field_insights_title)

    CompositionLocalProvider(LocalInsightColors provides palette) {
        Scaffold(
            containerColor = AppTheme.colors.background,
            topBar = {
                AppBar(
                    title = title,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(R.drawable.arrow_left),
                                contentDescription = stringResource(R.string.appbar_back)
                            )
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (val state = uiState) {
                    is FieldInsightsUiState.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = AppTheme.colors.interactive.accent
                        )
                    }

                    is FieldInsightsUiState.Error -> {
                        InsightMessage(stringResource(state.messageRes))
                    }

                    is FieldInsightsUiState.Success -> {
                        if (state.data.observations.isEmpty() && state.data.naObservations.isEmpty()) {
                            InsightMessage(stringResource(R.string.field_insights_no_data))
                        } else {
                            FieldInsightsContent(state.data)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tabs over one loaded field. The selected trait is shared by the tabs that chart one trait, so
 * moving between them keeps looking at the same thing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldInsightsContent(data: FieldInsightsData) {

    // The pager holds which tab is showing, so swiping and the tab row stay in step; its state
    // is saved, so the tab survives rotation.
    val tabs = InsightTab.entries
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    val tabIndex = pagerState.currentPage

    var traitId by rememberSaveable {
        mutableStateOf(data.traits.firstOrNull { it.kind != InsightTraitKind.OTHER }?.id ?: data.traits.first().id)
    }
    // Progress shares the selected trait with the other tabs, and can also show all traits at
    // once; that choice is Progress's own, so it leaves the other tabs' trait alone.
    var progressAllTraits by rememberSaveable { mutableStateOf(true) }
    var pairFirstId by rememberSaveable { mutableStateOf<String?>(null) }
    var pairSecondId by rememberSaveable { mutableStateOf<String?>(null) }
    var rowAttribute by rememberSaveable { mutableStateOf(data.defaultRowAttribute) }
    var columnAttribute by rememberSaveable { mutableStateOf(data.defaultColumnAttribute) }

    val trait = data.trait(traitId) ?: data.traits.first()
    val relationshipPair = pairFirstId?.let { first -> pairSecondId?.let { second -> first to second } }

    fun open(tab: InsightTab, trait: String? = null) {
        trait?.let { traitId = it }
        scope.launch { pagerState.animateScrollToPage(tab.ordinal) }
    }

    // Back returns to Summary before it leaves the screen. Only registered off Summary, so from
    // there back falls through to the activity as usual.
    BackHandler(enabled = tabIndex != InsightTab.SUMMARY.ordinal) {
        open(InsightTab.SUMMARY)
    }

    Column(modifier = Modifier.fillMaxSize()) {

        PrimaryScrollableTabRow(
            selectedTabIndex = tabIndex,
            containerColor = AppTheme.colors.background,
            contentColor = AppTheme.colors.text.primary,
            edgePadding = 8.dp,
            indicator = {
                TabRowDefaults.PrimaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabIndex, matchContentSize = true),
                    width = Dp.Unspecified,
                    color = AppTheme.colors.interactive.accent
                )
            }
        ) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = index == tabIndex,
                    onClick = { open(tab) },
                    text = { Text(stringResource(tab.title)) },
                    selectedContentColor = AppTheme.colors.text.primary,
                    unselectedContentColor = AppTheme.colors.text.secondary
                )
            }
        }

        // Pages fill the space under the tabs. The map and the correlation matrix scroll sideways
        // themselves; a swipe on them scrolls them first and turns the page once they reach
        // their edge.
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { tabs[it] }
        ) { page ->
            when (tabs[page]) {
                InsightTab.SUMMARY -> SummaryTab(
                    data = data,
                    onOpenTrait = { open(InsightTab.STATISTICS, it) }
                )

                InsightTab.PROGRESS -> ProgressTab(
                    data = data,
                    trait = if (progressAllTraits) null else trait,
                    onTraitChange = { selected ->
                        progressAllTraits = selected == null
                        selected?.let { traitId = it.id }
                    }
                )

                InsightTab.STATISTICS -> StatisticsTab(
                    data = data,
                    trait = trait,
                    onTraitChange = {
                        traitId = it.id
                    }
                )

                InsightTab.MAP -> FieldMapTab(
                    data = data,
                    trait = trait,
                    rowAttribute = rowAttribute,
                    columnAttribute = columnAttribute,
                    onTraitChange = {
                        traitId = it.id
                    },
                    onRowAttributeChange = {
                        rowAttribute = it
                    },
                    onColumnAttributeChange = {
                        columnAttribute = it
                    }
                )

                InsightTab.COMPARE -> CompareTab(
                    data = data,
                    trait = trait,
                    onTraitChange = {
                        traitId = it.id
                    }
                )

                InsightTab.CORRELATIONS -> CorrelationsTab(
                    data = data,
                    selectedPair = relationshipPair,
                    onPairChange = {
                        pairFirstId = it.first
                        pairSecondId = it.second
                    }
                )
            }
        }
    }
}

/** The trait picker shared by the single-trait tabs. */
@Composable
fun TraitDropdown(
    traits: List<InsightTrait>,
    selected: InsightTrait,
    onSelect: (InsightTrait) -> Unit,
    modifier: Modifier = Modifier
) {
    InsightDropdown(
        label = stringResource(R.string.field_insights_trait),
        selectedText = selected.name,
        options = traits,
        optionText = { it.name },
        onSelect = onSelect,
        modifier = modifier
    )
}
