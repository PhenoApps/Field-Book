package com.fieldbook.shared.brapi

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSError
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.darwin.NSObject
import kotlin.coroutines.resume

private var activeSession: ASWebAuthenticationSession? = null
private val presentationContextProvider = BrapiOAuthPresentationContextProvider()

actual suspend fun openBrapiAuthorizationUrl(authUrl: String, redirectUri: String): String? {
    return suspendCancellableCoroutine { continuation ->
        val url = NSURL.URLWithString(authUrl)
        val callbackScheme = NSURL.URLWithString(redirectUri)?.scheme

        if (url == null || callbackScheme == null) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        val session = ASWebAuthenticationSession(
            url,
            callbackScheme
        ) { callbackUrl: NSURL?, _: NSError? ->
            activeSession = null
            if (continuation.isActive) {
                continuation.resume(callbackUrl?.absoluteString)
            }
        }

        session.presentationContextProvider = presentationContextProvider
        activeSession = session

        continuation.invokeOnCancellation {
            activeSession?.cancel()
            activeSession = null
        }

        if (!session.start()) {
            activeSession = null
            continuation.resume(null)
        }
    }
}

private class BrapiOAuthPresentationContextProvider :
    NSObject(),
    ASWebAuthenticationPresentationContextProvidingProtocol {

    override fun presentationAnchorForWebAuthenticationSession(
        session: ASWebAuthenticationSession
    ): ASPresentationAnchor {
        return UIApplication.sharedApplication.keyWindow ?: ASPresentationAnchor()
    }
}
