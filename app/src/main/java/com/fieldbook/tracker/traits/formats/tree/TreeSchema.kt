package com.fieldbook.tracker.traits.formats.tree

import kotlinx.serialization.Serializable

@Serializable
data class TreeSchema(
    val id: String,
    val name: String,
    val version: Int,
    val rootType: String,
    val nodeTypes: List<NodeTypeDef>,
    val summaryPodTraitName: String? = "Seed count",
    val summaryPodNodeType: String? = "pod",
) {
    fun typeOf(name: String): NodeTypeDef? = nodeTypes.firstOrNull { it.name == name }

    /** True when any [TraitRef] or [summaryPodTraitName] equals [traitName] (NOCASE). */
    fun referencesTraitName(traitName: String): Boolean {
        if (summaryPodTraitName.equals(traitName, ignoreCase = true)) return true
        // Null summaryPodTraitName falls back to "Seed count" at compute time.
        if (summaryPodTraitName == null && traitName.equals("Seed count", ignoreCase = true)) return true
        return nodeTypes.any { type ->
            type.traitRefs.any { it.traitName.equals(traitName, ignoreCase = true) }
        }
    }

    /**
     * Rewrites name-based trait references after a study trait rename (R-18).
     * Case-insensitive match (ObservationVariableDao resolves NOCASE).
     * No-op when names are equal or [oldName] is blank.
     */
    fun renameTraitRefs(oldName: String, newName: String): TreeSchema {
        if (oldName.isBlank() || oldName == newName) return this
        return copy(
            nodeTypes = nodeTypes.map { type ->
                type.copy(
                    traitRefs = type.traitRefs.map { ref ->
                        if (ref.traitName.equals(oldName, ignoreCase = true)) {
                            ref.copy(traitName = newName)
                        } else {
                            ref
                        }
                    },
                )
            },
            summaryPodTraitName = when {
                summaryPodTraitName.equals(oldName, ignoreCase = true) -> newName
                summaryPodTraitName == null && oldName.equals("Seed count", ignoreCase = true) -> newName
                else -> summaryPodTraitName
            },
        )
    }
}

@Serializable
data class NodeTypeDef(
    val name: String,
    val displayName: String,
    val cls: String,
    val allowedChildren: List<ChildRule> = emptyList(),
    val maxChildren: Int? = null,
    val traitRefs: List<TraitRef> = emptyList(),
)

@Serializable
data class ChildRule(
    val nodeType: String,
    val edge: EdgeType,
    val label: String,
)

@Serializable
data class TraitRef(
    val traitName: String,
    val requiredOverride: Boolean? = null,
    val order: Int = 0,
)
