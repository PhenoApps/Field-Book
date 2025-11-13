package com.fieldbook.tracker.utilities

import android.content.Context
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.brapi.io.fieldBookImplementedCalls
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

/**
 * Once we get the results from the serverinfo endpoint, we see if FB implements at least one
 * of the methods for the corresponding service. If yes, we treat it as implemented
 *
 * Steps:
 *
 *      - compareImplementation: The received calls from the server are grouped by module, and this is stored in a map.
 *
 *      - processServerCalls: Iterate over the map. For each service, compare the service methods and see if
 *        FB implements at least one of the methods and store the result.
 *        We maintain a visited set to mark whether an FB call was visited ^
 *        At the end of each module, we also count the number of services FB implements
 *        to use the stat later in form of % completed.
 *        In this step, the source may be SERVER or SERVER_AND_FIELD_BOOK
 *
 *      - addRemainingFieldBookCalls: ^ = Once all brapi calls are done processing from the map,
 *        we go over the remaining FB calls which were not visited and add them according to the module mapping.
 *        Since the server has not implemented it, the source will be FIELD_BOOK.
 *
 *      - getFieldBookCompatibility: This is only for the top card, which just compares the
 *        FB implemented services and checks whether server implements it
 *
 * If we assume SERVER vs FieldBook as tables:
 *      - The first card would do right join
 *      - The rest of the cards would do left join
 */
object BrapiImplementationHelper {

    private val fieldBookCallsMap = fieldBookImplementedCalls.associateBy { it.service }

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

        val allCalls = fieldBookImplementedCalls.map { fbCall ->
            val serverMethods = serverCallsMap[fbCall.service] ?: emptyList()
            val supportedMethods = fbCall.methods.filter { it in serverMethods }
            val isSupported = supportedMethods.isNotEmpty()

            ServiceComparison(
                service = fbCall.service,
                methods = fbCall.methods,
                isFbImplemented = isSupported, // this denotes whether server supports the service
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
        for (fbCall in fieldBookImplementedCalls) {
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