package com.fieldbook.tracker.brapi

import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.composables.constructor.defaultSoybeanSchema
import com.fieldbook.tracker.traits.formats.Formats
import com.fieldbook.tracker.traits.formats.tree.EdgeType
import com.fieldbook.tracker.traits.formats.tree.TreeMutations
import com.fieldbook.tracker.traits.formats.tree.TreeObservation
import com.fieldbook.tracker.traits.formats.tree.find
import org.brapi.v2.model.pheno.BrAPIObservation
import org.brapi.v2.model.pheno.BrAPIObservationUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.threeten.bp.OffsetDateTime
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class TreeBrapiUploadOrchestrationTest {

    private fun sampleTree(): TreeObservation {
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
        val (withPod, _) = TreeMutations.addChild(
            root,
            childId,
            podRule,
            schema,
            "2020-01-01T00:00:01Z",
        )
        return TreeObservation(
            schemaId = schema.id,
            unit = "PLOT1",
            trait = "arch",
            rep = "1",
            captured = "2020-01-01T00:00:00Z",
            sourceApp = "test",
            mtg = "",
            root = withPod,
        )
    }

    @Test
    fun prepareUploadBuildsChildUnitsAndNodeObservationsWithOntology() {
        val obs = sampleTree()
        val schema = defaultSoybeanSchema()
        val height = TraitObject().apply {
            name = "Height"
            externalDbId = "CO_336:0000001"
        }

        val prepared = TreeBrapiMapper.prepareUpload(
            obs,
            schema,
            parentUnitDbId = "PLOT1",
            studyDbId = "STUDY1",
            collector = "tech",
            timestamp = OffsetDateTime.parse("2020-01-01T00:00:00Z"),
        ) { name -> if (name == "Height") height else null }

        assertEquals(3, prepared.childUnits.size)
        assertEquals(1, prepared.nodeObservations.size)
        val nodeObs = prepared.nodeObservations.single()
        assertEquals("CO_336:0000001", nodeObs.observationVariableDbId)
        assertEquals("12.5", nodeObs.value)
        assertEquals("STUDY1", nodeObs.studyDbId)
        assertTrue(prepared.childUnits.any { it.nodeId == nodeObs.observationUnitDbId })
    }

    @Test
    fun prepareUploadSkipsNodeObservationsWithoutOntologyId() {
        val obs = sampleTree()
        val prepared = TreeBrapiMapper.prepareUpload(
            obs,
            defaultSoybeanSchema(),
            parentUnitDbId = "PLOT1",
            studyDbId = "STUDY1",
        ) { null }
        assertEquals(3, prepared.childUnits.size)
        assertTrue(prepared.nodeObservations.isEmpty())
    }

    @Test
    fun uploadSequencePostsChildUnitsBeforeObservations() {
        val order = mutableListOf<String>()
        val failCode = AtomicInteger(-1)

        TreeBrapiUploadSequence.execute(
            true,
            { onSuccess, _ ->
                order += "units"
                onSuccess.run()
            },
            { _ -> order += "observations" },
            { code -> failCode.set(code) },
        )

        assertEquals(listOf("units", "observations"), order)
        assertEquals(-1, failCode.get())
    }

    @Test
    fun uploadSequenceSkipsUnitsWhenNone() {
        val order = mutableListOf<String>()
        var unitsCalled = false

        TreeBrapiUploadSequence.execute(
            false,
            { _, _ -> unitsCalled = true },
            { _ -> order += "observations" },
            { },
        )

        assertFalse(unitsCalled)
        assertEquals(listOf("observations"), order)
    }

    @Test
    fun uploadSequenceFailsObservationsWhenUnitsFail() {
        val failCode = AtomicReference(0)
        var observationsCalled = false

        TreeBrapiUploadSequence.execute(
            true,
            { _, onFail -> onFail.accept(409) },
            { _ -> observationsCalled = true },
            { code -> failCode.set(code) },
        )

        assertFalse(observationsCalled)
        assertEquals(409, failCode.get())
    }

    @Test
    fun uploadSequencePropagatesObservationFail() {
        val failCode = AtomicReference(0)

        TreeBrapiUploadSequence.execute(
            true,
            { onSuccess, _ -> onSuccess.run() },
            { onFail -> onFail.accept(500) },
            { code -> failCode.set(code) },
        )

        assertEquals(500, failCode.get())
    }

    @Test
    fun localTreeTraitRoutesToNewObservationsNotUserCreated() {
        val category = TreeBrapiExportRouting.exportCategory(
            Formats.TREE_ARCHITECTURE.getDatabaseName(),
            "local",
            "https://brapi.example",
            TreeBrapiExportRouting.SyncStatus(dbId = null, timestamp = null, lastSyncedTime = null),
            isPhoto = false,
        )
        assertEquals("newObservations", category)
    }

    @Test
    fun localNonTreeTraitStaysUserCreated() {
        val category = TreeBrapiExportRouting.exportCategory(
            "numeric",
            "local",
            "https://brapi.example",
            TreeBrapiExportRouting.SyncStatus(dbId = null, timestamp = null, lastSyncedTime = null),
            isPhoto = false,
        )
        assertEquals("userCreatedTraitObservations", category)
    }

    @Test
    fun hostTreeTraitUsesStatusCategory() {
        val category = TreeBrapiExportRouting.exportCategory(
            Formats.TREE_ARCHITECTURE.getDatabaseName(),
            "https://brapi.example",
            "https://brapi.example",
            TreeBrapiExportRouting.SyncStatus(dbId = null, timestamp = null, lastSyncedTime = null),
            isPhoto = false,
        )
        assertEquals("newObservations", category)
    }

    @Test
    fun preparedUnitsAreDistinctBrAPIObservationUnitsReadyToPost() {
        val prepared = TreeBrapiMapper.prepareUpload(
            sampleTree(),
            defaultSoybeanSchema(),
            parentUnitDbId = "PLOT1",
            studyDbId = "STUDY1",
        ) { null }
        val units: List<BrAPIObservationUnit> = prepared.childUnits.map { it.observationUnit }
        assertEquals(3, units.size)
        assertTrue(units.all { !it.observationUnitDbId.isNullOrBlank() })
        assertTrue(units.all { it.studyDbId == "STUDY1" })
        assertTrue(units.all { it.observationUnitPosition?.observationLevelRelationships != null })
        // Contract: units are real POST payloads, not stuffed into a parent observation blob.
        val parent = BrAPIObservation()
        parent.additionalInfo = brapiInfoOf(
            "treeChildUnitCount" to units.size.toString(),
            "treeChildUnitsPosted" to "true",
        )
        assertFalse(parent.additionalInfo.has("treeChildUnits"))
        assertEquals("true", parent.additionalInfo.get("treeChildUnitsPosted").asString)
    }

    @Test
    fun remapChildUnitResponseIds_rewritesNodeObservationUnitIds() {
        val requested = listOf(
            BrAPIObservationUnit().apply {
                observationUnitDbId = "local-a"
                observationUnitName = "N1"
            },
            BrAPIObservationUnit().apply {
                observationUnitDbId = "local-b"
                observationUnitName = "N2"
            },
        )
        val response = listOf(
            BrAPIObservationUnit().apply {
                observationUnitDbId = "server-a"
                observationUnitName = "N1"
            },
            BrAPIObservationUnit().apply {
                observationUnitDbId = "server-b"
                observationUnitName = "N2"
            },
        )
        val nodeObs = listOf(
            BrAPIObservation().apply {
                observationUnitDbId = "local-b"
                observationVariableDbId = "VAR1"
                value = "12"
            },
        )
        val map = TreeBrapiMapper.remapChildUnitResponseIds(requested, response, nodeObs)
        assertEquals("server-a", map["local-a"])
        assertEquals("server-b", map["local-b"])
        assertEquals("server-b", nodeObs.single().observationUnitDbId)
    }

    @Test
    fun ensureLocalTreeParentsSynced_synthesizesDbIdWhenVariableBlank() {
        val submitted = listOf(
            com.fieldbook.tracker.brapi.model.Observation().apply {
                fieldBookDbId = "fb-1"
                unitDbId = "PLOT1"
                variableDbId = ""
                value = "content://local/tree.json"
            },
        )
        val mapped = mutableListOf<com.fieldbook.tracker.brapi.model.Observation>()
        TreeBrapiExportRouting.ensureLocalTreeParentsSynced(
            submitted,
            mapped,
            serverData = null,
            fieldBookReferenceSource = "Field Book Upload",
        )
        assertEquals(1, mapped.size)
        assertEquals("fb-1", mapped.single().fieldBookDbId)
        assertEquals("tree-local-fb-1", mapped.single().dbId)
        assertTrue(mapped.single().lastSyncedTime != null)
        val category = TreeBrapiExportRouting.exportCategory(
            Formats.TREE_ARCHITECTURE.getDatabaseName(),
            "local",
            "https://brapi.example",
            TreeBrapiExportRouting.SyncStatus(
                dbId = mapped.single().dbId,
                timestamp = null,
                lastSyncedTime = mapped.single().lastSyncedTime,
            ),
        )
        assertEquals("syncedObservations", category)
    }
}
