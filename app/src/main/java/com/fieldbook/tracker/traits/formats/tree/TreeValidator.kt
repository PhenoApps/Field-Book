package com.fieldbook.tracker.traits.formats.tree

import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.formats.Formats
import kotlin.math.max

object TreeValidator {

    fun validate(
        root: TreeNode,
        schema: TreeSchema,
        resolver: TraitRefResolver,
    ): List<Issue> {
        val issues = mutableListOf<Issue>()
        validateNode(root, null, schema, resolver, issues)
        return issues
    }

    private fun validateNode(
        node: TreeNode,
        parent: TreeNode?,
        schema: TreeSchema,
        resolver: TraitRefResolver,
        issues: MutableList<Issue>,
    ) {
        val typeDef = schema.typeOf(node.nodeType)
        if (typeDef == null) {
            issues += Issue.UnknownType(node.id, node.nodeType)
            // Still walk children so deeper structure is reported.
            node.children.forEach { child ->
                validateNode(child, node, schema, resolver, issues)
            }
            return
        }

        typeDef.traitRefs.sortedBy { it.order }.forEach { ref ->
            val trait = TraitRefResolverUtil.resolve(ref, resolver)
            val value = node.traits[ref.traitName].orEmpty()
            val required = ref.requiredOverride == true
            if (required && value.isBlank()) {
                issues += Issue.MissingRequired(node.id, ref.traitName)
            }
            if (value.isNotBlank() && trait != null) {
                validateTraitValue(node.id, ref.traitName, value, trait, issues)
            }
        }

        val maxChildren = typeDef.maxChildren
        if (maxChildren != null && node.children.size > maxChildren) {
            issues += Issue.TooManyChildren(node.id, maxChildren)
        }

        val idxByClass = node.children.groupBy { it.cls }
        idxByClass.forEach { (_, siblings) ->
            val dupes = siblings.groupBy { it.idx }.filter { it.value.size > 1 }
            dupes.forEach { (idx, _) ->
                issues += Issue.DuplicateSiblingIndex(node.id, idx)
            }
        }

        node.children.forEach { child ->
            val typeMatch = typeDef.allowedChildren.any { it.nodeType == child.nodeType }
            val edgeMatch = typeDef.allowedChildren.any { it.nodeType == child.nodeType && it.edge == child.edge }
            if (!typeMatch) {
                issues += Issue.IllegalChild(node.id, child.nodeType)
            } else if (!edgeMatch) {
                issues += Issue.IllegalEdge(node.id, child.nodeType)
            }
            validateNode(child, node, schema, resolver, issues)
        }
    }

    private fun validateTraitValue(
        nodeId: String,
        traitName: String,
        value: String,
        trait: TraitObject,
        issues: MutableList<Issue>,
    ) {
        val format = trait.format.orEmpty().lowercase()
        when (format) {
            Formats.NUMERIC.getDatabaseName(),
            Formats.PERCENT.getDatabaseName(),
            Formats.COUNTER.getDatabaseName(),
            Formats.ANGLE.getDatabaseName() -> {
                if (value.equals("NA", ignoreCase = true)) {
                    // Field Book NA is a stored value, not invalid numeric input.
                } else if (!TreeTraitValueSupport.isValidNumeric(value, trait)) {
                    issues += Issue.NotNumeric(nodeId, traitName)
                } else {
                    val num = value.toDouble()
                    trait.minimum.takeIf { it.isNotBlank() }?.toDoubleOrNull()?.let { min ->
                        if (num < min) issues += Issue.OutOfRange(nodeId, traitName, value)
                    }
                    trait.maximum.takeIf { it.isNotBlank() }?.toDoubleOrNull()?.let { max ->
                        if (num > max) issues += Issue.OutOfRange(nodeId, traitName, value)
                    }
                }
            }
            Formats.DATE.getDatabaseName() -> {
                if (!value.equals("NA", ignoreCase = true) &&
                    !TreeTraitValueSupport.isValidDate(value, trait)
                ) {
                    issues += Issue.InvalidValue(nodeId, traitName, value)
                }
            }
            Formats.CATEGORICAL.getDatabaseName(),
            "multicat",
            "qualitative" -> {
                if (!value.equals("NA", ignoreCase = true) &&
                    trait.categories.isNotBlank() &&
                    !TreeTraitValueSupport.isValidCategory(value, trait)
                ) {
                    issues += Issue.BadCategory(nodeId, traitName, value)
                }
            }
            Formats.BOOLEAN.getDatabaseName() -> {
                if (!value.equals("NA", ignoreCase = true) &&
                    !TreeTraitValueSupport.isValidBoolean(value)
                ) {
                    issues += Issue.InvalidValue(nodeId, traitName, value)
                }
            }
            Formats.STOP_WATCH.getDatabaseName(),
            "stopwatch",
            "seconds" -> {
                if (!value.equals("NA", ignoreCase = true) &&
                    !TreeTraitValueSupport.isValidStopWatch(value)
                ) {
                    issues += Issue.InvalidValue(nodeId, traitName, value)
                }
            }
        }
    }
}

object TreeSchemaValidator {

    fun validate(schema: TreeSchema): List<String> {
        val issues = mutableListOf<String>()
        val types = schema.nodeTypes.associateBy { it.name }
        if (schema.rootType !in types) {
            issues += "Missing root type ${schema.rootType}"
        }
        schema.nodeTypes.forEach { type ->
            type.allowedChildren.forEach { rule ->
                if (rule.nodeType !in types) {
                    issues += "Unknown child type ${rule.nodeType} on ${type.name}"
                }
            }
        }
        val reachable = mutableSetOf<String>()
        fun walk(typeName: String) {
            if (!reachable.add(typeName)) return
            types[typeName]?.allowedChildren?.forEach { walk(it.nodeType) }
        }
        walk(schema.rootType)
        schema.nodeTypes.forEach { if (it.name !in reachable) issues += "Unreachable type ${it.name}" }
        return issues
    }
}
