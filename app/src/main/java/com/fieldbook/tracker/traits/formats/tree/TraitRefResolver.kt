package com.fieldbook.tracker.traits.formats.tree

import android.util.Log
import com.fieldbook.tracker.objects.TraitObject

fun interface TraitRefResolver {
    fun resolve(name: String): TraitObject?
}

class DatabaseTraitRefResolver(
    private val getByName: (String) -> TraitObject?,
    private val getByAlias: (String) -> TraitObject?,
) : TraitRefResolver {
    override fun resolve(name: String): TraitObject? =
        getByName(name) ?: getByAlias(name)
}

object TraitRefResolverUtil {
    private const val TAG = "TraitRefResolver"

    fun resolve(ref: TraitRef, resolver: TraitRefResolver): TraitObject? {
        val resolved = resolver.resolve(ref.traitName)
        if (resolved == null) {
            Log.w(TAG, "Unresolved TraitRef: '${ref.traitName}'")
        }
        return resolved
    }

    /** Distinct TraitRef names across the schema that do not resolve (R-20). */
    fun unresolvedTraitNames(schema: TreeSchema, resolver: TraitRefResolver): List<String> =
        schema.nodeTypes
            .flatMap { it.traitRefs }
            .map { it.traitName }
            .distinct()
            .filter { resolver.resolve(it) == null }
}
