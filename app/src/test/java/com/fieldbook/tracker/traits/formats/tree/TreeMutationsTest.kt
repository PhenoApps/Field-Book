package com.fieldbook.tracker.traits.formats.tree

import com.fieldbook.tracker.objects.TraitObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TreeMutationsTest {

    @Test
    fun nextIndex_globalPerClass_notPerParent() {
        val schema = soybeanSchema()
        val ts = "2026-01-01T00:00:00Z"
        var root = TreeCodec.newRoot(schema, ts)
        val internode = schema.typeOf("internode")!!
        val precedes = internode.allowedChildren.first { it.edge == EdgeType.PRECEDES }
        val bears = ChildRule("internode", EdgeType.BEARS, "Offshoot")

        val (r1, n1) = TreeMutations.addChild(root, root.id, precedes, schema, ts)
        root = r1
        val (r2, n2) = TreeMutations.addChild(root, n1, precedes, schema, ts)
        root = r2
        val (r3, n3) = TreeMutations.addChild(root, n2, precedes, schema, ts)
        root = r3
        val (r4, n4) = TreeMutations.addChild(root, n2, bears, schema, ts)
        root = r4

        val indices = allNodes(root).filter { it.cls == "N" }.map { it.idx }.sorted().toList()
        assertEquals(listOf(1, 2, 3, 4), indices)
        assertEquals("N4", find(root, n4)?.let { "${it.cls}${it.idx}" })
        assertEquals(5, TreeMutations.nextIndex(root, "N"))
    }

    @Test
    fun pathTo_resolvesFiveLevels() {
        val schema = soybeanSchema()
        val ts = "2026-01-01T00:00:00Z"
        var root = TreeCodec.newRoot(schema, ts)
        val internode = schema.typeOf("internode")!!
        val precedes = internode.allowedChildren.first()
        val bears = ChildRule("internode", EdgeType.BEARS, "Offshoot")
        val podRule = ChildRule("pod", EdgeType.BEARS, "Pod")

        val (r1, n1) = TreeMutations.addChild(root, root.id, precedes, schema, ts)
        root = r1
        val (r2, n2) = TreeMutations.addChild(root, n1, precedes, schema, ts)
        root = r2
        val (r3, n4) = TreeMutations.addChild(root, n2, bears, schema, ts)
        root = r3
        val (r4, _) = TreeMutations.addChild(root, n4, podRule, schema, ts)
        root = r4
        val (r5, n3) = TreeMutations.addChild(root, n2, precedes, schema, ts)
        root = r5
        val (r6, _) = TreeMutations.addChild(root, n3, podRule, schema, ts)
        root = r6

        val path = pathTo(root, find(root, n4)!!.children.first { it.nodeType == "pod" }.id)
        assertEquals(5, path.size)
    }

    @Test
    fun moveNode_rejectsCycleWhenNewParentIsDescendant() {
        val schema = soybeanSchema()
        val ts = "2026-01-01T00:00:00Z"
        var root = TreeCodec.newRoot(schema, ts)
        val precedes = schema.typeOf("internode")!!.allowedChildren.first { it.edge == EdgeType.PRECEDES }
        val (r1, n1) = TreeMutations.addChild(root, root.id, precedes, schema, ts)
        root = r1
        val (r2, n2) = TreeMutations.addChild(root, n1, precedes, schema, ts)
        root = r2
        val before = TreeCodec.encodeMtg(root)
        val moved = TreeMutations.moveNode(root, n1, n2, EdgeType.PRECEDES, schema)
        assertEquals(before, TreeCodec.encodeMtg(moved))
        assertEquals(n1, parentOf(moved, n2)?.id)
    }

    companion object {
        fun soybeanSchema() = TreeSchema(
            id = "soy_arch_v1",
            name = "Soybean architecture",
            version = 1,
            rootType = "plant",
            nodeTypes = listOf(
                NodeTypeDef("plant", "Plant", "P", listOf(ChildRule("internode", EdgeType.PRECEDES, "Main"))),
                NodeTypeDef(
                    "internode", "Internode", "N",
                    listOf(
                        ChildRule("internode", EdgeType.PRECEDES, "Next"),
                        ChildRule("internode", EdgeType.BEARS, "Offshoot"),
                        ChildRule("pod", EdgeType.BEARS, "Pod"),
                    ),
                ),
                NodeTypeDef("pod", "Pod", "C"),
            ),
        )
    }
}

class TreeSummaryTest {

    @Test
    fun compute_exampleTree_matchesFixture() {
        val schema = TreeMutationsTest.soybeanSchema().copy(
            summaryPodTraitName = "Seed count",
            summaryPodNodeType = "pod",
        )
        val ts = "2026-01-01T00:00:00Z"
        var root = TreeCodec.newRoot(schema, ts)
        val internode = schema.typeOf("internode")!!
        val precedes = internode.allowedChildren.first { it.edge == EdgeType.PRECEDES }
        val bearsInternode = ChildRule("internode", EdgeType.BEARS, "Offshoot")
        val podRule = ChildRule("pod", EdgeType.BEARS, "Pod")

        val (r1, n1) = TreeMutations.addChild(root, root.id, precedes, schema, ts)
        root = r1
        val (r2, n2) = TreeMutations.addChild(root, n1, precedes, schema, ts)
        root = r2
        val (r3, n4) = TreeMutations.addChild(root, n2, bearsInternode, schema, ts)
        root = r3
        val (r4, _) = TreeMutations.addChild(root, n4, podRule, schema, ts)
        root = r4
        val podOnN4 = find(root, n4)!!.children.first { it.nodeType == "pod" }
        root = TreeMutations.setTrait(root, podOnN4.id, "Seed count", "8", ts)
        val (r5, n3) = TreeMutations.addChild(root, n2, precedes, schema, ts)
        root = r5
        val (r6, _) = TreeMutations.addChild(root, n3, podRule, schema, ts)
        root = r6
        val podOnN3 = find(root, n3)!!.children.first { it.nodeType == "pod" }
        root = TreeMutations.setTrait(root, podOnN3.id, "Seed count", "10", ts)

        val summary = TreeSummary.compute(root, schema)
        assertEquals(4, summary.nodeCount)
        assertEquals(18, summary.podTotal)
        assertEquals(1, summary.branchCount)
        assertEquals(5, summary.maxOrder)
        assertEquals(4.5, summary.podsPerNode, 0.001)
        assertEquals(false, summary.usesLengthMetric)
    }

    /**
     * soy tree-carrier plant:
     * R > S1 > S2 > S3 > S4 > S5; B1,B2 from S1; B3,B4 from S3
     * stem lengths [12,10,NA,8,5]; branch lengths [4,5,4,NA]
     *
     * Before length fallback: nodeCount/podTotal/branchCount were all 0 (no internode/pod).
     * After: stems=5, length sum=48 (NA skipped), BEARS branches=4. Observation = podTotal.toString().
     */
    @Test
    fun compute_soyTreeCarrier_sumsLengthAndCountsStemsBranches() {
        val schema = soyTreeCarrierSchema()
        val ts = "2026-07-30T00:00:00Z"
        var root = TreeCodec.newRoot(schema, ts)
        val stemFromRoot = schema.typeOf("root")!!.allowedChildren.first { it.nodeType == "stem" }
        val nextStem = schema.typeOf("stem")!!.allowedChildren.first { it.edge == EdgeType.PRECEDES }
        val branchRule = schema.typeOf("stem")!!.allowedChildren.first { it.edge == EdgeType.BEARS }

        val stemIds = mutableListOf<String>()
        val (r1, s1) = TreeMutations.addChild(root, root.id, stemFromRoot, schema, ts)
        root = r1
        stemIds += s1
        var parent = s1
        repeat(4) {
            val (r, id) = TreeMutations.addChild(root, parent, nextStem, schema, ts)
            root = r
            stemIds += id
            parent = id
        }
        // stem lengths [12, 10, NA, 8, 5]
        listOf("12", "10", "NA", "8", "5").forEachIndexed { i, v ->
            root = TreeMutations.setTrait(root, stemIds[i], "length", v, ts)
        }
        // b1,b2 from s1; b3,b4 from s3 — lengths [4, 5, 4, NA]
        val branchParents = listOf(stemIds[0], stemIds[0], stemIds[2], stemIds[2])
        val branchLengths = listOf("4", "5", "4", "NA")
        branchParents.zip(branchLengths).forEach { (stemId, len) ->
            val (r, bid) = TreeMutations.addChild(root, stemId, branchRule, schema, ts)
            root = r
            root = TreeMutations.setTrait(root, bid, "length", len, ts)
        }

        val summary = TreeSummary.compute(root, schema)
        assertEquals(5, summary.nodeCount)
        assertEquals(48, summary.podTotal) // 12+10+8+5 + 4+5+4; NA skipped
        assertEquals(4, summary.branchCount)
        assertEquals(6, summary.maxOrder) // R + five stems
        assertEquals(true, summary.usesLengthMetric)
        assertEquals("48", summary.podTotal.toString()) // flush writes this observation
    }

    @Test(expected = IllegalStateException::class)
    fun addChild_enforcesMaxChildren() {
        val schema = TreeSchema(
            id = "max_v1",
            name = "max",
            version = 1,
            rootType = "root",
            nodeTypes = listOf(
                NodeTypeDef(
                    "root", "Root", "R",
                    listOf(ChildRule("stem", EdgeType.PRECEDES, "Add Stem")),
                    maxChildren = 1,
                ),
                NodeTypeDef("stem", "Stem", "S"),
            ),
        )
        val ts = "2026-01-01T00:00:00Z"
        var root = TreeCodec.newRoot(schema, ts)
        val rule = schema.typeOf("root")!!.allowedChildren.first()
        val (r1, _) = TreeMutations.addChild(root, root.id, rule, schema, ts)
        root = r1
        TreeMutations.addChild(root, root.id, rule, schema, ts)
    }

    companion object {
        fun soyTreeCarrierSchema() = TreeSchema(
            id = "soy_tree_carrier_v1",
            name = "soy tree-carrier",
            version = 1,
            rootType = "root",
            nodeTypes = listOf(
                NodeTypeDef(
                    "root", "Root", "R",
                    listOf(ChildRule("stem", EdgeType.PRECEDES, "Add Stem")),
                    traitRefs = listOf(TraitRef("length", order = 0)),
                ),
                NodeTypeDef(
                    "stem", "Stem", "S",
                    listOf(
                        ChildRule("stem", EdgeType.PRECEDES, "Add Stem"),
                        ChildRule("branch", EdgeType.BEARS, "Add Branch"),
                    ),
                    traitRefs = listOf(TraitRef("length", order = 0)),
                ),
                NodeTypeDef(
                    "branch", "Branch", "B",
                    traitRefs = listOf(TraitRef("length", order = 0)),
                ),
            ),
            summaryPodTraitName = null,
            summaryPodNodeType = null,
        )
    }
}
