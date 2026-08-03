package com.fieldbook.tracker.traits.tree

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.TraitActivity
import com.fieldbook.tracker.dialogs.TreeConstructorDialogFragment
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.formats.tree.ChildRule
import com.fieldbook.tracker.traits.formats.tree.EdgeType
import com.fieldbook.tracker.utilities.TreeDerivedTraitHelper
import com.fieldbook.tracker.utilities.TreeSchemaLoader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
/**
 * Constructor UI on a live [TreeConstructorDialogFragment]: attach study traits,
 * configure edges, persist schema, then create the tree trait with that resource.
 *
 * Uses ActivityScenario (not createAndroidComposeRule) so DialogFragment Compose
 * is not owned/contended by the Compose test rule.
 */
class TreeTraitConstructorUxInstrumentedTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    companion object {
        @JvmStatic
        @BeforeClass
        fun prepareDevice() {
            TreeInstrumentedDevice.prepareHeadlessEmulator()
        }
    }

    @Before
    fun setUp() {
        TreeInstrumentedDevice.ensureWindowFocus()
        TreeInstrumentedSeed.enableExperimentalTraits()
        TreeInstrumentedSeed.ensureDocumentTree(context)
    }

    @Test
    fun soyTreeCarrier_constructor_attachesTraits_and_persistsSchema() {
        val traitName = "soy tree-carrier-test-${System.currentTimeMillis()}"
        val savedLeaf = AtomicReference<String?>(null)

        ActivityScenario.launch(TraitActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                runBlocking {
                    TreeInstrumentedSeed.seedStudyTraits(activity)
                    val names = activity.traitRepo.getTraits().map { it.name }.toSet()
                    assertTrue(
                        "seedStudyTraits missing length/color: $names",
                        "length" in names && "color" in names,
                    )
                }
            }

            val listenLatch = CountDownLatch(1)
            scenario.onActivity { activity ->
                activity.supportFragmentManager.setFragmentResultListener(
                    TreeConstructorDialogFragment.REQUEST_KEY_SCHEMA_SAVED,
                    activity,
                ) { _, bundle ->
                    savedLeaf.set(bundle.getString(TreeConstructorDialogFragment.RESULT_RESOURCE_REF))
                }
                listenLatch.countDown()
            }
            check(listenLatch.await(5, TimeUnit.SECONDS))

            openConstructor(scenario, traitName)

            val blank = context.getString(R.string.tree_schema_blank)
            // Do not use TreeInstrumentedUi.waitForText here — its aggressive scrollables
            // scrolling can overshoot past "Attach trait".
            val opened = device.wait(Until.hasObject(By.text(blank)), 15_000) ||
                device.wait(Until.hasObject(By.text(context.getString(R.string.tree_edit_schema))), 5_000) ||
                device.wait(Until.hasObject(By.text(context.getString(R.string.tree_node_types))), 5_000)
            if (!opened) {
                dumpHierarchy("ctor-open-failed")
                throw AssertionError("Constructor UI missing. See hierarchy dump.")
            }

            // Blank schema already selects Root — scroll to Attach trait and proceed.
            clickAttachTrait()
            attachStudyTraitsInPalette(setOf("length", "color"))

            addNodeType(displayName = "Stem", internalName = "stem", cls = "S")
            clickAttachTrait()
            attachStudyTraitsInPalette(setOf("length", "color"))

            addNodeType(displayName = "Branch", internalName = "branch", cls = "B")
            clickAttachTrait()
            attachStudyTraitsInPalette(
                setOf("length", "color", "flowering date", "branch photo"),
            )

            clickRequired("flowering date")
            clickRequired("branch photo")

            clickTypeChip("Root")
            clickAddConnection(edgeTitle = context.getString(R.string.tree_precedes))
            clickConnectionTarget("Stem")

            clickTypeChip("Stem")
            clickAddConnection(edgeTitle = context.getString(R.string.tree_precedes))
            clickConnectionTarget("Stem")
            clickAddConnection(edgeTitle = context.getString(R.string.tree_bears))
            clickConnectionTarget("Branch")

            scrollToAndClick(context.getString(R.string.tree_save_schema))
            assertTrue(
                "Save confirmation missing",
                device.wait(
                    Until.hasObject(By.text(context.getString(R.string.tree_save_schema))),
                    5_000,
                ),
            )
            val saveButtons = device.findObjects(By.text(context.getString(R.string.tree_save_schema)))
            assertTrue("Expected save + confirm, got ${saveButtons.size}", saveButtons.size >= 2)
            clickClickable(saveButtons.last())

            val resultLatch = CountDownLatch(1)
            // Re-check leaf; listener may already have fired during confirm click.
            if (savedLeaf.get() != null) {
                resultLatch.countDown()
            } else {
                // Poll briefly for async FragmentResult delivery.
                val deadline = System.currentTimeMillis() + 15_000
                while (System.currentTimeMillis() < deadline) {
                    if (savedLeaf.get() != null) {
                        resultLatch.countDown()
                        break
                    }
                    Thread.sleep(100)
                }
            }
            assertTrue(
                "Constructor should dismiss after save",
                device.wait(
                    Until.gone(By.text(context.getString(R.string.tree_edit_schema))),
                    15_000,
                ),
            )
            assertTrue(
                "Constructor did not emit schema leaf via FragmentResult",
                resultLatch.await(1, TimeUnit.SECONDS) || savedLeaf.get() != null,
            )

            val leaf = savedLeaf.get()
            assertNotNull("Constructor did not emit schema leaf via FragmentResult", leaf)
            assertTrue("Empty schema leaf", leaf!!.isNotBlank())

            scenario.onActivity { activity ->
                runBlocking {
                    val repo = activity.traitRepo
                    val next = repo.getMaxPosition() + 1
                    repo.insertTrait(
                        TraitObject().apply {
                            name = traitName
                            alias = traitName
                            synonyms = listOf(traitName)
                            format = "tree architecture"
                            visible = true
                            realPosition = next
                            resourceFile = leaf
                        },
                    )
                    val source = repo.getTraitByName(traitName)!!
                    repo.insertTrait(TreeDerivedTraitHelper.createSummaryTrait(source, next + 1))
                    val summary = repo.getTraitByName("$traitName (summary)")!!
                    TreeDerivedTraitHelper.linkTraits(source, summary)
                    repo.updateTrait(source)
                    repo.updateTrait(summary)

                    val savedSchema = TreeSchemaLoader.load(activity, leaf)
                        ?: throw AssertionError("Saved schema file could not be loaded: $leaf")

                    assertEquals("root", savedSchema.rootType)
                    val rootType = savedSchema.typeOf("root")!!
                    val stemType = savedSchema.typeOf("stem")!!
                    val branchType = savedSchema.typeOf("branch")!!

                    assertEquals(
                        listOf("length", "color"),
                        rootType.traitRefs.sortedBy { it.order }.map { it.traitName },
                    )
                    assertEquals(
                        listOf("length", "color"),
                        stemType.traitRefs.sortedBy { it.order }.map { it.traitName },
                    )
                    assertEquals(
                        listOf("length", "color", "flowering date", "branch photo"),
                        branchType.traitRefs.sortedBy { it.order }.map { it.traitName },
                    )
                    assertEquals(
                        true,
                        branchType.traitRefs.first { it.traitName == "flowering date" }.requiredOverride,
                    )
                    assertEquals(
                        true,
                        branchType.traitRefs.first { it.traitName == "branch photo" }.requiredOverride,
                    )

                    assertEquals(
                        listOf(ChildRule("stem", EdgeType.PRECEDES, "Add Stem")),
                        rootType.allowedChildren,
                    )
                    assertEquals(
                        listOf(
                            ChildRule("stem", EdgeType.PRECEDES, "Add Stem"),
                            ChildRule("branch", EdgeType.BEARS, "Add Branch"),
                        ),
                        stemType.allowedChildren,
                    )
                    assertEquals(emptyList<ChildRule>(), branchType.allowedChildren)
                }
            }
        }
    }

    private fun openConstructor(scenario: ActivityScenario<TraitActivity>, traitName: String) {
        val error = AtomicReference<Throwable?>(null)
        val showing = AtomicBoolean(false)
        scenario.onActivity { activity ->
            try {
                TreeConstructorDialogFragment.show(
                    activity = activity,
                    traitName = traitName,
                    existingResourceRef = null,
                )
                activity.supportFragmentManager.executePendingTransactions()
                val frag = activity.supportFragmentManager.findFragmentByTag("TreeConstructor")
                    as? TreeConstructorDialogFragment
                showing.set(frag?.dialog?.isShowing == true || frag?.isAdded == true)
            } catch (t: Throwable) {
                error.set(t)
            }
        }
        error.get()?.let { throw AssertionError("TreeConstructor.show failed", it) }
        assertTrue("TreeConstructor fragment not showing/added", showing.get())
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun attachStudyTraitsInPalette(traitNames: Set<String>) {
        val title = context.getString(R.string.tree_palette_title)
        assertTrue(
            "Trait palette did not open",
            device.wait(Until.hasObject(By.text(title)), 15_000),
        )
        traitNames.forEach { scrollToAndClick(it) }
        clickLabel(context.getString(R.string.tree_palette_done))
        dismissIme()
    }

    private fun addNodeType(displayName: String, internalName: String, cls: String) {
        dismissIme()
        scrollTowardTop()
        val addDesc = context.getString(R.string.tree_add_node_type)
        val deadline = System.currentTimeMillis() + 15_000
        var found = false
        while (System.currentTimeMillis() < deadline) {
            if (device.hasObject(By.desc(addDesc))) {
                clickClickable(device.findObject(By.desc(addDesc)))
                found = true
                break
            }
            swipeDownSlightly()
            Thread.sleep(200)
        }
        assertTrue("Missing desc '$addDesc'", found)
        assertTrue(
            "Add node type dialog missing",
            TreeInstrumentedUi.waitForText(context, R.string.tree_display_name, 10_000),
        )

        // Prefer resource-id testTags (requires testTagsAsResourceId on Constructor host).
        typeIntoRes("tree_add_type_display", displayName)
        // Display onChange auto-fills internal name when blank ("Stem" → "stem").
        if (!device.wait(Until.hasObject(By.text(internalName)), 2_000)) {
            typeIntoRes("tree_add_type_name", internalName)
        }
        typeIntoRes("tree_add_type_class", cls)

        val addLabel = context.getString(R.string.tree_add)
        assertTrue(
            "Add button not enabled — typed values did not reach Compose state",
            device.wait(Until.hasObject(By.text(addLabel).enabled(true)), 8_000),
        )
        clickClickable(device.findObject(By.text(addLabel).enabled(true)))
        assertTrue(
            "Added type chip missing: $displayName ($cls)",
            device.wait(Until.hasObject(By.textContains("$displayName ($cls)")), 10_000) ||
                device.wait(Until.hasObject(By.descContains("Node type: $displayName")), 5_000),
        )
        dismissIme()
    }

    private fun typeIntoRes(resourceId: String, value: String) {
        val pkg = context.packageName
        val found = device.wait(Until.hasObject(By.res(resourceId)), 5_000) ||
            device.wait(Until.hasObject(By.res(pkg, resourceId)), 3_000)
        assertTrue("Missing field res '$resourceId'", found)
        val node = device.findObject(By.res(resourceId))
            ?: device.findObject(By.res(pkg, resourceId))!!
        val bounds = node.visibleBounds
        device.click(bounds.centerX(), bounds.centerY())
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        Thread.sleep(200)

        // Clear existing value (e.g. suggested class letter).
        runCatching { node.setText("") }
        runCatching { device.findObject(By.focused(true))?.setText("") }

        // KeyCharacterMap injection updates Compose controlled TextFields more reliably
        // than UiObject2.setText / `input text` on this emulator.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val keyMap = android.view.KeyCharacterMap.load(android.view.KeyCharacterMap.VIRTUAL_KEYBOARD)
        val events = keyMap.getEvents(value.toCharArray())
        if (events != null) {
            for (event in events) {
                instrumentation.sendKeySync(event)
            }
        } else {
            instrumentation.sendStringSync(value)
        }
        instrumentation.waitForIdleSync()
        Thread.sleep(200)

        val visible = device.hasObject(By.text(value)) ||
            device.findObject(By.res(resourceId))?.text == value ||
            device.findObject(By.res(pkg, resourceId))?.text == value ||
            device.findObject(By.focused(true))?.text == value
        assertTrue(
            "Failed to enter '$value' into res='$resourceId' (nodeText='${node.text}')",
            visible || device.wait(Until.hasObject(By.text(value)), 2_000),
        )
    }

    /**
     * Attach trait sits below several OutlinedTextFields; Compose verticalScroll is often
     * invisible to UiAutomator [By.scrollable], so prefer testTag / desc + vertical scroll.
     */
    private fun clickAttachTrait() {
        val label = context.getString(R.string.tree_attach_trait)
        dismissIme()
        fun findAttach() =
            device.findObject(By.res("tree_attach_trait"))
                ?: device.findObject(By.desc(label))
                ?: device.findObject(By.text(label))

        findAttach()?.let { hit ->
            runCatching {
                clickClickable(hit)
                return
            }
        }
        // Only scroll if not already visible — full reset is slow and flaky.
        scrollTowardTop()
        val deadline = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < deadline) {
            val hit = findAttach()
            if (hit != null) {
                runCatching {
                    clickClickable(hit)
                    return
                }
            }
            scrollConstructor(Direction.DOWN)
            Thread.sleep(100)
        }
        dumpHierarchy("attach-trait-missing")
        assertTrue("Expected '$label' on screen (desc/text)", false)
    }

    private fun clickConnectionTarget(displayName: String) {
        val desc = "Connect to $displayName"
        assertTrue(
            "Connection picker missing target '$displayName'",
            device.wait(Until.hasObject(By.desc(desc)), 8_000),
        )
        clickClickable(device.findObject(By.desc(desc)))
        // Connection rule label fields steal focus and open the IME over Bears.
        dismissIme()
        Thread.sleep(200)
    }

    private fun clickAddConnection(edgeTitle: String) {
        val desc = "Add connection ($edgeTitle)"
        dismissIme()
        scrollTowardTop()
        val deadline = System.currentTimeMillis() + 25_000
        while (System.currentTimeMillis() < deadline) {
            val byDesc = device.findObject(By.desc(desc))
            if (byDesc != null) {
                runCatching {
                    clickClickable(byDesc)
                    if (device.wait(Until.hasObject(By.descContains("Connect to")), 5_000)) return
                }
            }
            scrollConstructor(Direction.DOWN, 0.4f)
            Thread.sleep(120)
        }
        dumpHierarchy("add-connection-missing")
        assertTrue("Add connection for '$edgeTitle' missing", false)
    }

    private fun clickRequired(traitName: String) {
        val description = "Required: $traitName"
        dismissIme()
        // Trait rows sit just under Attach trait — start near top of the type section.
        scrollTowardTop()
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            val byDesc = device.findObject(By.desc(description))
            if (byDesc != null) {
                runCatching {
                    clickClickable(byDesc)
                    return
                }
            }
            // Fallback: trait label visible but star semantics not yet — scroll slightly.
            if (device.hasObject(By.text(traitName))) {
                scrollConstructor(Direction.DOWN, 0.2f)
            } else {
                scrollConstructor(Direction.DOWN)
            }
            Thread.sleep(120)
        }
        dumpHierarchy("required-missing-$traitName")
        assertTrue("Missing desc '$description'", false)
    }

    private fun clickDesc(description: String) {
        val selector = By.desc(description)
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            if (device.hasObject(selector)) {
                clickClickable(device.findObject(selector))
                return
            }
            scrollConstructor(Direction.DOWN)
            Thread.sleep(150)
        }
        dumpHierarchy("desc-missing")
        assertTrue("Missing desc '$description'", false)
    }

    private fun clickLabel(label: String) {
        scrollToAndClick(label)
    }

            // Prefer contentDescription "Node type: Display (C)"; fall back to visible chip text.
    private fun clickTypeChip(displayName: String) {
        dismissIme()
        val descPrefix = "Node type: $displayName"
        val deadline = System.currentTimeMillis() + 20_000
        var towardTop = true
        while (System.currentTimeMillis() < deadline) {
            val byDesc = device.findObjects(By.descContains(descPrefix)).firstOrNull()
            if (byDesc != null) {
                clickClickable(byDesc)
                return
            }
            val byText = device.findObjects(By.textContains(displayName)).firstOrNull {
                val t = it.text.orEmpty()
                t.contains("(") && (t.startsWith(displayName) || t.contains("$displayName ("))
            }
            if (byText != null) {
                clickClickable(byText)
                return
            }
            scrollConstructor(if (towardTop) Direction.UP else Direction.DOWN)
            towardTop = !towardTop
            Thread.sleep(150)
        }
        dumpHierarchy("type-chip-missing-$displayName")
        assertTrue("Expected type chip '$displayName' on screen", false)
    }

    private fun clickClickable(obj: androidx.test.uiautomator.UiObject2?) {
        requireNotNull(obj) { "null UiObject2" }
        repeat(4) { attempt ->
            try {
                var target = obj
                var parent = runCatching { obj.parent }.getOrNull()
                var hops = 0
                while (target != null &&
                    runCatching { !target.isClickable }.getOrDefault(false) &&
                    parent != null &&
                    hops < 6
                ) {
                    target = parent
                    parent = runCatching { parent!!.parent }.getOrNull()
                    hops++
                }
                (target ?: obj).click()
                return
            } catch (_: androidx.test.uiautomator.StaleObjectException) {
                if (attempt == 3) throw AssertionError("Stale UI object while clicking")
                Thread.sleep(200)
            }
        }
    }

    private fun scrollToAndClick(label: String) {
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            val textHit = device.findObject(By.text(label))
            if (textHit != null) {
                runCatching { clickClickable(textHit) }.onSuccess { return }
            }
            val descHit = device.findObject(By.desc(label))
            if (descHit != null) {
                runCatching { clickClickable(descHit) }.onSuccess { return }
            }
            scrollConstructor(Direction.DOWN)
            Thread.sleep(150)
        }
        dumpHierarchy("label-missing-$label")
        assertTrue("Expected '$label' on screen", false)
    }

    private fun dismissIme() {
        // Click a stable header label to clear EditText focus / hide soft keyboard without
        // pressing Back (Back would dismiss the Constructor dialog).
        runCatching {
            device.findObject(By.text(context.getString(R.string.tree_edit_schema)))?.click()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        Thread.sleep(150)
    }

    /** Prefer the tallest scrollable (main form); drag inside its bounds (Compose scroll() is flaky). */
    private fun scrollConstructor(direction: Direction, percent: Float = 0.8f) {
        val scrollables = device.findObjects(By.scrollable(true))
        val main = scrollables.maxByOrNull {
            runCatching { it.visibleBounds.height() }.getOrDefault(0)
        }
        if (main != null) {
            val b = runCatching { main.visibleBounds }.getOrNull()
            if (b != null && b.height() > 200) {
                val x = b.centerX()
                val margin = (b.height() * 0.12).toInt().coerceAtLeast(60)
                if (direction == Direction.DOWN) {
                    device.swipe(x, b.bottom - margin, x, b.top + margin, 28)
                } else {
                    device.swipe(x, b.top + margin, x, b.bottom - margin, 28)
                }
            }
            runCatching { main.scroll(direction, percent) }
            return
        }
        if (direction == Direction.DOWN) swipeUp() else swipeDownSlightly()
    }

    private fun swipeUp() {
        val w = device.displayWidth
        val h = device.displayHeight
        device.swipe(w / 2, (h * 0.72).toInt(), w / 2, (h * 0.28).toInt(), 24)
    }

    private fun swipeDownSlightly() {
        val w = device.displayWidth
        val h = device.displayHeight
        device.swipe(w / 2, (h * 0.35).toInt(), w / 2, (h * 0.65).toInt(), 18)
    }

    private fun scrollTowardTop() {
        repeat(4) {
            runCatching { scrollConstructor(Direction.UP, 1f) }
        }
    }

    private fun dumpHierarchy(tag: String) {
        runCatching {
            val safe = tag.replace(Regex("[^A-Za-z0-9._-]"), "_").take(60)
            device.dumpWindowHierarchy(File("/sdcard/Download/ctor-$safe.xml"))
            val local = File(context.cacheDir, "ctor-$safe.xml")
            device.dumpWindowHierarchy(local)
        }
    }
}
