package com.fieldbook.tracker.screenshots

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.fieldbook.tracker.traits.composables.constructor.AddTypeDialogFields
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.ThemedActivity
import com.fieldbook.tracker.traits.composables.constructor.TreeConstructorScreen
import com.fieldbook.tracker.traits.formats.tree.TreeSchema

/** Trait create-flow chrome + Constructor fullscreen panel. */
class TreeConstructorIntegratedHarness : AppCompatActivity() {

    enum class Step {
        BLANK_ROOT,
        ADD_TYPE_DIALOG,
        STEM_SELECTED,
        BRANCH_PLUS_CONNECTIONS,
        PALETTE_OPEN_ON_BRANCH,
        TRAITS_ATTACHED,
        ALL_SAMPLE_TRAITS_ATTACHED,
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemedActivity.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tree_constructor_integrated)

        val step = intent.getStringExtra(EXTRA_STEP)?.let { runCatching { Step.valueOf(it) }.getOrNull() }
            ?: Step.BLANK_ROOT
        val fullLength = intent.getBooleanExtra(IntegratedScreenshotCapture.EXTRA_FULL_LENGTH, true)

        setupToolbar()
        if (fullLength) applyConstructorFullLengthChrome()

        findViewById<ComposeView>(R.id.constructor_compose_host).setContent {
            ScreenshotAppTheme {
                Surface(
                    modifier = if (fullLength) {
                        Modifier.fillMaxWidth().wrapContentHeight()
                    } else {
                        Modifier.fillMaxSize()
                    },
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    ConstructorPanel(step, fullLength)
                }
            }
        }
    }

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = getString(R.string.traits_dialog_create)
            setDisplayHomeAsUpEnabled(true)
        }
    }

    companion object {
        const val EXTRA_STEP = "step"
    }
}

@Composable
private fun ConstructorPanel(step: TreeConstructorIntegratedHarness.Step, expandVertically: Boolean) {
    val traits = when (step) {
        TreeConstructorIntegratedHarness.Step.ALL_SAMPLE_TRAITS_ATTACHED ->
            TreeScreenshotFixtures.traitSampleStudyTraits()
        else -> TreeScreenshotFixtures.studyTraits()
    }
    val schema = schemaFor(step)
    when (step) {
        TreeConstructorIntegratedHarness.Step.ADD_TYPE_DIALOG -> AddTypeDialogBody()
        else -> TreeConstructorScreen(
            initialSchema = schema,
            availableTraits = traits,
            traitNameHint = "soy tree-carrier",
            onSave = { _, _ -> },
            onCancel = {},
            initialSelectedTypeName = selectedType(step),
            initialShowPalette = step == TreeConstructorIntegratedHarness.Step.PALETTE_OPEN_ON_BRANCH,
            expandVertically = expandVertically,
        )
    }
}

@Composable
private fun AddTypeDialogBody() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Add node type", style = MaterialTheme.typography.titleLarge)
        AddTypeDialogFields(
            display = "Stem",
            onDisplayChange = {},
            name = "stem",
            onNameChange = {},
            cls = "S",
            onClsChange = {},
        )
    }
}

private fun schemaFor(step: TreeConstructorIntegratedHarness.Step): TreeSchema = when (step) {
    TreeConstructorIntegratedHarness.Step.BLANK_ROOT,
    TreeConstructorIntegratedHarness.Step.ADD_TYPE_DIALOG,
    -> TreeScreenshotFixtures.blankRoot()
    TreeConstructorIntegratedHarness.Step.STEM_SELECTED -> TreeScreenshotFixtures.withStem()
    TreeConstructorIntegratedHarness.Step.BRANCH_PLUS_CONNECTIONS,
    TreeConstructorIntegratedHarness.Step.PALETTE_OPEN_ON_BRANCH,
    -> TreeScreenshotFixtures.withStemAndBranch()
    TreeConstructorIntegratedHarness.Step.TRAITS_ATTACHED -> TreeScreenshotFixtures.finishedSchema()
    TreeConstructorIntegratedHarness.Step.ALL_SAMPLE_TRAITS_ATTACHED ->
        TreeScreenshotFixtures.withAllSampleTraitsAttached()
}

private fun selectedType(step: TreeConstructorIntegratedHarness.Step): String? = when (step) {
    TreeConstructorIntegratedHarness.Step.STEM_SELECTED,
    TreeConstructorIntegratedHarness.Step.BRANCH_PLUS_CONNECTIONS,
    -> "stem"
    TreeConstructorIntegratedHarness.Step.PALETTE_OPEN_ON_BRANCH,
    TreeConstructorIntegratedHarness.Step.TRAITS_ATTACHED,
    TreeConstructorIntegratedHarness.Step.ALL_SAMPLE_TRAITS_ATTACHED,
    -> "branch"
    else -> null
}
