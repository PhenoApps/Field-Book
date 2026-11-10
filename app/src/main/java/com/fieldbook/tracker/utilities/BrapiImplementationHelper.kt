package com.fieldbook.tracker.utilities

import android.content.Context
import com.fieldbook.tracker.R
import com.fieldbook.tracker.brapi.model.BrapiCallPurpose
import com.fieldbook.tracker.brapi.model.FieldBookCall
import org.brapi.v2.model.core.BrAPIService

/**
 * One method of one BrAPI call, and which side supports it.
 */
data class CallComparison(
    val service: String,
    val method: String,
    val supportedByServer: Boolean,
    val supportedByApp: Boolean
)

/**
 * @param supportedCount for a server module, the calls Field Book makes. For a Field Book
 * purpose card ([isAppCompatibility]), the calls the server supports.
 */
data class BrapiModuleCalls(
    val moduleName: String,
    val calls: List<CallComparison>,
    val supportedCount: Int,
    val totalCalls: Int,
    val isAppCompatibility: Boolean = false
) {
    val implementationPercentage: Int
        get() = if (totalCalls > 0) (supportedCount * 100 / totalCalls) else 0
}

/**
 * Compares the calls a server lists in serverinfo with the calls Field Book makes.
 *
 * Every comparison is per method. A server listing GET observations but not POST or PUT supports
 * downloading observations and nothing else, so a call only counts as supported for the methods
 * the server actually lists.
 *
 * Service names are normalized before matching, servers list them with and without a leading
 * slash and in either case.
 */
class BrapiImplementationHelper(
    private val appCalls: List<FieldBookCall>,
    private val appName: String,
) {

    companion object {

        private val METHOD_ORDER = listOf("GET", "POST", "PUT", "PATCH", "DELETE")

        fun normalizeService(service: String): String = service.trim().trimStart('/').lowercase()

        private fun key(service: String, method: String) = normalizeService(service) to method.trim().uppercase()

        /**
         * Every (service, method) pair a server lists, normalized for matching.
         */
        fun serverCallKeys(serverCalls: List<BrAPIService>): Set<Pair<String, String>> =
            serverCalls.flatMap { call ->
                val service = call.service ?: return@flatMap emptyList()
                call.methods.orEmpty().map { key(service, it.brapiValue) }
            }.toSet()

        fun supports(serverKeys: Set<Pair<String, String>>, service: String, method: String) =
            key(service, method) in serverKeys
    }

    private val appCallKeys = appCalls.map { key(it.service, it.method) }.toSet()

    /**
     * All calls the server lists, grouped by module, each method marked with whether Field Book
     * makes it. Field Book calls the server does not list are added to their module, so every
     * module shows what Field Book would need from it.
     */
    fun compareImplementation(serverCalls: List<BrAPIService>): Map<String, BrapiModuleCalls> {

        val rows = linkedMapOf<Pair<String, String>, CallComparison>()

        for (call in serverCalls) {
            val service = call.service ?: continue
            for (method in call.methods.orEmpty()) {
                val callKey = key(service, method.brapiValue)
                rows.getOrPut(callKey) {
                    CallComparison(
                        service = service.trim().trimStart('/'),
                        method = callKey.second,
                        supportedByServer = true,
                        supportedByApp = callKey in appCallKeys
                    )
                }
            }
        }

        for (appCall in appCalls) {
            val callKey = key(appCall.service, appCall.method)
            if (callKey !in rows) {
                rows[callKey] = CallComparison(
                    service = appCall.service,
                    method = callKey.second,
                    supportedByServer = false,
                    supportedByApp = true
                )
            }
        }

        return rows.values
            .groupBy { BrapiModuleHelper.getModule(normalizeService(it.service)) }
            .mapValues { (moduleName, calls) ->
                BrapiModuleCalls(
                    moduleName = moduleName,
                    calls = calls.sortedForDisplay(),
                    supportedCount = calls.count { it.supportedByApp },
                    totalCalls = calls.size
                )
            }
    }

    /**
     * The calls Field Book makes, one card per [BrapiCallPurpose], each method marked with whether
     * the server lists it.
     */
    fun getAppCompatibility(serverCalls: List<BrAPIService>, context: Context): List<BrapiModuleCalls> {

        val serverKeys = serverCallKeys(serverCalls)

        return BrapiCallPurpose.entries.mapNotNull { purpose ->

            val calls = appCalls.filter { it.purpose == purpose }.map { appCall ->
                CallComparison(
                    service = appCall.service,
                    method = appCall.method.uppercase(),
                    supportedByServer = supports(serverKeys, appCall.service, appCall.method),
                    supportedByApp = true
                )
            }

            if (calls.isEmpty()) return@mapNotNull null

            BrapiModuleCalls(
                moduleName = when (purpose) {
                    BrapiCallPurpose.IMPORT -> context.getString(R.string.brapi_compatibility_import, appName)
                    BrapiCallPurpose.UPLOAD -> context.getString(R.string.brapi_compatibility_upload, appName)
                },
                calls = calls.sortedForDisplay(),
                supportedCount = calls.count { it.supportedByServer },
                totalCalls = calls.size,
                isAppCompatibility = true
            )
        }
    }

    private fun List<CallComparison>.sortedForDisplay() = sortedWith(
        compareBy<CallComparison>({ normalizeService(it.service) }, {
            METHOD_ORDER.indexOf(it.method).let { index -> if (index == -1) METHOD_ORDER.size else index }
        })
    )
}
