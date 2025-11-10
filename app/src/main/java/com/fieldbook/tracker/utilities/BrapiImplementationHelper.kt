package com.fieldbook.tracker.utilities

import android.content.Context
import com.fieldbook.tracker.R
import com.fieldbook.tracker.brapi.model.BrapiModule
import com.fieldbook.tracker.brapi.model.BrapiServerCall
import org.brapi.v2.model.core.BrAPIService

enum class CallImplementedBy {
    SERVER,
    FIELD_BOOK,
    SERVER_AND_FIELD_BOOK
}

data class ServiceComparison(
    val service: String,
    val methods: List<String>,
    val isFbImplemented: Boolean,
    val implementedMethods: List<String> = emptyList(),
    val source: CallImplementedBy
)

data class BrapiModuleCalls(
    val moduleName: String,
    val calls: List<ServiceComparison>,
    val fbImplementedCount: Int,
    val totalCalls: Int
) {
    val implementationPercentage: Int
        get() = if (totalCalls > 0) (fbImplementedCount * 100 / totalCalls) else 0
}

object BrapiImplementationHelper {

    private val fieldBookCalls = listOf(
        // core calls
        BrapiServerCall("programs", listOf("GET")),
        BrapiServerCall("studies", listOf("GET")),
        BrapiServerCall("studies/{studyDbId}", listOf("GET")),
        BrapiServerCall("serverinfo", listOf("GET")),
        BrapiServerCall("trials", listOf("GET")),

        // phenotyping calls
        BrapiServerCall("variables", listOf("GET")),
        BrapiServerCall("observations", listOf("GET", "POST", "PUT")),
        BrapiServerCall("observationlevels", listOf("GET")),
        BrapiServerCall("observationunits", listOf("GET")),
        BrapiServerCall("images", listOf("POST")),
        BrapiServerCall("images/{imageDbId}", listOf("PUT")),
        BrapiServerCall("images/{imageDbId}/imagecontent", listOf("PUT")),
    )

    private val fieldBookCallsMap = fieldBookCalls.associateBy { it.service }

    fun compareImplementation(serverCalls: List<BrAPIService>): Map<String, BrapiModuleCalls> {
        // convert BrAPIService to BrapiServerCall
        val serverBrapiCalls = serverCalls.map { call ->
            BrapiServerCall(
                service = call.service,
                methods = call.methods?.map { it.brapiValue } ?: emptyList()
            )
        }

        val serverModuleMap = BrapiModuleHelper.groupServerCallsByModule(serverBrapiCalls)

        // track which Field Book calls we've already processed
        val visitedFBCalls = mutableSetOf<String>()

        val moduleComparisonMap = processServerCalls(serverModuleMap, visitedFBCalls)

        addRemainingFieldBookCalls(moduleComparisonMap, visitedFBCalls)

        return moduleComparisonMap.mapValues { (_, module) ->
            module.copy(calls = module.calls.sortedBy { it.service })
        }
    }

    /**
     * The top card in the screen would show implemented % for serverSupportedCount / totalFBCalls
     * Returns the comparison as a (BrAPI) module for simplicity
     * Shows what % of FB calls does server support
     */
    fun getFieldBookCompatibility(serverCalls: List<BrAPIService>, context: Context): BrapiModuleCalls {
        // convert server calls to map
        val serverCallsMap = serverCalls.associate { call ->
            call.service to (call.methods?.map { it.brapiValue } ?: emptyList())
        }

        val allCalls = fieldBookCalls.map { fbCall ->
            val serverMethods = serverCallsMap[fbCall.service] ?: emptyList()
            val supportedMethods = fbCall.methods.filter { it in serverMethods }
            val isSupported = supportedMethods.isNotEmpty()

            ServiceComparison(
                service = fbCall.service,
                methods = fbCall.methods,
                isFbImplemented = fieldBookCalls.last() != fbCall,
                implementedMethods = supportedMethods,
                source = if (isSupported) CallImplementedBy.SERVER_AND_FIELD_BOOK else CallImplementedBy.FIELD_BOOK
            )
        }

        val serverSupportedCount = allCalls.count { it.implementedMethods.isNotEmpty() }

        return BrapiModuleCalls(
            moduleName = context.getString(R.string.brapi_compatibility_fieldbook),
            calls = allCalls.sortedBy { it.service },
            fbImplementedCount = serverSupportedCount,
            totalCalls = allCalls.size
        )
    }

    /**
     * Process all server calls and compare with Field Book implementation
     * This shows implemented % for fbImplementedCount / totalServerCalls
     * Shows what % of server calls does FB implement
     */
    private fun processServerCalls(
        serverModuleMap: Map<String, BrapiModule>,
        visited: MutableSet<String>
    ): MutableMap<String, BrapiModuleCalls> {
        val moduleComparisonMap = mutableMapOf<String, BrapiModuleCalls>()

        for ((serverModuleName, serverModule) in serverModuleMap) {
            val comparisonCalls = mutableListOf<ServiceComparison>()
            var serverCallsImplementedByFB = 0

            for (serverCall in serverModule.calls) {
                val fbImplementedMethods = getFieldBookImplementedMethods(serverCall.service, serverCall.methods)
                val isFbImplemented = fbImplementedMethods.isNotEmpty()

                if (isFbImplemented) {
                    serverCallsImplementedByFB++
                    // field book implements it, mark the call as visited
                    visited.add(serverCall.service)
                }

                comparisonCalls.add( // add comparison
                    ServiceComparison(
                        service = serverCall.service,
                        methods = serverCall.methods,
                        isFbImplemented = isFbImplemented,
                        implementedMethods = fbImplementedMethods,
                        source = if (isFbImplemented) CallImplementedBy.SERVER_AND_FIELD_BOOK else CallImplementedBy.SERVER
                    )
                )
            }

            moduleComparisonMap[serverModuleName] = BrapiModuleCalls(
                moduleName = serverModuleName,
                calls = comparisonCalls,
                fbImplementedCount = serverCallsImplementedByFB,
                totalCalls = serverModule.calls.size
            )
        }

        return moduleComparisonMap
    }

    /**
     * Add Field Book calls that weren't found on the server
     */
    private fun addRemainingFieldBookCalls(
        moduleComparisonMap: MutableMap<String, BrapiModuleCalls>,
        visited: Set<String>
    ) {
        for (fbCall in fieldBookCalls) {
            // skip if already visited
            if (visited.contains(fbCall.service)) continue

            val moduleName = BrapiModuleHelper.getModule(fbCall.service)

            val newCall = ServiceComparison(
                service = fbCall.service,
                methods = fbCall.methods,
                isFbImplemented = true,
                implementedMethods = fbCall.methods,
                source = CallImplementedBy.FIELD_BOOK
            )

            val existingModule = moduleComparisonMap[moduleName]

            if (existingModule != null) { // module exists in the map
                moduleComparisonMap[moduleName] = existingModule.copy(
                    calls = existingModule.calls + newCall,
                    fbImplementedCount = existingModule.fbImplementedCount + 1,
                    totalCalls = existingModule.totalCalls + 1
                )
            } else { // module does not exist, create new module
                moduleComparisonMap[moduleName] = BrapiModuleCalls(
                    moduleName = moduleName,
                    calls = listOf(newCall),
                    fbImplementedCount = 1,
                    totalCalls = 1
                )
            }
        }
    }

    /**
     * Return HTTP methods that are implemented by Field Book for service
     * If the service is not implemented by the app, emptyList will be returned
     */
    private fun getFieldBookImplementedMethods(
        service: String,
        serverMethods: List<String>
    ): List<String> {
        val fbCall = fieldBookCallsMap[service] ?: return emptyList()
        return fbCall.methods.filter { it in serverMethods }
    }
}