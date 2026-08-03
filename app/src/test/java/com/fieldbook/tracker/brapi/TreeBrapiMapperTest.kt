package com.fieldbook.tracker.brapi

import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.composables.constructor.defaultSoybeanSchema
import com.fieldbook.tracker.traits.formats.tree.EdgeType
import com.fieldbook.tracker.traits.formats.tree.TreeMutations
import com.fieldbook.tracker.traits.formats.tree.TreeObservation
import com.fieldbook.tracker.traits.formats.tree.find
import org.brapi.v2.model.pheno.BrAPIObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TreeBrapiMapperTest {

    private fun sampleTree(): Pair<TreeObservation, String> {
        val schema = defaultSoybeanSchema()
        var root = TreeMutations.newRoot(schema, "2020-01-01T00:00:00Z")
        val internodeRule = schema.typeOf("plant")!!.allowedChildren.first()
        val (withChild, childId) = TreeMutations.addChild(
            root,
            root.id,
            internodeRule,
            schema,
            "2020-01-01T00:00:00Z",
        )
        root = withChild
        find(root, childId)!!.traits["Height"] = "12.5"
        val podRule = schema.typeOf("internode")!!.allowedChildren
            .first { it.edge == EdgeType.BEARS && it.nodeType == "pod" }
        val (withPod, podId) = TreeMutations.addChild(
            root,
            childId,
            podRule,
            schema,
            "2020-01-01T00:00:01Z",
        )
        val obs = TreeObservation(
            schemaId = schema.id,
            unit = "PLOT1",
            trait = "arch",
            rep = "1",
            captured = "2020-01-01T00:00:00Z",
            sourceApp = "test",
            mtg = "",
            root = withPod,
        )
        return obs to podId
    }

    @Test
    fun includesRootAndEveryDescendant() {
        val (obs, _) = sampleTree()
        val schema = defaultSoybeanSchema()
        val units = TreeBrapiMapper.buildChildObservationUnits(
            obs,
            schema,
            parentUnitDbId = "PLOT1",
            studyDbId = "STUDY1",
        ) { null }

        assertEquals(3, units.size)
        assertTrue(units.any { it.nodeId == obs.root.id })
        assertEquals("STUDY1", units.first().observationUnit.studyDbId)
    }

    @Test
    fun blankStudyDbIdIsLeftUnset() {
        val (obs, _) = sampleTree()
        val units = TreeBrapiMapper.buildChildObservationUnits(
            obs,
            defaultSoybeanSchema(),
            parentUnitDbId = "PLOT1",
            studyDbId = "  ",
        ) { null }
        assertNull(units.first().observationUnit.studyDbId)
    }

    @Test
    fun relationshipsAreAncestorChainToPlotWithoutSelfOrFakeParent() {
        val (obs, podId) = sampleTree()
        val schema = defaultSoybeanSchema()
        val units = TreeBrapiMapper.buildChildObservationUnits(
            obs,
            schema,
            parentUnitDbId = "PLOT1",
            studyDbId = "STUDY1",
        ) { null }

        val podUnit = units.first { it.nodeId == podId }.observationUnit
        val position = podUnit.observationUnitPosition
        assertNotNull(position)
        val rels = position.observationLevelRelationships
        assertNotNull(rels)
        assertFalse(rels.any { it.levelName == "parent" })
        assertFalse(rels.any { it.observationUnitDbId == podId })
        assertEquals(podId, position.observationLevel.observationUnitDbId)
        assertEquals("plot", rels.first().levelName)
        assertEquals("PLOT1", rels.first().observationUnitDbId)
        assertEquals(0, rels.first().levelOrder)
        // plot + plant root + internode ancestor
        assertEquals(3, rels.size)
        assertEquals(obs.root.id, rels[1].observationUnitDbId)
    }

    @Test
    fun rootRelationshipsContainOnlyPlot() {
        val (obs, _) = sampleTree()
        val units = TreeBrapiMapper.buildChildObservationUnits(
            obs,
            defaultSoybeanSchema(),
            parentUnitDbId = "PLOT1",
        ) { null }
        val rootUnit = units.first { it.nodeId == obs.root.id }.observationUnit
        val rels = rootUnit.observationUnitPosition.observationLevelRelationships
        assertEquals(1, rels.size)
        assertEquals("plot", rels.single().levelName)
        assertEquals("PLOT1", rels.single().observationUnitDbId)
        assertEquals(
            "PLOT1",
            rootUnit.additionalInfo.get("parentObservationUnitDbId").asString,
        )
    }

    @Test
    fun childUnitsCarryMtgEdgeAndOntologyValues() {
        val (obs, podId) = sampleTree()
        val schema = defaultSoybeanSchema()
        val height = TraitObject().apply {
            name = "Height"
            externalDbId = "CO_336:0000001"
        }

        val units = TreeBrapiMapper.buildChildObservationUnits(
            obs,
            schema,
            parentUnitDbId = "PLOT1",
            studyDbId = "STUDY1",
        ) { name -> if (name == "Height") height else null }

        val internode = units.first {
            it.observationUnit.additionalInfo.has("value_Height")
        }
        assertEquals("12.5", internode.observationUnit.additionalInfo.get("value_Height").asString)
        assertEquals(
            "CO_336:0000001",
            internode.observationUnit.additionalInfo.get("observationVariableDbId_Height").asString,
        )

        val podUnit = units.first { it.nodeId == podId }
        assertEquals("+", podUnit.mtgEdge)
        assertEquals("+", podUnit.observationUnit.additionalInfo.get("mtgEdge").asString)
    }

    @Test
    fun valuesAttachedWithoutOntologyId() {
        val schema = defaultSoybeanSchema()
        var root = TreeMutations.newRoot(schema, "t")
        val rule = schema.typeOf("plant")!!.allowedChildren.first()
        val (tree, childId) = TreeMutations.addChild(root, root.id, rule, schema, "t")
        find(tree, childId)!!.traits["Height"] = "9"
        val obs = TreeObservation(
            schemaId = schema.id,
            unit = "U",
            trait = "t",
            rep = "1",
            captured = "t",
            sourceApp = "t",
            mtg = "",
            root = tree,
        )
        val units = TreeBrapiMapper.buildChildObservationUnits(obs, schema, "U") { null }
        val child = units.first { it.nodeId == childId }
        assertTrue(child.observationUnit.additionalInfo.has("value_Height"))
        assertFalse(child.observationUnit.additionalInfo.has("observationVariableDbId_Height"))
        assertEquals("9", child.observationUnit.additionalInfo.get("value_Height").asString)
    }

    @Test
    fun precedesEdgeIsPreserved() {
        val schema = defaultSoybeanSchema()
        val root = TreeMutations.newRoot(schema, "2020-01-01T00:00:00Z")
        val rule = schema.typeOf("plant")!!.allowedChildren.first { it.edge == EdgeType.PRECEDES }
        val (tree, id) = TreeMutations.addChild(root, root.id, rule, schema, "t")
        val obs = TreeObservation(
            schemaId = schema.id,
            unit = "U",
            trait = "t",
            rep = "1",
            captured = "t",
            sourceApp = "t",
            mtg = "",
            root = tree,
        )
        val units = TreeBrapiMapper.buildChildObservationUnits(obs, schema, "U") { null }
        assertEquals("<", units.first { it.nodeId == id }.mtgEdge)
    }

    @Test
    fun bridgeScaffoldWritesSummaryAdditionalInfoNotChildUnitBlob() {
        // Prepared child units are POSTed separately; parent additionalInfo only
        // carries summary metadata (never the unit list JSON).
        val (obs, _) = sampleTree()
        val schema = defaultSoybeanSchema()
        val prepared = TreeBrapiMapper.prepareUpload(
            obs,
            schema,
            parentUnitDbId = "PLOT1",
            studyDbId = "STUDY1",
        ) { null }

        val parent = BrAPIObservation()
        parent.observationUnitDbId = "PLOT1"
        parent.studyDbId = "STUDY1"
        parent.additionalInfo = brapiInfoOf(
            "treeSchemaId" to schema.id,
            "treeMtg" to obs.mtg,
            "treeChildUnitCount" to prepared.childUnits.size.toString(),
            "sampleMtgEdge" to prepared.childUnits.first().mtgEdge,
            "treeStudyDbId" to "STUDY1",
            "treeChildUnitsPosted" to "true",
        )

        assertEquals("3", parent.additionalInfo.get("treeChildUnitCount").asString)
        assertEquals("STUDY1", parent.additionalInfo.get("treeStudyDbId").asString)
        assertEquals(schema.id, parent.additionalInfo.get("treeSchemaId").asString)
        assertEquals("true", parent.additionalInfo.get("treeChildUnitsPosted").asString)
        assertFalse(parent.additionalInfo.has("treeChildUnits"))
        assertFalse(parent.additionalInfo.has("observationUnits"))
        assertEquals(3, prepared.childUnits.size)
    }
}
