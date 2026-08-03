package com.fieldbook.tracker.traits.formats.tree

import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.utilities.CategoryJsonUtil
import org.brapi.v2.model.pheno.BrAPIScaleValidValuesCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TreeValidatorTest {

    private val resolver = TraitRefResolver { name ->
        when (name) {
            "Node position" -> TraitObject().apply {
                this.name = name
                format = "numeric"
                minimum = "1"
                maximum = "40"
            }
            // Simulate alias resolution: schema refers to an alias string, resolver maps it to metadata.
            "position_alias" -> TraitObject().apply {
                this.name = name
                format = "numeric"
                minimum = "1"
                maximum = "40"
            }
            "Seed count" -> TraitObject().apply {
                this.name = name
                format = "numeric"
                minimum = "0"
                maximum = "6"
            }
            "Color" -> TraitObject().apply {
                this.name = name
                format = "categorical"
                categories = "green/purple/brown"
            }
            "Growth stage" -> TraitObject().apply {
                this.name = name
                format = "categorical"
                categories = CategoryJsonUtil.buildCategoryList(
                    listOf(
                        BrAPIScaleValidValuesCategories().apply {
                            label = "Vegetative"
                            value = "veg"
                        },
                        BrAPIScaleValidValuesCategories().apply {
                            label = "Flowering"
                            value = "flow"
                        },
                    ),
                )
            }
            "Flowering date" -> TraitObject().apply {
                this.name = name
                format = "date"
            }
            "Flowering doy" -> TraitObject().apply {
                this.name = name
                format = "date"
                useDayOfYear = true
            }
            "Decimal length" -> TraitObject().apply {
                this.name = name
                format = "numeric"
                maxDecimalPlaces = "1"
            }
            // From trait-tree/trait_export_2026-07-30-11-49-30.trt study traits
            "длина междоузлия" -> TraitObject().apply {
                this.name = name
                format = "numeric"
                minimum = "1"
                maximum = "10"
                mathSymbolsEnabled = false
            }
            "длина боба" -> TraitObject().apply {
                this.name = name
                format = "numeric"
                mathSymbolsEnabled = false
            }
            "flowering date" -> TraitObject().apply {
                this.name = name
                format = "date"
            }
            else -> null
        }
    }

    @Test
    fun missingRequired_reported() {
        val schema = TreeMutationsTest.soybeanSchema().let { s ->
            s.copy(nodeTypes = s.nodeTypes.map {
                if (it.name == "internode") it.copy(
                    traitRefs = listOf(TraitRef("Node position", requiredOverride = true, order = 0)),
                ) else it
            })
        }
        val root = TreeCodec.newRoot(schema, "2026-01-01T00:00:00Z")
        val rule = schema.typeOf("internode")!!.allowedChildren.first()
        val (withChild, _) = TreeMutations.addChild(root, root.id, rule, schema, "2026-01-01T00:00:00Z")
        val issues = TreeValidator.validate(withChild, schema, resolver)
        assertTrue(issues.any { it is Issue.MissingRequired })
    }

    @Test
    fun outOfRange_reported() {
        val schema = TreeMutationsTest.soybeanSchema().let { s ->
            s.copy(nodeTypes = s.nodeTypes.map {
                if (it.name == "internode") it.copy(
                    traitRefs = listOf(TraitRef("Node position", order = 0)),
                ) else it
            })
        }
        val ts = "2026-01-01T00:00:00Z"
        var root = TreeCodec.newRoot(schema, ts)
        val rule = schema.typeOf("internode")!!.allowedChildren.first()
        val (r1, n1) = TreeMutations.addChild(root, root.id, rule, schema, ts)
        root = TreeMutations.setTrait(r1, n1, "Node position", "99", ts)
        val issues = TreeValidator.validate(root, schema, resolver)
        assertTrue(issues.any { it is Issue.OutOfRange })
    }

    @Test
    fun traitRef_aliasResolved_validationUsesTraitMetadata() {
        val schema = TreeMutationsTest.soybeanSchema().let { s ->
            s.copy(nodeTypes = s.nodeTypes.map {
                if (it.name == "internode") it.copy(
                    traitRefs = listOf(TraitRef("position_alias", requiredOverride = true, order = 0)),
                ) else it
            })
        }
        val ts = "2026-01-01T00:00:00Z"
        var root = TreeCodec.newRoot(schema, ts)
        val rule = schema.typeOf("internode")!!.allowedChildren.first()
        val (r1, n1) = TreeMutations.addChild(root, root.id, rule, schema, ts)
        root = TreeMutations.setTrait(r1, n1, "position_alias", "99", ts)

        val issues = TreeValidator.validate(root, schema, resolver)
        assertTrue(issues.any { it is Issue.OutOfRange })
    }

    @Test
    fun badCategory_reported() {
        val schema = TreeMutationsTest.soybeanSchema().copy(
            nodeTypes = TreeMutationsTest.soybeanSchema().nodeTypes.map {
                if (it.name == "internode") it.copy(
                    traitRefs = listOf(TraitRef("Color", order = 0)),
                ) else it
            },
        )
        val ts = "2026-01-01T00:00:00Z"
        var root = TreeCodec.newRoot(schema, ts)
        val rule = schema.typeOf("internode")!!.allowedChildren.first()
        val (r1, n1) = TreeMutations.addChild(root, root.id, rule, schema, ts)
        root = TreeMutations.setTrait(r1, n1, "Color", "blue", ts)
        val issues = TreeValidator.validate(root, schema, resolver)
        assertTrue(issues.any { it is Issue.BadCategory })
    }

    @Test
    fun notNumeric_reported() {
        val schema = TreeMutationsTest.soybeanSchema().copy(
            nodeTypes = TreeMutationsTest.soybeanSchema().nodeTypes.map {
                if (it.name == "pod") it.copy(
                    traitRefs = listOf(TraitRef("Seed count", requiredOverride = true, order = 0)),
                ) else it
            },
        )
        val ts = "2026-01-01T00:00:00Z"
        var root = TreeCodec.newRoot(schema, ts)
        val internodeRule = schema.typeOf("internode")!!.allowedChildren.first()
        val podRule = ChildRule("pod", EdgeType.BEARS, "Pod")
        val (r1, n1) = TreeMutations.addChild(root, root.id, internodeRule, schema, ts)
        val (r2, podId) = TreeMutations.addChild(r1, n1, podRule, schema, ts)
        root = TreeMutations.setTrait(r2, podId, "Seed count", "many", ts)
        val issues = TreeValidator.validate(root, schema, resolver)
        assertTrue(issues.any { it is Issue.NotNumeric })
    }

    @Test
    fun categoricalJsonDefinition_acceptsDisplayLabel() {
        val schema = TreeMutationsTest.soybeanSchema().copy(
            nodeTypes = TreeMutationsTest.soybeanSchema().nodeTypes.map {
                if (it.name == "internode") it.copy(
                    traitRefs = listOf(TraitRef("Growth stage", order = 0)),
                ) else it
            },
        )
        val ts = "2026-01-01T00:00:00Z"
        var root = TreeCodec.newRoot(schema, ts)
        val rule = schema.typeOf("internode")!!.allowedChildren.first()
        val (r1, n1) = TreeMutations.addChild(root, root.id, rule, schema, ts)
        root = TreeMutations.setTrait(r1, n1, "Growth stage", "Flowering", ts)

        val issues = TreeValidator.validate(root, schema, resolver)
        assertTrue(issues.none { it is Issue.BadCategory })
    }

    @Test
    fun invalidDate_reported() {
        val schema = TreeMutationsTest.soybeanSchema().copy(
            nodeTypes = TreeMutationsTest.soybeanSchema().nodeTypes.map {
                if (it.name == "pod") it.copy(
                    traitRefs = listOf(TraitRef("Flowering date", requiredOverride = true, order = 0)),
                ) else it
            },
        )
        val ts = "2026-01-01T00:00:00Z"
        var root = TreeCodec.newRoot(schema, ts)
        val internodeRule = schema.typeOf("internode")!!.allowedChildren.first()
        val podRule = ChildRule("pod", EdgeType.BEARS, "Pod")
        val (r1, n1) = TreeMutations.addChild(root, root.id, internodeRule, schema, ts)
        val (r2, podId) = TreeMutations.addChild(r1, n1, podRule, schema, ts)
        root = TreeMutations.setTrait(r2, podId, "Flowering date", "tomorrow", ts)

        val issues = TreeValidator.validate(root, schema, resolver)
        assertTrue(issues.any { it is Issue.InvalidValue })
    }

    @Test
    fun dayOfYearDate_acceptsValidOrdinal() {
        val schema = TreeMutationsTest.soybeanSchema().copy(
            nodeTypes = TreeMutationsTest.soybeanSchema().nodeTypes.map {
                if (it.name == "pod") it.copy(
                    traitRefs = listOf(TraitRef("Flowering doy", requiredOverride = true, order = 0)),
                ) else it
            },
        )
        val ts = "2026-01-01T00:00:00Z"
        var root = TreeCodec.newRoot(schema, ts)
        val internodeRule = schema.typeOf("internode")!!.allowedChildren.first()
        val podRule = ChildRule("pod", EdgeType.BEARS, "Pod")
        val (r1, n1) = TreeMutations.addChild(root, root.id, internodeRule, schema, ts)
        val (r2, podId) = TreeMutations.addChild(r1, n1, podRule, schema, ts)
        root = TreeMutations.setTrait(r2, podId, "Flowering doy", "208", ts)

        val issues = TreeValidator.validate(root, schema, resolver)
        assertTrue(issues.none { it is Issue.InvalidValue })
    }

    @Test
    fun tooManyDecimalPlaces_reportedAsInvalidNumeric() {
        val schema = TreeMutationsTest.soybeanSchema().copy(
            nodeTypes = TreeMutationsTest.soybeanSchema().nodeTypes.map {
                if (it.name == "internode") it.copy(
                    traitRefs = listOf(TraitRef("Decimal length", order = 0)),
                ) else it
            },
        )
        val ts = "2026-01-01T00:00:00Z"
        var root = TreeCodec.newRoot(schema, ts)
        val rule = schema.typeOf("internode")!!.allowedChildren.first()
        val (r1, n1) = TreeMutations.addChild(root, root.id, rule, schema, ts)
        root = TreeMutations.setTrait(r1, n1, "Decimal length", "1.25", ts)

        val issues = TreeValidator.validate(root, schema, resolver)
        assertTrue(issues.any { it is Issue.NotNumeric })
    }

    @Test
    fun wrongEdge_reportedAsIllegalEdge() {
        val schema = TreeMutationsTest.soybeanSchema()
        val ts = "2026-01-01T00:00:00Z"
        val root = TreeCodec.newRoot(schema, ts)
        val child = TreeNode(
            id = "bad-edge",
            nodeType = "internode",
            cls = "N",
            idx = 1,
            edge = EdgeType.BEARS,
            createdAt = ts,
        )
        val invalid = root.copy(children = mutableListOf(child))

        val issues = TreeValidator.validate(invalid, schema, resolver)
        assertTrue(issues.any { it is Issue.IllegalEdge })
    }

    /**
     * Study traits from the 2026-07-30 export keep their Field Book metadata
     * (min/max/mathSymbols). Tree collect validates via [TreeValidator] →
     * [TreeTraitValueSupport], not by embedding NumericTraitLayout / DateTraitLayout.
     *
     * Russian names apply **only** when TraitRefs resolve those names. The soy
     * tree-carrier length/color path uses English study traits instead.
     */
    @Test
    fun exportRussianNumericTraits_minMaxAppliedThroughTreeValidator() {
        val schema = TreeMutationsTest.soybeanSchema().copy(
            nodeTypes = TreeMutationsTest.soybeanSchema().nodeTypes.map {
                if (it.name == "internode") {
                    it.copy(
                        traitRefs = listOf(
                            TraitRef("длина междоузлия", order = 0),
                            TraitRef("длина боба", order = 1),
                        ),
                    )
                } else {
                    it
                }
            },
        )
        val ts = "2026-07-30T12:00:00Z"
        var root = TreeCodec.newRoot(schema, ts)
        val rule = schema.typeOf("internode")!!.allowedChildren.first()
        val (r1, n1) = TreeMutations.addChild(root, root.id, rule, schema, ts)

        root = TreeMutations.setTrait(r1, n1, "длина междоузлия", "11", ts)
        root = TreeMutations.setTrait(root, n1, "длина боба", "3.5", ts)
        assertTrue(TreeValidator.validate(root, schema, resolver).any { it is Issue.OutOfRange })

        root = TreeMutations.setTrait(root, n1, "длина междоузлия", "5", ts)
        assertTrue(TreeValidator.validate(root, schema, resolver).none { it is Issue.OutOfRange })
        assertTrue(TreeValidator.validate(root, schema, resolver).none { it is Issue.NotNumeric })
    }

    @Test
    fun englishLengthColor_notRussianExportNames_whenTraitRefsAreEnglish() {
        val schema = TreeMutationsTest.soybeanSchema().copy(
            nodeTypes = TreeMutationsTest.soybeanSchema().nodeTypes.map {
                if (it.name == "internode") {
                    it.copy(
                        traitRefs = listOf(
                            TraitRef("length", order = 0),
                            TraitRef("color", order = 1),
                        ),
                    )
                } else {
                    it
                }
            },
        )
        val englishOnly = TraitRefResolver { name ->
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
                else -> null // Russian .trt names unresolved for this schema
            }
        }
        assertTrue(englishOnly.resolve("длина междоузлия") == null)

        val ts = "2026-07-30T12:00:00Z"
        var root = TreeCodec.newRoot(schema, ts)
        val rule = schema.typeOf("internode")!!.allowedChildren.first()
        val (r1, n1) = TreeMutations.addChild(root, root.id, rule, schema, ts)
        root = TreeMutations.setTrait(r1, n1, "length", "12", ts)
        root = TreeMutations.setTrait(root, n1, "color", "green", ts)

        assertTrue(TreeValidator.validate(root, schema, englishOnly).isEmpty())

        // 11 would fail Russian min/max (1..10) but English length allows 0..100
        root = TreeMutations.setTrait(root, n1, "length", "11", ts)
        assertTrue(TreeValidator.validate(root, schema, englishOnly).none { it is Issue.OutOfRange })

        root = TreeMutations.setTrait(root, n1, "length", "101", ts)
        assertTrue(TreeValidator.validate(root, schema, englishOnly).any { it is Issue.OutOfRange })
    }

    @Test
    fun floweringDate_usesTreeTraitValueSupportNotLayoutEmbed() {
        val schema = TreeMutationsTest.soybeanSchema().copy(
            nodeTypes = TreeMutationsTest.soybeanSchema().nodeTypes.map {
                if (it.name == "pod") {
                    it.copy(traitRefs = listOf(TraitRef("flowering date", order = 0)))
                } else {
                    it
                }
            },
        )
        val ts = "2026-07-30T12:00:00Z"
        var root = TreeCodec.newRoot(schema, ts)
        val internodeRule = schema.typeOf("internode")!!.allowedChildren.first()
        val podRule = ChildRule("pod", EdgeType.BEARS, "Pod")
        val (r1, n1) = TreeMutations.addChild(root, root.id, internodeRule, schema, ts)
        val (r2, podId) = TreeMutations.addChild(r1, n1, podRule, schema, ts)

        root = TreeMutations.setTrait(r2, podId, "flowering date", "not-a-date", ts)
        assertTrue(TreeValidator.validate(root, schema, resolver).any { it is Issue.InvalidValue })

        root = TreeMutations.setTrait(root, podId, "flowering date", "2026-07-30", ts)
        assertTrue(TreeValidator.validate(root, schema, resolver).none { it is Issue.InvalidValue })
        assertTrue(TreeTraitValueSupport.isValidDate("2026-07-30", resolver.resolve("flowering date")!!))
    }

    @Test
    fun stopWatch_invalidValue_flagsInvalidValue() {
        val schema = TreeMutationsTest.soybeanSchema().copy(
            nodeTypes = TreeMutationsTest.soybeanSchema().nodeTypes.map {
                if (it.name == "pod") {
                    it.copy(traitRefs = listOf(TraitRef("timer", order = 0)))
                } else {
                    it
                }
            },
        )
        val stopResolver = TraitRefResolver { name ->
            if (name == "timer") {
                TraitObject().apply {
                    this.name = name
                    format = "stop_watch"
                }
            } else {
                resolver.resolve(name)
            }
        }
        val ts = "2026-07-30T12:00:00Z"
        var root = TreeCodec.newRoot(schema, ts)
        val internodeRule = schema.typeOf("internode")!!.allowedChildren.first()
        val podRule = ChildRule("pod", EdgeType.BEARS, "Pod")
        val (r1, n1) = TreeMutations.addChild(root, root.id, internodeRule, schema, ts)
        val (r2, podId) = TreeMutations.addChild(r1, n1, podRule, schema, ts)

        root = TreeMutations.setTrait(r2, podId, "timer", "not-a-time", ts)
        assertTrue(TreeValidator.validate(root, schema, stopResolver).any { it is Issue.InvalidValue })

        root = TreeMutations.setTrait(root, podId, "timer", "0:01:23.456", ts)
        assertTrue(TreeValidator.validate(root, schema, stopResolver).none { it is Issue.InvalidValue })
    }

    @Test
    fun blockingIssues_onlyMissingRequired_matchesCollectScreen() {
        val schema = TreeMutationsTest.soybeanSchema().let { s ->
            s.copy(
                nodeTypes = s.nodeTypes.map {
                    if (it.name == "internode") {
                        it.copy(
                            traitRefs = listOf(
                                TraitRef("Node position", requiredOverride = true, order = 0),
                            ),
                        )
                    } else {
                        it
                    }
                },
            )
        }
        val ts = "2026-07-30T12:00:00Z"
        var root = TreeCodec.newRoot(schema, ts)
        val rule = schema.typeOf("internode")!!.allowedChildren.first()
        val (r1, n1) = TreeMutations.addChild(root, root.id, rule, schema, ts)
        root = TreeMutations.setTrait(r1, n1, "Node position", "99", ts)

        val issues = TreeValidator.validate(root, schema, resolver)
        assertTrue(issues.any { it is Issue.OutOfRange })
        assertTrue(issues.none { it is Issue.MissingRequired })
        // TreeCollectScreen / TreeTraitLayout.block only count MissingRequired
        assertEquals(0, issues.count { it is Issue.MissingRequired })

        val field = issues.forTraitField(n1, "Node position")
        assertEquals(1, field.size)
        assertTrue(field.single().isFieldWarning())
        assertFalse(field.single().isFieldBlocking())
        assertEquals("Node position", field.single().traitName)
        assertTrue(issues.forTraitField(n1, "other").isEmpty())
    }
}
