package com.fieldbook.tracker.traits.formats.tree

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class TreeCodecTest {

    @Test
    fun encodeMtg_poplarReference_byteExact() {
        val expected =
            "/I1<I2<I3<I4<I5<I6[+I20<I21<I22<I23<I24<I25<I26<I27<I28<I29]<I7<I8<I9<I10<I11<I12<I13<I14<I15<I16<I17<I18<I19"
        val actual = TreeCodec.encodeMtg(TreeCodec.poplarReferenceRoot())
        assertEquals(expected, actual)
    }

    @Test
    fun encodeMtg_bearsBeforePrecedes_simpleTree() {
        val schema = TreeSchema(
            id = "mtg_test_v1",
            name = "soy tree-carrier",
            version = 1,
            rootType = "root",
            nodeTypes = listOf(
                NodeTypeDef(
                    name = "root",
                    displayName = "Root",
                    cls = "R",
                    allowedChildren = listOf(
                        ChildRule("internode", EdgeType.BEARS, "Bears child"),
                        ChildRule("internode", EdgeType.PRECEDES, "Precedes child"),
                    ),
                ),
                NodeTypeDef(
                    name = "internode",
                    displayName = "Internode",
                    cls = "N",
                    allowedChildren = emptyList(),
                ),
            ),
        )

        var root = TreeCodec.newRoot(schema, "2026-07-27T10:00:00Z")
        val bearsRule = schema.typeOf("root")!!.allowedChildren.first { it.edge == EdgeType.BEARS }
        val precedesRule = schema.typeOf("root")!!.allowedChildren.first { it.edge == EdgeType.PRECEDES }

        val (r1, bearId) = TreeMutations.addChild(root, root.id, bearsRule, schema, "2026-07-27T10:00:01Z")
        root = r1
        val (r2, precedesId) = TreeMutations.addChild(root, root.id, precedesRule, schema, "2026-07-27T10:00:02Z")
        root = r2

        val bearsNode = find(root, bearId)!!
        val precedesNode = find(root, precedesId)!!
        val mtg = TreeCodec.encodeMtg(root)

        assertEquals(
            "/${root.cls}${root.idx}[+${bearsNode.cls}${bearsNode.idx}]<${precedesNode.cls}${precedesNode.idx}",
            mtg,
        )
    }

    @Test
    fun jsonRoundTrip_fiveLevelTree() {
        val schema = soybeanSchema()
        val created = Instant.parse("2026-07-23T12:00:00Z").toString()
        var root = TreeCodec.newRoot(schema, created)
        val mainRule = schema.typeOf("internode")!!.allowedChildren.first { it.edge == EdgeType.PRECEDES }
        val (r1, n1) = TreeMutations.addChild(root, root.id, mainRule, schema, created)
        root = r1
        val (r2, n2) = TreeMutations.addChild(root, n1, mainRule, schema, created)
        root = r2
        val (r3, _) = TreeMutations.addChild(root, n2, mainRule, schema, created)
        root = r3

        val pending = TreePending(
            unitId = "PLOT_1",
            studyId = "1",
            traitId = "t1",
            traitName = "Architecture",
            rep = "1",
            root = root,
            capturedAt = created,
            sourceApp = "Field Book Test",
        )
        val json = TreeCodec.encodeSidecar(schema.id, pending)
        val decoded = TreeCodec.decodeObservation(json)
        assertEquals(schema.id, decoded.schemaId)
        assertEquals(4, flatten(decoded.root).size)
        assertTrue(decoded.mtg.contains("N"))
    }

    private fun soybeanSchema() = TreeSchema(
        id = "soy_arch_v1",
        name = "Soybean architecture",
        version = 1,
        rootType = "plant",
        nodeTypes = listOf(
            NodeTypeDef("plant", "Plant", "P", listOf(ChildRule("internode", EdgeType.PRECEDES, "Main"))),
            NodeTypeDef(
                "internode", "Internode", "N",
                listOf(ChildRule("internode", EdgeType.PRECEDES, "Next")),
                traitRefs = listOf(TraitRef("Node position", requiredOverride = true, order = 0)),
            ),
        ),
    )

    @Test
    fun encodeMtg_multiplePrecedesSiblings_areBracketedNotChained() {
        val schema = TreeSchema(
            id = "multi_prec_v1",
            name = "multi",
            version = 1,
            rootType = "stem",
            nodeTypes = listOf(
                NodeTypeDef(
                    name = "stem",
                    displayName = "Stem",
                    cls = "S",
                    allowedChildren = listOf(
                        ChildRule("branch", EdgeType.PRECEDES, "Next"),
                    ),
                ),
                NodeTypeDef(name = "branch", displayName = "Branch", cls = "B"),
            ),
        )
        val ts = "2026-01-01T00:00:00Z"
        var root = TreeCodec.newRoot(schema, ts)
        val rule = schema.typeOf("stem")!!.allowedChildren.first()
        val (r1, a) = TreeMutations.addChild(root, root.id, rule, schema, ts)
        root = r1
        val (r2, b) = TreeMutations.addChild(root, root.id, rule, schema, ts)
        root = r2
        val na = find(root, a)!!
        val nb = find(root, b)!!
        val mtg = TreeCodec.encodeMtg(root)
        // Must NOT be S1<B1<B2 (false axis). Bracket each sibling.
        assertEquals(
            "/${root.cls}${root.idx}[<${na.cls}${na.idx}][<${nb.cls}${nb.idx}]",
            mtg,
        )
        assertTrue(!mtg.contains("<${na.cls}${na.idx}<"))
    }
}
