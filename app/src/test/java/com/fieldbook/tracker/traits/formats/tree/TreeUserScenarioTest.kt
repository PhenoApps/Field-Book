package com.fieldbook.tracker.traits.formats.tree

import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.composables.constructor.blankSchema
import com.fieldbook.tracker.utilities.TreePathPortability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end data-path proof for the user story:
 * soy tree-carrier with root/stem/branch, length+color on all,
 * flowering date + branch photo on branch; 3 stems; branch on stem 2.
 */
class TreeUserScenarioTest {

    /**
     * Study palette for the soy tree-carrier scenario uses English trait names.
     * Russian names from `trait-tree/trait_export_2026-07-30-11-49-30.trt` are
     * present in the study file but are not TraitRefs on this schema, so they
     * must not participate in length/color validation.
     */
    private val englishStudyResolver = TraitRefResolver { name ->
        when (name) {
            "length" -> TraitObject().apply {
                this.name = name
                format = "numeric"
                minimum = "0"
                maximum = "100"
            }
            "color" -> TraitObject().apply {
                this.name = name
                format = "text"
            }
            "flowering date" -> TraitObject().apply {
                this.name = name
                format = "date"
            }
            "branch photo" -> TraitObject().apply {
                this.name = name
                format = "photo"
            }
            // Exported Russian traits exist in the .trt but are unresolved here.
            "длина междоузлия", "длина боба" -> null
            else -> null
        }
    }

    @Test
    fun soyTreeCarrier_collectAndPortableSidecar() {
        val schema = userSchema()
        assertTrue(TreeSchemaValidator.validate(schema).isEmpty())

        val t0 = "2026-07-27T10:00:00Z"
        var root = TreeMutations.newRoot(schema, t0)
        // Scenario requirement: length + color exist on all node types (root/stem/branch).
        root = TreeMutations.setTrait(root, root.id, "length", "10", t0)
        root = TreeMutations.setTrait(root, root.id, "color", "green", t0)
        val stemRule = schema.typeOf("root")!!.allowedChildren.first { it.nodeType == "stem" }
        val nextStem = schema.typeOf("stem")!!.allowedChildren.first {
            it.nodeType == "stem" && it.edge == EdgeType.PRECEDES
        }
        val branchRule = schema.typeOf("stem")!!.allowedChildren.first {
            it.nodeType == "branch" && it.edge == EdgeType.BEARS
        }

        val (r1, s1) = TreeMutations.addChild(root, root.id, stemRule, schema, t0)
        root = r1
        root = TreeMutations.setTrait(root, s1, "length", "11", t0)
        root = TreeMutations.setTrait(root, s1, "color", "green", t0)

        val (r2, s2) = TreeMutations.addChild(root, s1, nextStem, schema, t0)
        root = r2
        root = TreeMutations.setTrait(root, s2, "length", "12", t0)
        root = TreeMutations.setTrait(root, s2, "color", "green", t0)

        val (r3, s3) = TreeMutations.addChild(root, s2, nextStem, schema, t0)
        root = r3
        root = TreeMutations.setTrait(root, s3, "length", "13", t0)
        root = TreeMutations.setTrait(root, s3, "color", "green", t0)

        // Navigate back to s2 (↑) then add branch
        assertEquals(s2, parentOf(root, s3)!!.id)
        val (r4, b1) = TreeMutations.addChild(root, s2, branchRule, schema, t0)
        root = r4
        root = TreeMutations.setTrait(root, b1, "length", "5", t0)
        root = TreeMutations.setTrait(root, b1, "color", "yellow", t0)
        root = TreeMutations.setTrait(root, b1, "flowering date", "2026-07-27", t0)
        val photoContentUri = "content://com.fieldbook/tree/sample1_node_2026-07-27-10-00-00-000.jpg"
        root = TreeMutations.setTrait(
            root,
            b1,
            "branch photo",
            photoContentUri,
            t0,
        )

        val s2Node = find(root, s2)!!
        assertEquals(listOf(s3, b1), s2Node.children.map { it.id })
        assertEquals(EdgeType.PRECEDES, find(root, s3)!!.edge)
        assertEquals(EdgeType.BEARS, find(root, b1)!!.edge)

        val pending = TreePending(
            unitId = "sample1",
            studyId = "field1",
            traitId = "t1",
            traitName = "soy tree-carrier",
            rep = "1",
            root = root,
            capturedAt = t0,
            sourceApp = "Field Book Test",
        )
        val expectedMtg = TreeCodec.encodeMtg(root)
        val json = TreeCodec.encodeSidecar(schema.id, pending)
        val decoded = TreeCodec.decodeObservation(json)

        assertEquals("soy tree-carrier", decoded.trait)
        assertEquals("sample1", decoded.unit)
        assertEquals("root", decoded.root.nodeType)
        assertEquals(expectedMtg, decoded.mtg)
        // portableizeTree rewrites photo URIs to ExportUtil trait-folder relative paths.
        assertTrue(json.contains("soy tree-carrier/"))
        assertTrue(!json.contains(photoContentUri))
        assertTrue(!json.contains("content://"))

        val branch = find(decoded.root, b1)!!
        assertEquals("branch", branch.nodeType)
        assertEquals("5", branch.traits["length"])
        assertEquals("yellow", branch.traits["color"])
        assertEquals("2026-07-27", branch.traits["flowering date"])
        val photo = branch.traits["branch photo"]!!
        assertTrue(TreePathPortability.isRelative(photo))
        assertTrue(photo.startsWith("soy tree-carrier/") || photo.contains("sample1_node_"))
        assertTrue(photo.endsWith(".jpg"))
        assertTrue(!photo.startsWith("content:"))

        val decodedRoot = decoded.root
        assertEquals("root", decodedRoot.nodeType)
        assertEquals("10", decodedRoot.traits["length"])
        assertEquals("green", decodedRoot.traits["color"])

        val decodedS2 = find(decoded.root, s2)!!
        assertEquals("stem", decodedS2.nodeType)
        assertEquals("12", decodedS2.traits["length"])
        assertEquals("green", decodedS2.traits["color"])
        assertTrue(!decodedS2.traits.containsKey("flowering date"))
        assertTrue(!decodedS2.traits.containsKey("branch photo"))
        // JSON children keep collect insertion order (stem3 PRECEDES, then branch BEARS).
        assertEquals(listOf(s3, b1), decodedS2.children.map { it.id })
        assertEquals(listOf("stem", "branch"), decodedS2.children.map { it.nodeType })
        assertEquals(listOf(EdgeType.PRECEDES, EdgeType.BEARS), decodedS2.children.map { it.edge })
        // Companion MTG intentionally encodes BEARS (+) groups before PRECEDES (<) under a parent,
        // even when JSON children keep collect order [PRECEDES, BEARS].
        assertTrue(Regex("""S\d+\[\+[^]]*]<S\d+""").containsMatchIn(decoded.mtg))

        val decodedS3 = find(decoded.root, s3)!!
        assertEquals("stem", decodedS3.nodeType)
        assertEquals("13", decodedS3.traits["length"])
        assertEquals("green", decodedS3.traits["color"])
        assertTrue(!decodedS3.traits.containsKey("flowering date"))
        assertTrue(!decodedS3.traits.containsKey("branch photo"))

        // Topology: root → stem → stem(s2) → [stem3, branch]
        assertEquals(listOf("root", "stem", "stem"), listOf(
            decoded.root.nodeType,
            decoded.root.children.single().nodeType,
            decoded.root.children.single().children.single().nodeType,
        ))

        val rows = TreeFlattenExport.rows(decoded)
        val expectedHeader = listOf(
            "unit",
            "node_id",
            "node_type",
            "node_path",
            "depth",
            "edge",
            "trait",
            "value",
            "timestamp",
        )
        assertEquals(expectedHeader, rows.first())

        val expectedBodyPairs = listOf(
            decoded.root.id to "length",
            decoded.root.id to "color",
            s1 to "length",
            s1 to "color",
            s2 to "length",
            s2 to "color",
            s3 to "length",
            s3 to "color",
            b1 to "length",
            b1 to "color",
            b1 to "flowering date",
            b1 to "branch photo",
        )

        assertEquals(expectedBodyPairs, rows.drop(1).map { it[1] to it[6] })

        val photoRow = rows.first { it[6] == "branch photo" }
        assertTrue(photoRow[7].contains("sample1_node_") || photoRow[7].endsWith(".jpg"))
        assertTrue(!photoRow[7].startsWith("content:"))
    }

    /**
     * Plant topology for export payload proof:
     * root > s1 > s2 > s3 > s4 > s5 with b1,b2 on s1 and b3,b4 on s3;
     * stem lengths [12,10,na,8,5]; branch lengths [4,5,4,na].
     * Sidecar JSON keeps collect insertion order and values on the correct nodes.
     */
    @Test
    fun plantTopology_sidecarJsonOrderAndValues() {
        val schema = userSchema()
        val t0 = "2026-07-30T14:00:00Z"
        val stemRule = schema.typeOf("root")!!.allowedChildren.first { it.nodeType == "stem" }
        val nextStem = schema.typeOf("stem")!!.allowedChildren.first {
            it.nodeType == "stem" && it.edge == EdgeType.PRECEDES
        }
        val branchRule = schema.typeOf("stem")!!.allowedChildren.first {
            it.nodeType == "branch" && it.edge == EdgeType.BEARS
        }

        var root = TreeMutations.newRoot(schema, t0)
        val (r1, s1) = TreeMutations.addChild(root, root.id, stemRule, schema, t0)
        root = r1
        val (r2, s2) = TreeMutations.addChild(root, s1, nextStem, schema, t0)
        root = r2
        val (r3, s3) = TreeMutations.addChild(root, s2, nextStem, schema, t0)
        root = r3
        val (r4, s4) = TreeMutations.addChild(root, s3, nextStem, schema, t0)
        root = r4
        val (r5, s5) = TreeMutations.addChild(root, s4, nextStem, schema, t0)
        root = r5

        val (r6, b1) = TreeMutations.addChild(root, s1, branchRule, schema, t0)
        root = r6
        val (r7, b2) = TreeMutations.addChild(root, s1, branchRule, schema, t0)
        root = r7
        val (r8, b3) = TreeMutations.addChild(root, s3, branchRule, schema, t0)
        root = r8
        val (r9, b4) = TreeMutations.addChild(root, s3, branchRule, schema, t0)
        root = r9

        listOf(s1 to "12", s2 to "10", s3 to "NA", s4 to "8", s5 to "5").forEach { (id, len) ->
            root = TreeMutations.setTrait(root, id, "length", len, t0)
        }
        listOf(b1 to "4", b2 to "5", b3 to "4", b4 to "NA").forEach { (id, len) ->
            root = TreeMutations.setTrait(root, id, "length", len, t0)
        }

        // Stem chain after collect: each stem's PRECEDES child precedes later BEARS siblings.
        assertEquals(listOf(s2, b1, b2), find(root, s1)!!.children.map { it.id })
        assertEquals(listOf(s3), find(root, s2)!!.children.map { it.id })
        assertEquals(listOf(s4, b3, b4), find(root, s3)!!.children.map { it.id })
        assertEquals(listOf(s5), find(root, s4)!!.children.map { it.id })
        assertTrue(find(root, s5)!!.children.isEmpty())

        val pending = TreePending(
            unitId = "sample1",
            studyId = "field1",
            traitId = "t1",
            traitName = "soy tree-carrier",
            rep = "1",
            root = root,
            capturedAt = t0,
            sourceApp = "Field Book Test",
        )
        val json = TreeCodec.encodeSidecar(schema.id, pending)
        val decoded = TreeCodec.decodeObservation(json)

        assertEquals(
            listOf("12", "10", "NA", "8", "5"),
            listOf(s1, s2, s3, s4, s5).map { find(decoded.root, it)!!.traits["length"] },
        )
        assertEquals(
            listOf("4", "5", "4", "NA"),
            listOf(b1, b2, b3, b4).map { find(decoded.root, it)!!.traits["length"] },
        )
        assertEquals(listOf(s2, b1, b2), find(decoded.root, s1)!!.children.map { it.id })
        assertEquals(listOf(s4, b3, b4), find(decoded.root, s3)!!.children.map { it.id })
        assertEquals(
            listOf("stem", "branch", "branch"),
            find(decoded.root, s1)!!.children.map { it.nodeType },
        )
        assertEquals(
            listOf(EdgeType.PRECEDES, EdgeType.BEARS, EdgeType.BEARS),
            find(decoded.root, s1)!!.children.map { it.edge },
        )
        // MTG groups BEARS before PRECEDES even when JSON children keep collect order.
        assertTrue(decoded.mtg.contains("[+"))
        assertTrue(Regex("""S\d+\[\+""").containsMatchIn(decoded.mtg))
    }

    /**
     * Collect UI press path for sample1/field1 (labels match [TreeCollectScreen] button text:
     * `"${rule.edge.symbol} ${rule.label}"`).
     *
     * 1. On root: tap `< Add Stem` → auto-navigate to stem 1
     * 2. On stem 1: tap `< Add Stem` → stem 2
     * 3. On stem 2: tap `< Add Stem` → stem 3
     * 4. On stem 3: tap `↑ Up` (or breadcrumb S2) → back to stem 2
     * 5. On stem 2: tap `+ Add Branch` → branch
     * 6. On branch: date chrome (`trait_date_node_host` FABs) → save today
     *
     * Chrome hosts (not reinvented text buttons): numeric keypad, date FABs,
     * camera FAB → CameraActivity. No "Capture photo" text button.
     */
    @Test
    fun collectPressPath_threeStemsThenBranchOnStem2_buttonLabels() {
        val schema = userSchema()
        val rootRule = schema.typeOf("root")!!.allowedChildren.single()
        val stemRules = schema.typeOf("stem")!!.allowedChildren
        val nextStem = stemRules.single { it.nodeType == "stem" }
        val addBranch = stemRules.single { it.nodeType == "branch" }

        // TreeCollectScreen FlowRow buttons:
        assertEquals("< Add Stem", "${rootRule.edge.symbol} ${rootRule.label}")
        assertEquals("< Add Stem", "${nextStem.edge.symbol} ${nextStem.label}")
        assertEquals("+ Add Branch", "${addBranch.edge.symbol} ${addBranch.label}")
        assertEquals(EdgeType.PRECEDES, rootRule.edge)
        assertEquals(EdgeType.BEARS, addBranch.edge)

        // Existing children rows use type displayName — never ChildRule.label ("Add Branch").
        val branchDisplay = schema.typeOf("branch")!!.displayName
        assertEquals("Branch", branchDisplay)
        val stem2ChildrenLabels = listOf(
            // After press-path below: S3 then B1 under stem 2
            "S3 ${schema.typeOf("stem")!!.displayName}",
            "B1 $branchDisplay",
        )
        // Contract mirrors TreeCollectScreen: "${cls}${idx} $typeLabel"
        assertFalse(
            "existing child row must not use Add-prefixed ChildRule.label",
            stem2ChildrenLabels.any { it.contains("Add ") },
        )

        // Ascend label matches TreeCollectStrings.ascend default ("↑ Up")
        assertEquals("↑ Up", "↑ ${TreeCollectStringsStub.ascend}")

        val t0 = "2026-07-30T12:00:00Z"
        var root = TreeMutations.newRoot(schema, t0)
        val (r1, s1) = TreeMutations.addChild(root, root.id, rootRule, schema, t0)
        root = r1
        val (r2, s2) = TreeMutations.addChild(root, s1, nextStem, schema, t0)
        root = r2
        val (r3, s3) = TreeMutations.addChild(root, s2, nextStem, schema, t0)
        root = r3

        // After stem 3, current node is s3; ascend lands on stem 2
        assertEquals(s2, parentOf(root, s3)!!.id)
        assertEquals(listOf(s3), find(root, s2)!!.children.map { it.id })
        // Breadcrumb path to S2: R1 › S1 › S2
        assertEquals(listOf("root", "stem", "stem"), pathTo(root, s2).map { it.nodeType })

        val (r4, b1) = TreeMutations.addChild(root, s2, addBranch, schema, t0)
        root = r4
        root = TreeMutations.setTrait(root, b1, "flowering date", "2026-07-30", t0)
        root = TreeMutations.setTrait(root, b1, "length", "5", t0)
        root = TreeMutations.setTrait(root, b1, "color", "yellow", t0)

        val stem2 = find(root, s2)!!
        assertEquals(listOf(s3, b1), stem2.children.map { it.id })
        // Existing-children UI labels (displayName, not "Add …"):
        assertEquals(
            listOf(
                "${stem2.children[0].cls}${stem2.children[0].idx} ${schema.typeOf("stem")!!.displayName}",
                "${stem2.children[1].cls}${stem2.children[1].idx} ${schema.typeOf("branch")!!.displayName}",
            ),
            stem2.children.map { child ->
                val typeLabel = schema.typeOf(child.nodeType)?.displayName ?: child.nodeType
                "${child.cls}${child.idx} $typeLabel"
            },
        )
        assertEquals("S3 Stem", "${stem2.children[0].cls}${stem2.children[0].idx} ${schema.typeOf("stem")!!.displayName}")
        assertEquals("B1 Branch", "${stem2.children[1].cls}${stem2.children[1].idx} $branchDisplay")
        assertEquals("2026-07-30", find(root, b1)!!.traits["flowering date"])
        assertEquals(3, pathTo(root, s3).count { it.nodeType == "stem" })

        // English length/color TraitObjects validate; Russian export names stay unresolved.
        assertNull(englishStudyResolver.resolve("длина междоузлия"))
        assertNull(englishStudyResolver.resolve("длина боба"))
        val issues = TreeValidator.validate(root, schema, englishStudyResolver)
        assertTrue(issues.none { it is Issue.OutOfRange })
        assertTrue(issues.none { it is Issue.NotNumeric })
        assertTrue(issues.none { it is Issue.InvalidValue })
        assertTrue(issues.none { it is Issue.MissingRequired })

        // Out-of-range uses English "length" metadata (0..100), not Russian min/max.
        root = TreeMutations.setTrait(root, b1, "length", "101", t0)
        assertTrue(TreeValidator.validate(root, schema, englishStudyResolver).any { it is Issue.OutOfRange })
    }

    /** Mirrors [com.fieldbook.tracker.traits.composables.collect.TreeCollectStrings] defaults. */
    private object TreeCollectStringsStub {
        const val ascend = "Up"
    }

    private fun userSchema(): TreeSchema {
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
