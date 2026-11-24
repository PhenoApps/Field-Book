package com.fieldbook.tracker.utilities

import android.util.Log
import com.fieldbook.tracker.brapi.coreEndpoints
import com.fieldbook.tracker.brapi.genotypingEndpoints
import com.fieldbook.tracker.brapi.germplasmEndpoints
import com.fieldbook.tracker.brapi.phenotypingEndpoints

object BrapiModuleHelper {

    private const val TAG = "BrapiModuleHelper"

    private val excludeSpecialPrefixes = listOf("search", "delete")

    fun getModule(service: String?): String {
        if (service.isNullOrEmpty()) return BrApiModule.Other.name

        return try {
            val pathComponents = service.split("/")

            val resourceName = extractResourceName(pathComponents)

            categorizeByResourceName(resourceName)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting module: ${e.message}", e)
            BrApiModule.Other.name
        }
    }

    /**
     * Extracts the resource name from path components, handling special prefixes
     */
    private fun extractResourceName(pathComponents: List<String>): String {
        return if (
            pathComponents.firstOrNull() in excludeSpecialPrefixes
            && pathComponents.size > 1
        ) {
            // handle /search/resource or /delete/resource patterns
            // (eg. search/studies, search/studies/{searchResultsDbId} )
            pathComponents[1].lowercase()
        } else {
            // use the first component for standard endpoints
            pathComponents.firstOrNull()?.lowercase() ?: ""
        }
    }

    /**
     * Categorizes a resource name into its corresponding BrAPI module
     */
    private fun categorizeByResourceName(resourceName: String): String {
        return when (resourceName) {
            in coreEndpoints -> BrApiModule.Core.name
            in phenotypingEndpoints -> BrApiModule.Phenotyping.name
            in germplasmEndpoints -> BrApiModule.Germplasm.name
            in genotypingEndpoints -> BrApiModule.Genotyping.name
            else -> BrApiModule.Other.name
        }
    }

    /**
     * Represents all the BrAPI Modules
     */
    sealed class BrApiModule(val name: String) {
        object Core : BrApiModule("Core")
        object Phenotyping : BrApiModule("Phenotyping")
        object Germplasm : BrApiModule("Germplasm")
        object Genotyping : BrApiModule("Genotyping")
        object Other : BrApiModule("Other")
    }
}