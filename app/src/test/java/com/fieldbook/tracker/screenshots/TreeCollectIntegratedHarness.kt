package com.fieldbook.tracker.screenshots

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.ThemedActivity
import com.fieldbook.tracker.adapters.InfoBarAdapter
import com.fieldbook.tracker.objects.InfoBarModel
import com.fieldbook.tracker.traits.composables.collect.TreeCollectScreen
import com.fieldbook.tracker.traits.composables.collect.TreeCollectStrings
import com.fieldbook.tracker.traits.composables.collect.OverviewMode
import com.fieldbook.tracker.traits.composables.collect.TreeOverviewSheet
import com.fieldbook.tracker.traits.formats.tree.TreeSummary
import com.fieldbook.tracker.ui.BottomToolbar
import com.fieldbook.tracker.ui.BottomToolbarListener

/** CollectActivity chrome (toolbar, infobar, trait/range boxes, bottom bar) + tree panel. */
class TreeCollectIntegratedHarness : AppCompatActivity() {

    enum class Step {
        STEM_ADD_BUTTONS,
        BRANCH_DATE_FIELDS,
        OVERVIEW_SUMMARY,
        LOCKED_NAVIGATE_ONLY,
        DEEP_BREADCRUMB,
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemedActivity.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tree_collect_integrated)

        val step = intent.getStringExtra(EXTRA_STEP)?.let { runCatching { Step.valueOf(it) }.getOrNull() }
            ?: Step.STEM_ADD_BUTTONS
        val fullLength = intent.getBooleanExtra(IntegratedScreenshotCapture.EXTRA_FULL_LENGTH, true)

        setupToolbar()
        setupInfoBar()
        setupRangeBox()
        setupBottomToolbar()
        if (fullLength) applyCollectFullLengthChrome()

        when (step) {
            Step.OVERVIEW_SUMMARY -> bindOverviewSummary()
            Step.STEM_ADD_BUTTONS, Step.BRANCH_DATE_FIELDS,
            Step.LOCKED_NAVIGATE_ONLY, Step.DEEP_BREADCRUMB,
            -> {
                setupTraitBox(traitName = "soy tree-carrier")
                bindTreeCollect(step, fullLength)
            }
        }
    }

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = "field1"
            setDisplayHomeAsUpEnabled(true)
        }
    }

    private fun setupInfoBar() {
        val rv = findViewById<RecyclerView>(R.id.act_collect_infobar_rv)
        rv.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        InfoBarAdapter(this).also { adapter ->
            rv.adapter = adapter
            adapter.submitList(
                listOf(
                    InfoBarModel("row", "1", false),
                    InfoBarModel("col", "1", false),
                    InfoBarModel("plot", "sample1", false),
                ),
            )
        }
    }

    private fun setupTraitBox(traitName: String) {
        findViewById<TextView>(R.id.traitTypeTv).text = traitName
        findViewById<TextView>(R.id.traitDetails).text = getString(R.string.main_trait_details)
        findViewById<View>(R.id.traitsStatusBarRv).visibility = View.GONE
    }

    private fun setupRangeBox() {
        findViewById<TextView>(R.id.primaryNameTv).text = "row:"
        findViewById<TextView>(R.id.secondaryNameTv).text = "col:"
        findViewById<TextView>(R.id.primaryIdTv).text = "1"
        findViewById<TextView>(R.id.secondaryIdTv).text = "1"
        findViewById<View>(R.id.plotsProgressBar).visibility = View.GONE
    }

    private fun setupBottomToolbar() {
        val noop = object : BottomToolbarListener {
            override fun onMissing() = Unit
            override fun onBarcode() = Unit
            override fun onDelete() = Unit
            override fun onDeleteLong() = Unit
            override fun onMediaOption(option: com.fieldbook.tracker.ui.MediaOption) = Unit
        }
        findViewById<ComposeView>(R.id.toolbarBottomCompose).setContent {
            ScreenshotAppTheme { BottomToolbar(noop, isMediaEnabled = false) }
        }
    }

    private fun bindOverviewSummary() {
        setupTraitBox(traitName = "soy tree-carrier")
        val plant = TreeScreenshotFixtures.collectSummaryPlant()
        val summary = TreeSummary.compute(plant.root, plant.schema)
        val traitsByName = TreeScreenshotFixtures.studyTraits().associateBy { it.name }
        // Fixed host height mirrors Collect nearly-full Overview sheet (~95%); List/Graph use weight.
        findViewById<ComposeView>(R.id.tree_compose_host).setContent {
            ScreenshotAppTheme {
                Surface(modifier = Modifier.fillMaxWidth().height(900.dp).padding(16.dp)) {
                    TreeOverviewSheet(
                        root = plant.root,
                        schema = plant.schema,
                        currentNodeId = plant.root.id,
                        issues = emptyList(),
                        summary = summary,
                        resolveTrait = { traitsByName[it] },
                        onJumpTo = {},
                        initialMode = OverviewMode.Graph,
                        modifier = Modifier.fillMaxHeight(),
                    )
                }
            }
        }
    }

    private fun bindTreeCollect(step: Step, expandVertically: Boolean) {
        val fixture = when (step) {
            Step.DEEP_BREADCRUMB -> TreeScreenshotFixtures.collectDeepBreadcrumb()
            else -> TreeScreenshotFixtures.collectAfterThreeStemsAndBranch()
        }
        // Seed the portable node photo so preview loads a real bitmap (not grey placeholder).
        if (step == Step.BRANCH_DATE_FIELDS) {
            TreeScreenshotFixtures.ensureBranchPhotoFile(this)
        }
        val traits = TreeScreenshotFixtures.studyTraits()
        val nodeId = when (step) {
            Step.STEM_ADD_BUTTONS, Step.LOCKED_NAVIGATE_ONLY -> fixture.stem2Id
            Step.BRANCH_DATE_FIELDS -> fixture.branch1Id
            Step.DEEP_BREADCRUMB -> fixture.deepLeafId
            else -> fixture.root.id
        }
        val locked = step == Step.LOCKED_NAVIGATE_ONLY
        val strings = TreeCollectStrings(
            noIssues = getString(R.string.tree_no_issues),
            childrenTitle = { getString(R.string.tree_children, it) },
            overview = getString(R.string.tree_overview),
            ascend = getString(R.string.tree_ascend),
            ascendDescription = getString(R.string.tree_ascend_description),
            lockedBanner = getString(R.string.tree_locked_navigate_only),
            breadcrumbEllipsisDescription = getString(R.string.tree_breadcrumb_ellipsis),
        )
        findViewById<ComposeView>(R.id.tree_compose_host).setContent {
            ScreenshotAppTheme {
                TreeCollectScreen(
                    schema = fixture.schema,
                    root = fixture.root,
                    currentNodeId = nodeId,
                    issues = emptyList(),
                    locked = locked,
                    onNavigate = {},
                    onAddChild = {},
                    onDeleteChild = {},
                    onTraitChange = { _, _ -> },
                    onRequestPhoto = {},
                    onShowOverview = {},
                    resolveTrait = { name -> traits.firstOrNull { it.name == name } },
                    strings = strings,
                    expandVertically = expandVertically,
                )
            }
        }
    }

    companion object {
        const val EXTRA_STEP = "step"
    }
}
