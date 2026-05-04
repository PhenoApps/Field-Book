package com.fieldbook.shared.brapi

object BrAPIServiceFactory {
    const val VERSION_V1 = "V1"
    const val VERSION_V2 = "V2"

    fun create(
        baseUrl: String,
        bearerToken: String? = null,
        version: String? = VERSION_V2,
    ): BrAPIService {
        return when (version?.trim()?.uppercase()) {
            VERSION_V1 -> BrAPIServiceV1(
                baseUrl = baseUrl,
                bearerToken = bearerToken,
            )
            else -> BrAPIServiceV2(
                baseUrl = baseUrl,
                bearerToken = bearerToken,
            )
        }
    }
}
