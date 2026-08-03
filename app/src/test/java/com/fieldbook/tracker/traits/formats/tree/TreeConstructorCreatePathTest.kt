package com.fieldbook.tracker.traits.formats.tree

import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.composables.constructor.attachTraitByName
import com.fieldbook.tracker.traits.composables.constructor.blankSchema
import com.fieldbook.tracker.traits.composables.constructor.defaultSoybeanSchema
import com.fieldbook.tracker.traits.composables.constructor.filterAttachableStudyTraits
import com.fieldbook.tracker.traits.composables.constructor.isUnsupportedTreePaletteFormat
import com.fieldbook.tracker.traits.formats.Formats
import com.fieldbook.tracker.traits.formats.parameters.AttachMediaParameter
import com.fieldbook.tracker.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.traits.formats.parameters.NameParameter
import com.fieldbook.tracker.traits.formats.parameters.RepeatedMeasureParameter
import com.fieldbook.tracker.traits.formats.parameters.TreeResourceFileParameter
import com.fieldbook.tracker.utilities.TreeDerivedTraitHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Create-path contracts for soy tree-carrier:
 * Tree Architecture plugs into [com.fieldbook.tracker.dialogs.NewTraitDialog],
 * [com.fieldbook.tracker.traits.formats.parameters.TreeResourceFileParameter] opens
 * [com.fieldbook.tracker.dialogs.TreeConstructorDialogFragment] on blankSchema (not soybean),
 * and Constructor attaches existing study traits by name (TraitRef), not new definitions.
 */
class TreeConstructorCreatePathTest {

    @Test
    fun treeArchitectureFormat_reusesNewTraitDialogParameters() {
        val params = Formats.TREE_ARCHITECTURE.getTraitFormatDefinition().parameters
        assertTrue(params.any { it is NameParameter })
        assertTrue(params.any { it is DetailsParameter })
        assertTrue(params.any { it is TreeResourceFileParameter })
        assertTrue(params.any { it is RepeatedMeasureParameter })
        assertTrue(params.any { it is AttachMediaParameter })
        assertEquals(5, params.size)
    }

    @Test
    fun treeSummaryFormat_isNotManualCreate_onlyNameAndDetails() {
        val params = Formats.TREE_SUMMARY.getTraitFormatDefinition().parameters
        assertTrue(params.any { it is NameParameter })
        assertTrue(params.any { it is DetailsParameter })
        assertFalse(params.any { it is TreeResourceFileParameter })
        assertEquals(2, params.size)
        assertFalse(Formats.TREE_SUMMARY in Formats.getCreatableExperimentalFormats())
    }

    @Test
    fun blankSchema_isNotSoybeanSample_constructorDefaultPath() {
        val blank = blankSchema()
        val sample = defaultSoybeanSchema()

        assertNotEquals(blank.id, sample.id)
        assertEquals("tree_blank_v1", blank.id)
        assertEquals("soy_arch_v1", sample.id)
        assertEquals("root", blank.rootType)
        assertEquals("plant", sample.rootType)
        assertEquals(1, blank.nodeTypes.size)
        assertTrue(blank.nodeTypes.single().traitRefs.isEmpty())

        assertTrue(sample.nodeTypes.any { it.name == "internode" })
        assertTrue(sample.nodeTypes.any { it.name == "pod" })
        assertFalse(sample.nodeTypes.any { it.name == "stem" })
        assertFalse(sample.nodeTypes.any { it.name == "branch" })
        assertFalse(blank.nodeTypes.any { it.name == "plant" })
    }

    @Test
    fun defaultSoybeanSchema_topologyOnly_noPredefinedTraitRefs() {
        // Soybean sample must not invent TraitRefs the study does not have.
        val sample = defaultSoybeanSchema()
        assertTrue(
            "Soybean sample must ship empty traitRefs; Attach real study traits only",
            sample.nodeTypes.all { it.traitRefs.isEmpty() },
        )
    }

    @Test
    fun shippedSoybeanAsset_topologyOnly_noPredefinedTraitRefs() {
        val asset = java.io.File("src/main/assets/trait/tree_soy_arch_sample.trt")
        assertTrue("Missing shipped soybean asset at ${asset.absolutePath}", asset.isFile)
        val schema = TreeCodec.decodeSchema(asset.readText())
        assertEquals("soy_arch_v1", schema.id)
        assertTrue(
            "Shipped tree_soy_arch_sample.trt must have empty traitRefs on all node types",
            schema.nodeTypes.all { it.traitRefs.isEmpty() },
        )
    }

    @Test
    fun traitRef_attachByName_fromStudyTraitObjectsOnly() {
        val studyTraits = studyTraits()
        val studyNames = studyTraits.map { it.name }.toSet()

        var root = NodeTypeDef(name = "root", displayName = "Root", cls = "R")
        for (trait in studyTraits.filter { it.name == "length" || it.name == "color" }) {
            assertFalse(isUnsupportedTreePaletteFormat(trait.format))
            root = root.attachTraitByName(trait.name)
        }

        assertEquals(listOf("length", "color"), root.traitNames())
        assertTrue(root.traitRefs.all { it.traitName in studyNames })
        // Idempotent: palette "already attached" path does not duplicate.
        assertEquals(root, root.attachTraitByName("length"))
    }

    @Test
    fun soyTreeCarrier_attachContract_refsExistingStudyTraitNames() {
        val studyTraits = setOf("length", "color", "flowering date", "branch photo")
        val schema = soyTreeCarrierSchema()

        assertTrue(TreeSchemaValidator.validate(schema).isEmpty())

        val allRefs = schema.nodeTypes.flatMap { it.traitRefs.map { ref -> ref.traitName } }.toSet()
        assertTrue(studyTraits.containsAll(allRefs))

        assertEquals(listOf("length", "color"), schema.typeOf("root")!!.traitNames())
        assertEquals(listOf("length", "color"), schema.typeOf("stem")!!.traitNames())
        assertEquals(
            listOf("length", "color", "flowering date", "branch photo"),
            schema.typeOf("branch")!!.traitNames(),
        )
    }

    @Test
    fun branchOnly_floweringDateAndBranchPhotoRefs() {
        val schema = soyTreeCarrierSchema()
        val branchOnly = setOf("flowering date", "branch photo")

        for (typeName in listOf("root", "stem")) {
            val names = schema.typeOf(typeName)!!.traitNames().toSet()
            assertTrue(names.intersect(branchOnly).isEmpty())
        }
        assertTrue(schema.typeOf("branch")!!.traitNames().containsAll(branchOnly))
    }

    @Test
    fun buildSoyTreeCarrier_fromBlank_viaAttachByName() {
        val study = studyTraits().associateBy { it.name }
        var schema = blankSchema().copy(
            id = "soy_tree_carrier_v1",
            name = "soy tree-carrier",
        )

        schema = schema.copy(
            nodeTypes = listOf(
                schema.typeOf("root")!!.copy(
                    allowedChildren = listOf(ChildRule("stem", EdgeType.PRECEDES, "Add Stem")),
                ).attachTraitByName(study.getValue("length").name)
                    .attachTraitByName(study.getValue("color").name),
                NodeTypeDef(
                    name = "stem",
                    displayName = "Stem",
                    cls = "S",
                    allowedChildren = listOf(
                        ChildRule("stem", EdgeType.PRECEDES, "Add Stem"),
                        ChildRule("branch", EdgeType.BEARS, "Add Branch"),
                    ),
                ).attachTraitByName("length").attachTraitByName("color"),
                NodeTypeDef(name = "branch", displayName = "Branch", cls = "B")
                    .attachTraitByName("length")
                    .attachTraitByName("color")
                    .attachTraitByName("flowering date")
                    .attachTraitByName("branch photo"),
            ),
        )

        assertNotEquals(defaultSoybeanSchema().id, schema.id)
        assertEquals(
            listOf("length", "color", "flowering date", "branch photo"),
            schema.typeOf("branch")!!.traitNames(),
        )
        assertTrue(TreeSchemaValidator.validate(schema).isEmpty())
    }

    @Test
    fun summaryAutoName_usesNameSummarySuffix() {
        val source = TraitObject().apply {
            id = "42"
            name = "soy tree-carrier"
            alias = name
            format = Formats.TREE_ARCHITECTURE.getDatabaseName()
        }
        val summary = TreeDerivedTraitHelper.createSummaryTrait(source, position = 3)
        assertEquals("soy tree-carrier (summary)", summary.name)
        assertEquals(Formats.TREE_SUMMARY.getDatabaseName(), summary.format)
        assertFalse(summary.visible) // export-only; Collect uses getVisibleTraits
    }

    @Test
    fun palette_listsStudyTraitObjects_noParallelTraitDefinitionUi() {
        // Constructor palette input is List<TraitObject> from DataHelper.allTraitObjects.
        // Attach stores TraitRef(traitName) only — no format/details/categories reinvented here.
        val studyTraits = studyTraits()
        val attachable = filterAttachableStudyTraits(studyTraits)
        assertEquals(4, attachable.size)

        val nested = studyTraits + TraitObject().apply {
            name = "nested tree"
            format = Formats.TREE_ARCHITECTURE.getDatabaseName()
        } + TraitObject().apply {
            name = "nested summary"
            format = Formats.TREE_SUMMARY.getDatabaseName()
        } + TraitObject().apply {
            name = "video clip"
            format = Formats.VIDEO.getDatabaseName()
        }
        assertTrue(isUnsupportedTreePaletteFormat(Formats.TREE_ARCHITECTURE.getDatabaseName()))
        assertTrue(isUnsupportedTreePaletteFormat(Formats.TREE_SUMMARY.getDatabaseName()))
        assertEquals(4, filterAttachableStudyTraits(nested).size)

        // TraitRef carries name (+ optional required/order) — not a trait definition editor payload.
        val ref = TraitRef("length", requiredOverride = true, order = 0)
        assertEquals("length", ref.traitName)
        assertEquals(true, ref.requiredOverride)
        assertEquals(0, ref.order)
    }

    private fun NodeTypeDef.traitNames() = traitRefs.sortedBy { it.order }.map { it.traitName }

    private fun studyTraits(): List<TraitObject> = listOf(
        TraitObject().apply { name = "length"; format = Formats.NUMERIC.getDatabaseName() },
        TraitObject().apply { name = "color"; format = Formats.TEXT.getDatabaseName() },
        TraitObject().apply { name = "flowering date"; format = Formats.DATE.getDatabaseName() },
        TraitObject().apply { name = "branch photo"; format = Formats.CAMERA.getDatabaseName() },
    )

    private fun soyTreeCarrierSchema(): TreeSchema {
        val lengthColor = listOf(
            TraitRef("length", order = 0),
            TraitRef("color", order = 1),
        )
        return blankSchema().copy(
            id = "soy_tree_carrier_v1",
            name = "soy tree-carrier",
            rootType = "root",
            nodeTypes = listOf(
                NodeTypeDef(
                    name = "root",
                    displayName = "Root",
                    cls = "R",
                    allowedChildren = listOf(ChildRule("stem", EdgeType.PRECEDES, "Add Stem")),
                    traitRefs = lengthColor,
                ),
                NodeTypeDef(
                    name = "stem",
                    displayName = "Stem",
                    cls = "S",
                    allowedChildren = listOf(
                        ChildRule("stem", EdgeType.PRECEDES, "Add Stem"),
                        ChildRule("branch", EdgeType.BEARS, "Add Branch"),
                    ),
                    traitRefs = lengthColor,
                ),
                NodeTypeDef(
                    name = "branch",
                    displayName = "Branch",
                    cls = "B",
                    traitRefs = lengthColor + listOf(
                        TraitRef("flowering date", order = 2),
                        TraitRef("branch photo", order = 3),
                    ),
                ),
            ),
        )
    }
}
