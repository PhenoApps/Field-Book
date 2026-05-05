package com.fieldbook.tracker.brapi.dialogs

internal fun BrapiAccountUiState.toProviderState(): org.phenoapps.brapi.ui.BrapiAccountUiState =
    org.phenoapps.brapi.ui.BrapiAccountUiState(
        url = url,
        displayName = displayName,
        oidcUrl = oidcUrl,
        oidcClientId = oidcClientId,
        oidcScope = oidcScope,
        oidcFlow = oidcFlow,
        brapiVersion = brapiVersion,
        oidcUrlExplicitlySet = oidcUrlExplicitlySet,
        currentStep = currentStep,
        isFetchingDisplayName = isFetchingDisplayName,
    )
