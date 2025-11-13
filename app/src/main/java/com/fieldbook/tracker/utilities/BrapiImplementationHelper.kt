package com.fieldbook.tracker.utilities

import android.content.Context
import com.fieldbook.tracker.R
import com.fieldbook.tracker.brapi.model.BrapiModule
import com.fieldbook.tracker.brapi.model.BrapiServerCall
import org.brapi.v2.model.core.BrAPIService

enum class CallImplementedBy {
    SERVER,
    APP,
    SERVER_AND_APP
}

data class ServiceComparison(
    val service: String,
    val methods: List<String>,
    val isAppImplemented: Boolean,
    val implementedMethods: List<String> = emptyList(),
    val source: CallImplementedBy
)

data class BrapiModuleCalls(
    val moduleName: String,
    val calls: List<ServiceComparison>,
    val appImplementedCount: Int,
    val totalCalls: Int
) {
    val implementationPercentage: Int
        get() = if (totalCalls > 0) (appImplementedCount * 100 / totalCalls) else 0
}

/**
 * Once we get the results from the serverinfo endpoint, we see if the app implements at least one
 * of the methods for the corresponding service. If yes, we treat it as implemented
 *
 * ### Steps:
 *
 * [compareImplementation]: The received calls from the server are grouped by module, and this is stored in a map.
 *
 * [processServerCalls]: Iterate over the map.
 *  * For each service, compare the service methods and see if
 * the app implements at least one of the methods and store the result.
 * * We maintain a visited set to mark whether an app call was visited ^.
 * * At the end of each module, we also count the number of services the app implements
 * to use the stat later in form of % completed.
 * * In this step, the source may be SERVER or SERVER_AND_APP
 *
 * [addRemainingAppCalls]:
 * * ^ = Once all brapi calls are done processing from the map,
 * we go over the remaining app calls which were not visited and add them according to the module mapping.
 * * Since the server has not implemented it, the source will be APP.
 *
 * [getAppCompatibility]: This is only for the top card, which just compares the
 * app implemented services and checks whether server implements it
 *
 * * If we assume SERVER vs APP as database tables:
 *      - The first card would do right join
 *      - The rest of the cards would do left join
 */
class BrapiImplementationHelper(
    private val appImplementedCalls: List<BrapiServerCall>,
    private val appName: String,
) {

    private val appCallsMap = appImplementedCalls.associateBy { it.service }

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
        val visitedAppCalls = mutableSetOf<String>()

        val moduleComparisonMap = processServerCalls(serverModuleMap, visitedAppCalls)

        addRemainingAppCalls(moduleComparisonMap, visitedAppCalls)

        return moduleComparisonMap.mapValues { (_, module) ->
            module.copy(calls = module.calls.sortedBy { it.service })
        }
    }

    /**
     * The top card in the screen would show implemented % for serverSupportedCount / totalAppCalls
     * Returns the comparison as a (BrAPI) module for simplicity
     * Shows what % of app calls does server support
     */
    fun getAppCompatibility(serverCalls: List<BrAPIService>, context: Context): BrapiModuleCalls {
        // convert server calls to map
        val serverCallsMap = serverCalls.associate { call ->
            call.service to (call.methods?.map { it.brapiValue } ?: emptyList())
        }

        val allCalls = appImplementedCalls.map { appCall ->
            val serverMethods = serverCallsMap[appCall.service] ?: emptyList()
            val supportedMethods = appCall.methods.filter { it in serverMethods }
            val isSupported = supportedMethods.isNotEmpty()

            ServiceComparison(
                service = appCall.service,
                methods = appCall.methods,
                isAppImplemented = isSupported, // this denotes whether server supports the service
                implementedMethods = supportedMethods,
                source = if (isSupported) CallImplementedBy.SERVER_AND_APP else CallImplementedBy.APP
            )
        }

        val serverSupportedCount = allCalls.count { it.implementedMethods.isNotEmpty() }

        return BrapiModuleCalls(
            moduleName = context.getString(R.string.brapi_compatibility_with_app, appName),
            calls = allCalls.sortedBy { it.service },
            appImplementedCount = serverSupportedCount,
            totalCalls = allCalls.size
        )
    }

    /**
     * Process all server calls and compare with App implementation
     * This shows implemented % for appImplementedCount / totalServerCalls
     * Shows what % of server calls does the app implement
     */
    private fun processServerCalls(
        serverModuleMap: Map<String, BrapiModule>,
        visited: MutableSet<String>
    ): MutableMap<String, BrapiModuleCalls> {
        val moduleComparisonMap = mutableMapOf<String, BrapiModuleCalls>()

        for ((serverModuleName, serverModule) in serverModuleMap) {
            val comparisonCalls = mutableListOf<ServiceComparison>()
            var serverCallsImplementedByApp = 0

            for (serverCall in serverModule.calls) {
                val appImplementedMethods = getAppImplementedMethods(serverCall.service, serverCall.methods)
                val isAppImplemented = appImplementedMethods.isNotEmpty()

                if (isAppImplemented) {
                    serverCallsImplementedByApp++
                    // app implements it, mark the call as visited
                    visited.add(serverCall.service)
                }

                comparisonCalls.add( // add comparison
                    ServiceComparison(
                        service = serverCall.service,
                        methods = serverCall.methods,
                        isAppImplemented = isAppImplemented,
                        implementedMethods = appImplementedMethods,
                        source = if (isAppImplemented) CallImplementedBy.SERVER_AND_APP else CallImplementedBy.SERVER
                    )
                )
            }

            moduleComparisonMap[serverModuleName] = BrapiModuleCalls(
                moduleName = serverModuleName,
                calls = comparisonCalls,
                appImplementedCount = serverCallsImplementedByApp,
                totalCalls = serverModule.calls.size
            )
        }

        return moduleComparisonMap
    }

    /**
     * Add app implemented calls that weren't found on the server
     */
    private fun addRemainingAppCalls(
        moduleComparisonMap: MutableMap<String, BrapiModuleCalls>,
        visited: Set<String>
    ) {
        for (appCall in appImplementedCalls) {
            // skip if already visited
            if (visited.contains(appCall.service)) continue

            val moduleName = BrapiModuleHelper.getModule(appCall.service)

            val newCall = ServiceComparison(
                service = appCall.service,
                methods = appCall.methods,
                isAppImplemented = true,
                implementedMethods = appCall.methods,
                source = CallImplementedBy.APP
            )

            val existingModule = moduleComparisonMap[moduleName]

            if (existingModule != null) { // module exists in the map
                moduleComparisonMap[moduleName] = existingModule.copy(
                    calls = existingModule.calls + newCall,
                    appImplementedCount = existingModule.appImplementedCount + 1,
                    totalCalls = existingModule.totalCalls + 1
                )
            } else { // module does not exist, create new module
                moduleComparisonMap[moduleName] = BrapiModuleCalls(
                    moduleName = moduleName,
                    calls = listOf(newCall),
                    appImplementedCount = 1,
                    totalCalls = 1
                )
            }
        }
    }

    /**
     * Return HTTP methods that are implemented by the app for service
     * If the service is not implemented by the app, emptyList will be returned
     */
    private fun getAppImplementedMethods(
        service: String,
        serverMethods: List<String>
    ): List<String> {
        val appCall = appCallsMap[service] ?: return emptyList()
        return appCall.methods.filter { it in serverMethods }
    }
}